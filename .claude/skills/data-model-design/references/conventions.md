# Quy ước schema (PostgreSQL 16+)

Nếu tài liệu data model của dự án đã có quy ước khác, **theo dự án**; file này là mặc định khi chưa có.

## 1. Đặt tên
- `snake_case`, tiếng Anh, **số ít** cho tên bảng (`payment_order`, `wallet`), theo quy ước đang có.
- Schema trùng tên service (`payment.`, `ledger.`). Mỗi service một database; tên schema giúp DDL tự mô tả.
- Khoá chính `id`. Tham chiếu: `<thực_thể>_id` (`wallet_id`, `customer_id`).
- Thời điểm: `<sự_kiện>_at` (`created_at`, `decided_at`). Ngày nghiệp vụ: `<tên>_date` (`business_date`).
- Boolean: dạng khẳng định `is_`/`has_`, hoặc tính từ rõ nghĩa (`debit_blocked`); tránh phủ định (`not_active`).
- Cột mã hoá `<tên>_enc`; cột hash tra cứu `<tên>_hash`; giá trị đã che để hiển thị `<tên>_masked`.
- Ràng buộc và index có tên: `pk_`, `uq_<bảng>_<cột>`, `ck_<bảng>_<ý nghĩa>`, `fk_<bảng>_<bảng_đích>`, `ix_<bảng>_<cột>`. Tên tốt giúp đọc lỗi vi phạm ràng buộc trong log ngay lập tức.

## 2. Kiểu dữ liệu

| Dữ liệu | Kiểu | Không dùng |
|---|---|---|
| ID nghiệp vụ (lộ ra API, sinh ở ứng dụng) | `uuid` (UUIDv7: có thứ tự theo thời gian nên index không bị phân mảnh) | `serial` lộ ra ngoài (đoán được số lượng) |
| ID nội bộ khối lượng lớn (bút toán, log) | `bigint generated always as identity` | `int` (tràn) |
| Tiền | `bigint`, đơn vị nhỏ nhất (đồng) + `currency char(3)` | `float`, `double precision`, `money` |
| Tỷ lệ (phí %) | `int` basis point (10 = 0,1%) | số thực |
| Thời điểm | `timestamptz` (lưu UTC) | `timestamp` không múi giờ |
| Ngày nghiệp vụ (kỳ hạn mức, ngày đối soát) | `date`, tính theo giờ Việt Nam ở ứng dụng | suy ra từ `created_at` lúc truy vấn |
| Trạng thái, loại | `varchar(n)` + `CHECK (... in (...))` | `ENUM` của PostgreSQL |
| Chuỗi | `varchar(n)` có giới hạn hợp lý theo SRS; `text` cho nội dung dài tự do | `char(n)` (trừ mã cố định độ dài như tiền tệ) |
| Số điện thoại | `varchar(15)`, chuẩn hoá E.164 | lưu nhiều định dạng |
| Dữ liệu mã hoá, hash | `bytea` | base64 trong `varchar` |
| Dữ liệu không có cấu trúc cố định (payload gốc của đối tác, metadata) | `jsonb` | `jsonb` cho trường cần lọc, ràng buộc hay join |
| IP | `inet` | `varchar` |

## 3. Khoá & ràng buộc
- **Mặc định `NOT NULL`.** Cột nullable phải có ý nghĩa rõ cho null; ghi trong comment.
- Khoá ngoại **chỉ trong cùng database của service**. Tham chiếu sang service khác: chỉ cột id, kèm comment nguồn (`-- id từ identity.customer`).
- `CHECK` cho mọi bất biến kiểm được trên một dòng: `amount > 0`, `fee >= 0`, `held <= balance`, `checker_id <> maker_id`, `effective_to > effective_from`.
- `UNIQUE` cho mọi khoá nghiệp vụ: idempotency key, mã tham chiếu đối tác, `(event_id, consumer)` của inbox.
- **Partial unique index** cho quy tắc "chỉ một bản ghi ở trạng thái X": `create unique index ... where status = 'APPROVED'`.
- Bất biến nhiều dòng (sổ cái cân) không kiểm được bằng `CHECK` → ghi rõ cơ chế bảo đảm (trong transaction + job đối chiếu) trong ghi chú thiết kế.

## 4. Index
- **Mỗi index trỏ về một truy vấn** trong bảng truy vấn của tài liệu. Không có truy vấn thì không có index.
- Thứ tự cột: cột lọc bằng (`=`) trước, cột khoảng/sắp xếp sau. Keyset pagination: `(wallet_id, created_at desc, id desc)`.
- **Partial index** cho hàng đợi theo trạng thái: `where status in ('PROCESSING','UNKNOWN')`, vì chỉ phần nhỏ dữ liệu ở trạng thái đó.
- Ràng buộc `UNIQUE` đã tạo index; không tạo trùng.
- Bảng partition: index tạo trên bảng cha; `UNIQUE` trên bảng partition phải chứa cột partition. Nếu không chứa được, đảm bảo duy nhất theo cách khác (bảng phụ, inbox) và ghi rõ.
- Bảng ghi nhiều (sổ cái, log): giữ số index tối thiểu.

## 5. Theo loại thực thể

| Loại | Cột bắt buộc | Quy tắc |
|---|---|---|
| Aggregate | `id`, `status`, `version`, `created_at`, `updated_at` | Đổi trạng thái bằng `UPDATE ... WHERE id = ? AND status = ?` (hoặc theo `version`) |
| Append-only | `id`, `created_at` | `REVOKE UPDATE, DELETE` khỏi user ứng dụng; partition theo tháng nếu lớn; sửa sai bằng dòng bù |
| Cấu hình theo thời gian | `id`, `effective_from`, `effective_to`, `approval_id`, `created_at` | Không sửa dòng đã có hiệu lực; thay đổi = dòng mới; giao dịch lưu lại id rule đã áp dụng |
| Projection | khoá theo thực thể nguồn, `source_version`, `updated_at` | Chỉ consumer ghi; áp event khi `version > source_version`; thiếu dữ liệu → hành vi an toàn (ví dụ tier 0) |
| Tham chiếu | `code` làm khoá, `status` | |

Không dùng xoá mềm chung chung (`deleted_at` ở mọi bảng). Dùng trạng thái có nghĩa nghiệp vụ (`CLOSED`, `REVOKED`) hoặc xoá thật theo chính sách lưu trữ.

## 6. Partition, lưu trữ, khối lượng
- Partition theo `created_at` (thường theo tháng) khi bảng append-only có thể đạt hàng chục triệu dòng, hoặc khi có thời hạn lưu trữ cần detach/archive.
- **Trước khi partition, kiểm từng `UNIQUE` của bảng:** cột partition sẽ bị thêm vào mọi unique. Unique nào bảo vệ tiền (idempotency, mã đối tác, ghi sổ) mà bị yếu đi thì không partition bảng đó (xem fintech-data §2).
- Insert ra ngoài khoảng của mọi partition sẽ báo lỗi; ghi rõ job tạo partition trước.
- Ghi rõ: số dòng/ngày ước tính (giả định từ NFR), kích thước mỗi dòng ước tính, thời hạn lưu nóng, cách archive (export sang lưu trữ không sửa được, rồi detach partition), job tạo partition trước.
- Dữ liệu cá nhân hết hạn lưu trữ: cột nào được ẩn danh và bằng giá trị gì.
