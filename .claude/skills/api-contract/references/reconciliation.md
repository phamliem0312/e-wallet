# Bảng đối chiếu

Ghi ba bảng vào báo cáo (và tóm tắt vào api doc nếu có thay đổi). Mỗi dòng có trạng thái: ✅ đã đáp ứng · ➕ đã thêm vào hợp đồng · ❓ thành câu hỏi mở (ghi `OQ-NNN`) · ➖ không cần (ghi lý do).

## 1. Nhu cầu dữ liệu
Duyệt bảng "Dữ liệu" của **từng màn hình** trong đặc tả UI, rồi đến từng service tiêu thụ (API nội bộ).

| Người dùng (màn hình / service) | Cần | Endpoint · field | Trạng thái |
|---|---|---|---|
| UI F-PAY-03.S-04 (tải lại bằng URL) | Tên người nhận đã che | `GET /orders/{id}` · `order.counterpartyDisplay` | ➕ |
| UI F-PAY-03.S-03 | Nơi nhận OTP đã che | `POST /challenges` · `destinationMasked` | ➕ |

Cách tìm dòng thiếu:
- Mỗi chỗ trong UI có `{placeholder}` trong câu chữ → dữ liệu đó lấy từ đâu?
- Mỗi màn hình mở được bằng URL (tải lại trang) → dữ liệu phải lấy lại được từ API, không chỉ từ state của bước trước.
- Mỗi kiểm tra phía client "để báo sớm" → client có đủ dữ liệu không (ví dụ ngưỡng cấu hình)?

## 2. Mã lỗi
Duyệt SRS §6 của tính năng.

| Mã (SRS §6) | Điều kiện | Endpoint trả | HTTP / nơi | Dữ liệu cho thông điệp | Trạng thái |
|---|---|---|---|---|---|
| `LIMIT_EXCEEDED` | Vượt hạn mức | `POST /transfers` | `200`, `order.failureCode` | `order.failureDetail.remaining` | ➕ |

Kiểm thêm:
- Mã có trong SRS mà không endpoint nào trả → hoặc SRS thừa, hoặc API thiếu.
- Mã mà API trả nhưng SRS không có → thêm vào SRS (qua `srs-feature-spec`) hoặc bỏ khỏi API.
- Thông điệp trong SRS có `{biến}` → body có biến đó chưa?

## 3. Event
Duyệt SRS §3.6 (`EV-nn`) và danh mục event trong api doc.

| EV / consumer | Event | Field consumer cần | Có trong schema | Trạng thái |
|---|---|---|---|---|
| F-PAY-03.EV-04 → `limit` | `PaymentFailed` | `orderId` (= reservation id) | ✅ | ✅ |
| `notification` | `PaymentSucceeded` | `counterpartyWalletId`, `amount`, `fee` | ✅ | ✅ |

Kiểm thêm:
- Consumer phải gọi ngược producer để lấy dữ liệu → nên thêm field vào event.
- Field nhạy cảm dạng rõ → bỏ, hoặc thay bằng hash hay dạng đã che.

## Tổng kết
Cuối báo cáo ghi: tổng số dòng, số ✅ / ➕ / ❓ / ➖, và danh sách `OQ-NNN` mới hoặc đã cập nhật.
