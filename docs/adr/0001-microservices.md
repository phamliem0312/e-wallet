# ADR-0001: Kiến trúc microservices thay cho modular monolith

- Trạng thái: Accepted
- Ngày: 2026-11-04

## Bối cảnh
Thiết kế v0.1 chọn modular monolith (một Spring Boot app chia 14 module, outbox JDBC, không dùng message broker). Sau đó có hai yêu cầu mới:
1. **Scale độc lập**: `payment` và `ledger` chịu tải cao hơn hẳn các phần còn lại (500 TPS lúc cao điểm).
2. **Chuẩn công ty / khách hàng**: hệ thống phải theo nền tảng microservices (Kubernetes, Kafka, CI/CD và deploy riêng cho từng service).

Team ước tính 4–6 dev.

## Các phương án
| Tiêu chí | Modular monolith | Microservices thô (~7) | Microservices mịn (14) |
|---|---|---|---|
| Đáp ứng chuẩn công ty | Không | Có | Có |
| Scale độc lập `payment`/`ledger` | Không (chỉ scale cả app) | Có, nhưng `ledger` gộp với wallet/limit/fee | Có, đầy đủ |
| Số saga / bước bù trừ | 0 | Ít | Một bước (reservation hạn mức) nhờ dồn mọi thao tác số dư vào `ledger` |
| Số hop trên một lệnh P2P | 0 | 2–3 | 5 |
| Chi phí vận hành | Thấp | Trung bình | Cao |

## Quyết định
Chọn **microservices mịn, 14 service**, vì:
- Đáp ứng cả hai yêu cầu: scale độc lập và chuẩn nền tảng.
- Tính đúng của tiền được giữ bằng cách: mọi thay đổi số dư nằm trong một DB transaction của `ledger`, và mọi lời gọi nội bộ đều idempotent nên retry được. Nhờ vậy không cần transaction phân tán, saga chỉ phải bù trừ cho reservation hạn mức.
- Monorepo cùng bộ `platform` starter dùng chung giảm chi phí cho mỗi service mới.

## Hệ quả
- **Tích cực:** scale và deploy từng service riêng; sự cố ở `notification`, `kyc` hay `audit` không chặn giao dịch tiền; tuân thủ chuẩn công ty.
- **Tiêu cực / chấp nhận:**
  - Thêm một mốc M1 chỉ cho nền tảng (K8s, mesh, Kafka, Schema Registry, CI/CD).
  - Độ trễ cộng dồn qua nhiều hop.
  - Bản sao dữ liệu nhất quán dần (eventual consistency).
  - Cần contract test và kiểm tra schema event.
  - Báo cáo phải gom dữ liệu qua event.
- **Khi nào xem lại:**
  - Chi phí vận hành vượt năng lực team → gộp `fee` và `limit` vào `payment` (không ảnh hưởng tính đúng của tiền).
  - Ghi > 400 TPS → shard `ledger` theo ví.
  - p99 của P2P > 500 ms → cache rule phí ở `payment`, hoặc chuyển `payment → ledger` sang gRPC.
