---
name: srs-feature-spec
description: Viết tài liệu đặc tả SRS chi tiết cho MỘT tính năng từ requirements, theo chuẩn IEEE 29148 rút gọn - mục đích và phạm vi, actor, use case (luồng chính/thay thế/lỗi), yêu cầu chức năng dạng "phải" kiểm chứng được, quy tắc nghiệp vụ, dữ liệu và validation, giao diện UI/API, danh mục lỗi, yêu cầu phi chức năng, tiêu chí chấp nhận Given/When/Then, ma trận truy vết về requirements. Mỗi tính năng một file trong docs/srs/features/. Dùng skill này khi người dùng muốn "viết SRS cho tính năng X", "đặc tả chi tiết FR-...", "feature spec", "viết use case và business rule", "chi tiết hoá yêu cầu thành tài liệu đặc tả", "làm rõ tính năng trước khi dev/test", kể cả khi chỉ đưa mã yêu cầu hoặc tên tính năng. Nên chạy srs-feature-map trước để có danh mục tính năng; đặc tả tạo ra là đầu vào cho srs-test-cases.
---

# Viết đặc tả SRS cho một tính năng

Một bản đặc tả tốt cho phép **ba nhóm người làm việc mà không phải hỏi lại nhau**: dev biết phải xây gì, QA biết phải kiểm chứng gì, người duyệt nghiệp vụ biết hệ thống sẽ cư xử thế nào. Vì vậy mỗi câu trong đặc tả phải *cụ thể, đơn nghĩa và kiểm chứng được*, và mọi chi tiết phải *truy vết được* về requirements hoặc ghi rõ là bổ sung/giả định.

Điều quan trọng nhất của skill này: **không bịa nghiệp vụ**. Đặc tả có vẻ đầy đủ mà chứa quy tắc do người viết tự nghĩ ra còn nguy hiểm hơn đặc tả còn trống, vì không ai biết chỗ nào là thật. Chỗ requirements chưa nói thì hoặc lấy từ design (có trích dẫn), hoặc ghi giả định, hoặc nêu thành câu hỏi mở.

Viết bằng **tiếng Việt**; ID, tên field, mã lỗi và thuật ngữ kỹ thuật giữ nguyên.

## Bước 1 — Xác định tính năng và đọc nguồn

1. Tìm tính năng trong `docs/srs/index.md` (theo ID `F-xxx-nn` hoặc tên). Từ đó lấy: yêu cầu sở hữu, yêu cầu liên quan, phụ thuộc, cross-cutting áp dụng.
2. Chưa có index → nếu người dùng đưa mã yêu cầu thì dùng luôn; nếu không, đề nghị chạy `srs-feature-map` trước (nhanh hơn việc đoán ranh giới tính năng rồi sửa lại).
3. Đọc, theo thứ tự:
   - Nội dung đầy đủ của từng yêu cầu sở hữu và liên quan trong `docs/requirements.md` (cùng các phần giải thích chi tiết ở cuối tài liệu).
   - Cross-cutting áp dụng (bảo mật, tính đúng của tiền, tuân thủ...).
   - Design doc liên quan: luồng, state machine, mã lỗi, endpoint, tên field, bảng dữ liệu (`docs/design/`).
   - Đặc tả của các tính năng phụ thuộc đã viết, để dùng **đúng cùng thuật ngữ** và không mâu thuẫn.
4. Spec đã tồn tại → chế độ **cập nhật** (cuối file).

## Bước 2 — Phân tích khoảng trống trước khi viết

Lập bảng nháp (trong đầu hoặc ghi chú, không cần đưa vào file): với mỗi mục của template, nguồn nào trả lời được?

| Tình huống | Cách xử lý |
|---|---|
| Requirements nói rõ | Dùng, trích dẫn ID |
| Requirements im lặng nhưng design đã quyết (luồng, mã lỗi, trạng thái) | Dùng, ghi `[Thiết kế §x]` |
| Là hành vi tiêu chuẩn gần như không có lựa chọn khác (ví dụ ẩn mật khẩu khi nhập) | Dùng, ghi `[Giả định]` để người duyệt thấy |
| Là quyết định nghiệp vụ có nhiều lựa chọn hợp lý (ngưỡng, thời hạn, ai được phép, văn bản pháp lý) | **Không tự chọn.** Dùng tham số cấu hình (`limit.tier1.p2p.per_txn`), ghi `[Cần xác nhận Q#]`, thêm vào câu hỏi mở |
| Requirements mâu thuẫn nhau hoặc mâu thuẫn design | Nêu mâu thuẫn ở câu hỏi mở; không chọn một bên ngầm |

Con số liên quan pháp lý (⚖️) luôn là tham số cấu hình có ghi chú "Pháp chế xác nhận", không bao giờ là sự thật cứng.

## Bước 3 — Viết đặc tả

Dùng cấu trúc ở `references/template.md`. Tuân thủ `references/writing-rules.md` (cách viết một yêu cầu, từ ngữ cấm, nội dung bắt buộc cho tính năng chạm tới tiền). Xem `references/example.md` để thấy mức chi tiết mong đợi.

Nguyên tắc chính:
- **Nguyên tử:** một câu = một hành vi; hành vi bắt buộc dùng "phải", khuyến nghị dùng "nên", tuỳ chọn dùng "có thể".
- **Kiểm chứng được:** đọc câu nào cũng nghĩ ra được một test pass/fail. Thay "nhanh" bằng "p99 < 300 ms"; thay "thông báo cho khách" bằng "gửi thông báo kênh X nội dung Y trong thời gian Z".
- **Cái gì, không phải thế nào:** mô tả hành vi quan sát được và hợp đồng bên ngoài (API, mã lỗi, nội dung thông báo). Không mô tả bảng DB, service nội bộ hay thuật toán nếu không phải là một yêu cầu; những thứ đó thuộc design doc, chỉ cần liên kết tới.
- **Truy vết:** mỗi FR/BR ghi nguồn (ID yêu cầu gốc). Nội dung bổ sung ghi `[Thiết kế §x]` / `[Giả định]` / `[Cần xác nhận Q#]`.
- **Đầy đủ lối ra:** mỗi use case có luồng chính, luồng thay thế và luồng lỗi. Đặc tả chỉ có happy path là đặc tả chưa xong.
- **Tiêu chí chấp nhận** (Given/When/Then) phủ mọi FR và BR; đây là cầu nối sang `srs-test-cases`.

Quy mô theo tính năng: tính năng nhỏ thì các mục viết ngắn, mục không áp dụng ghi "Không áp dụng — lý do" (không xoá, để người duyệt biết là đã cân nhắc).

## Bước 4 — Tự kiểm

Duyệt checklist ở cuối `references/writing-rules.md`. Tối thiểu:
- Mỗi yêu cầu sở hữu đều xuất hiện trong ma trận truy vết (hoặc có lý do được ghi).
- Mỗi FR, BR, ST (chuyển trạng thái) và EV (đầu ra phụ) có ít nhất một AC; mỗi AC trỏ về chúng.
- Không còn từ mơ hồ trong danh sách cấm; mọi con số có đơn vị hoặc là tham số cấu hình.
- Không có quyết định nghiệp vụ nào do mình tự nghĩ mà không gắn nhãn.
- Thuật ngữ khớp với các spec đã có.

## Bước 5 — Ghi file và cập nhật index

1. Ghi `docs/srs/features/<ID>-<slug>.md` (tạo thư mục nếu chưa có). Tên file theo index; chưa có index thì tự đặt theo quy ước và nói rõ.
2. Cập nhật cột "Spec" của tính năng trong `docs/srs/index.md` thành liên kết + trạng thái `Draft`.
3. Thêm câu hỏi mở mới vào `docs/open-questions.md`: ID `OQ-` tiếp theo, nguồn "SRS <ID> Q#", người quyết định, việc bị chặn, ưu tiên. Câu hỏi đã có trong danh sách (từ tài liệu khác) thì chỉ bổ sung nguồn, không tạo dòng mới.
3. Báo cáo ngắn gọn cho người dùng: số FR/BR/AC đã viết, danh sách **câu hỏi mở** và **giả định** (đây là phần người dùng cần quyết định nhất), mâu thuẫn tìm thấy, và gợi ý bước kế tiếp: duyệt spec, rồi chạy `srs-test-cases` để sinh test case từ AC.

## Chế độ cập nhật

Spec đã tồn tại:
- **Giữ nguyên ID** (`F-xxx.FR-nn`, `.BR-nn`, `.AC-nn`) và không dùng lại ID đã bỏ; mục bị bỏ thì đánh dấu `[REMOVED]` và ghi lý do. Lý do: test case và code đang trích các ID này.
- Chỉ sửa phần bị ảnh hưởng bởi thay đổi; không viết lại cả file (làm vậy sẽ che mất thay đổi thật khi review).
- Tăng version, thêm dòng vào "Lịch sử thay đổi" nêu rõ ID nào đổi và vì sao. Báo cho người dùng các test case/AC có thể bị ảnh hưởng.
