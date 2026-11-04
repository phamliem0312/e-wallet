# Trade-off & ADR

## Format ADR
Lưu ở `docs/adr/NNNN-<slug>.md`, số tiếp theo số lớn nhất đang có. ADR thay thế ADR cũ thì cập nhật trạng thái ADR cũ thành `Superseded by ADR-XXXX`.

```markdown
# ADR-NNNN: <Quyết định>

- Trạng thái: Proposed | Accepted | Superseded by ADR-XXXX
- Ngày: <yyyy-mm-dd>

## Bối cảnh
Vấn đề, ràng buộc, quy mô (ghi rõ giả định), quyết định liên quan đã có.

## Các phương án
| Tiêu chí | Phương án A | Phương án B | ... |
|---|---|---|---|
(tiêu chí theo bối cảnh: tính đúng của tiền, độ phức tạp vận hành, độ trễ, chi phí, năng lực team, lock-in)

## Quyết định
Chọn X vì ... (2–4 lý do gắn với bối cảnh, không phải ưu điểm chung chung).

## Hệ quả
- Tích cực: ...
- Tiêu cực / chấp nhận: ...
- Khi nào xem lại: <tín hiệu đo được>
```

Luôn chốt **một khuyến nghị**. Phụ thuộc thông tin chưa có thì nói rõ thông tin đó và khuyến nghị theo giả định hợp lý nhất.

## Quyết định đã chốt của dự án (không mở lại nếu không có ADR mới)

| Chủ đề | Đã chọn | Nguồn |
|---|---|---|
| Kiến trúc | Microservices mịn, 14 service, monorepo | ADR-0001 |
| Message broker | Kafka + Schema Registry; outbox trong từng service | design §7 |
| Giao tiếp nội bộ đồng bộ | REST + OpenAPI | design §7, §13 |
| Saga | Orchestration, `payment` điều phối | design §6, §13 |
| Số dư | Chỉ `ledger`; tài khoản hệ thống chia bucket | design §6.2 |
| Database | PostgreSQL, database-per-service | design §8 |
| Đăng nhập khách / nhân viên | Tự xây trong `identity` / IdP OIDC | design §10 |

Người dùng muốn đổi một trong các mục trên → viết ADR mới, nêu rõ ADR hay mục design bị thay thế.

## Điểm xuất phát cho các câu hỏi mới

| Câu hỏi | Mặc định | Đổi khi |
|---|---|---|
| Tạo service mới hay thêm vào service có sẵn | Thêm vào service sở hữu dữ liệu liên quan | Dữ liệu riêng + vòng đời riêng + lý do scale/cô lập đo được |
| Gọi đồng bộ hay event | Event, nếu bên phát không cần câu trả lời để quyết định | Cần kết quả ngay (báo giá phí, giữ hạn mức, ghi sổ) |
| Gọi trực tiếp hay giữ bản sao dữ liệu | Gọi API của chủ sở hữu | Đọc nhiều, chấp nhận trễ, không dùng cho quyết định về tiền → bản sao qua event |
| REST hay gRPC cho một cặp service | REST | Đo thấy p99 vượt mục tiêu vì serialization/kết nối (ví dụ `payment → ledger`) |
| Relay outbox trong app hay Debezium | Relay trong `platform` | Công ty đã vận hành Kafka Connect/CDC |
| Redis | Rate limit, đếm OTP, cache cấu hình ngắn hạn | Không bao giờ là nguồn sự thật của số dư |
| Elasticsearch/OpenSearch | Chưa dùng; tìm kiếm bằng index Postgres trong từng service | Cần full-text xuyên service hoặc dashboard phân tích (nạp từ Kafka) |
| Gộp service khi quá tải vận hành | Gộp `fee`, `limit` vào `payment` trước | Theo tiêu chí "khi nào xem lại" của ADR-0001 |
