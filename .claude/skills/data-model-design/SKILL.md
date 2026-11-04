---
name: data-model-design
description: Thiết kế data model (PostgreSQL) cho một tính năng hoặc một service từ đặc tả SRS và tài liệu thiết kế, rồi viết thành tài liệu gồm ERD Mermaid, DDL (bảng, cột, ràng buộc, index, partition), lý do thiết kế, mẫu truy vấn, ước lượng khối lượng và thời hạn lưu trữ, truy vết về FR/BR của SRS. Ưu tiên domain tài chính (tiền, sổ cái, idempotency, state machine, dữ liệu cá nhân). Theo kiến trúc database-per-service, không khoá ngoại xuyên service. Dùng skill này khi người dùng muốn "thiết kế data model", "thiết kế database/schema/bảng", "ERD", "DDL", "cần những bảng nào cho tính năng X", "bổ sung bảng/cột", "thiết kế index", "mô hình dữ liệu cho service Y", kể cả khi chỉ đưa SRS hoặc mô tả tính năng. Không sinh Flyway migration hay code entity.
---

# Thiết kế data model

Data model là phần **khó sửa nhất** của hệ thống: code sai thì deploy lại, còn schema sai thì phải migration dữ liệu thật, đôi khi hàng trăm triệu dòng, trong lúc hệ thống vẫn chạy. Trong ví điện tử, schema còn là **tuyến phòng thủ cuối** cho tính đúng của tiền: một ràng buộc `UNIQUE` hay `CHECK` đúng chỗ chặn được lỗi mà code bỏ sót. Vì vậy skill này ưu tiên: ràng buộc ở DB cho mọi bất biến quan trọng, index suy ra từ truy vấn thật, và mọi quyết định đều có lý do ghi lại.

Viết bằng **tiếng Việt**; DDL, tên bảng, tên cột bằng tiếng Anh.

## Bước 1 — Đọc nguồn

1. **SRS tính năng** (`docs/srs/features/<ID>-*.md`): dữ liệu (mục 3.5), quy tắc nghiệp vụ (3.3), trạng thái (3.4), đầu ra phụ như audit và thông báo (3.6), câu hỏi mở. Chưa có SRS → dùng requirements, và nói rõ thiết kế dựa trên nguồn chưa chi tiết.
2. **Design doc**: service nào sở hữu dữ liệu nào (bảng service), luồng (để biết truy vấn nào xảy ra), quy ước event/outbox.
3. **Data model hiện có** (`docs/design/*-data-model.md`): bảng đã có, quy ước đặt tên, bảng dùng chung (`outbox`, `inbox`). Thiết kế phải **nối tiếp**, không định nghĩa lại bảng đã có.
4. Yêu cầu phi chức năng liên quan: quy mô (TPS, số người dùng), thời hạn lưu trữ, dữ liệu cá nhân, RPO.

## Bước 2 — Xác định thực thể và chủ sở hữu

Từ SRS, liệt kê mọi thứ cần lưu và phân loại, vì mỗi loại có quy tắc khác nhau (chi tiết ở `references/conventions.md` §5):

| Loại | Ví dụ | Đặc điểm |
|---|---|---|
| Aggregate / thực thể | lệnh thanh toán, ví, hồ sơ KYC | Có vòng đời, trạng thái, `version` |
| Bản ghi chỉ thêm (append-only) | bút toán sổ cái, audit log, log gọi ngân hàng | Không sửa, không xoá; thường partition theo thời gian |
| Cấu hình có hiệu lực theo thời gian | biểu phí, rule hạn mức | `effective_from/to`, `approval_id`, không sửa dòng đã có hiệu lực |
| Bản sao dữ liệu (projection) | danh bạ khách trong `wallet`, tier trong `limit` | Chỉ consumer ghi; `source_version`; không dùng để quyết định về tiền |
| Dữ liệu tham chiếu | danh sách ngân hàng | Ít thay đổi |

Với mỗi thực thể, xác định **service sở hữu** theo design doc. Một thực thể chỉ có một chủ. Service khác cần dữ liệu đó thì: gọi API của chủ sở hữu, hoặc giữ bản sao nhận qua event. **Không bao giờ có khoá ngoại hay truy vấn xuyên database của service khác.** Không xác định được chủ sở hữu → câu hỏi mở, không tự đặt.

## Bước 3 — Liệt kê truy vấn trước khi thiết kế index

Với mỗi bảng, ghi các truy vấn chính: ai đọc/ghi, điều kiện lọc, sắp xếp, phân trang, tần suất. Ví dụ: "Lịch sử giao dịch của một ví, mới nhất trước, keyset theo `(created_at, id)`, mỗi lần mở app".

Ước lượng số dòng/ngày và tốc độ tăng từ NFR (ghi rõ đây là giả định). Từ đó quyết định:
- index nào cần (index suy ra từ truy vấn, không thêm "cho chắc": mỗi index làm chậm thao tác ghi và tốn dung lượng);
- có cần partition không (bảng append-only lớn, có thời hạn lưu trữ);
- thời hạn lưu trữ và cách archive hoặc ẩn danh.

## Bước 4 — Viết DDL theo quy ước

Áp dụng `references/conventions.md` (đặt tên, kiểu dữ liệu, khoá, ràng buộc, index) và `references/fintech-data.md` (tiền, idempotency, state machine, sổ cái, dữ liệu cá nhân, cấu hình theo thời gian).

Nguyên tắc cốt lõi:
- **Bất biến quan trọng nằm trong DB**, không chỉ trong code: `CHECK (amount > 0)`, `UNIQUE (customer_id, idempotency_key)`, `CHECK (balance >= 0)`, partial unique cho quy tắc "chỉ một bản ghi đang hiệu lực".
- **Mọi cột có lý do tồn tại**: truy vết được về một FR/BR, một truy vấn, hoặc một yêu cầu vận hành (audit, đối soát). Cột "để sau này dùng" thì bỏ.
- **Mặc định `NOT NULL`**; cột nullable phải có nghĩa rõ ràng cho giá trị null (ví dụ `closed_at` null = chưa đóng).
- **Trạng thái** dùng `varchar` + `CHECK`, không dùng `ENUM` của PostgreSQL (thêm giá trị vào ENUM khó hơn khi migration).
- **Dữ liệu cá nhân** được mã hoá (`*_enc`), có cột hash để tra cứu (`*_hash`), và có kế hoạch ẩn danh khi hết hạn lưu trữ.

## Bước 5 — Kiểm tra DDL

1. Tự rà checklist ở cuối `references/template.md`.
2. **Nếu có Docker**, chạy DDL vào một PostgreSQL tạm để bắt lỗi cú pháp:
   ```
   docker run --rm -d --name dm-check -e POSTGRES_PASSWORD=x -p 55432:5432 postgres:16-alpine
   # đợi khởi động, rồi:
   docker exec -i dm-check psql -U postgres -v ON_ERROR_STOP=1 < <file-ddl.sql>
   docker stop dm-check
   ```
   Tách DDL từ tài liệu ra file tạm trong scratchpad. Báo đúng kết quả: chạy được hay lỗi gì. Không có Docker → nói rõ là **chưa kiểm cú pháp**.
   - Ngoài cú pháp, viết thêm một **bộ ca vi phạm**: mỗi ràng buộc quan trọng một câu `INSERT`/`UPDATE` sai, chạy trong khối `DO` và đếm số ca không bị chặn. Chỉ bắt đúng lớp lỗi ràng buộc (`check_violation`, `unique_violation`, `not_null_violation`...); **không bắt `others`**, vì như vậy một câu test viết sai cú pháp cũng được tính là "bị chặn đúng".
   - Trên Windows, đưa file vào container bằng `docker cp` rồi chạy `psql -f`, thay vì pipe qua PowerShell (tránh lỗi mã hoá ký tự).
3. Thử "đi" từng luồng trong SRS trên schema: mỗi bước ghi/đọc nằm ở bảng nào, ràng buộc nào chặn tình huống lỗi nào (trùng lệnh, số dư âm, hai bản ghi hiệu lực).

## Bước 6 — Ghi tài liệu

Theo `references/template.md`:
- Dự án đã có tài liệu data model → **cập nhật đúng mục của service sở hữu**, thêm ERD và các mục cho bảng mới, ghi lịch sử thay đổi. Không tạo file song song gây hai nguồn sự thật.
- Chưa có → tạo `docs/design/data-model.md` với mỗi service một mục.
- Câu hỏi mở mới: thêm vào `docs/open-questions.md` (ID `OQ-` tiếp theo, nguồn "Data model DQ#"); câu hỏi đã có thì chỉ bổ sung nguồn.

Báo cáo cho người dùng:
- danh sách bảng mới hoặc thay đổi, kèm chủ sở hữu;
- các ràng buộc bảo vệ tiền và dữ liệu;
- index và lý do;
- ước lượng khối lượng;
- câu hỏi mở;
- thay đổi **phá tương thích** với schema đang có, nếu có (đổi tên, đổi kiểu, thêm `NOT NULL` vào bảng có dữ liệu). Các thay đổi này cần kế hoạch migration expand → migrate → contract; skill chỉ ghi nhận, không viết migration.

## Chế độ cập nhật

Khi sửa bảng đã có: không đổi tên hay xoá cột một cách lặng lẽ. Ghi rõ cột bị bỏ là `deprecated` (kèm lý do và thời điểm dự kiến xoá), cột mới thêm vào ở dạng nullable hoặc có default nếu bảng đã có dữ liệu, và liệt kê các service hay truy vấn bị ảnh hưởng.
