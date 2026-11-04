# Accessibility, nội dung chữ, định dạng

## 1. Accessibility (WCAG 2.1 AA, theo NFR-UX-03)

| Hạng mục | Yêu cầu trong đặc tả |
|---|---|
| Tương phản | Chữ ≥ 4.5:1; chữ lớn, viền ô nhập, icon mang nghĩa ≥ 3:1 |
| Không chỉ dùng màu | Trạng thái và tiền vào/ra luôn có chữ và/hoặc icon, dấu `+`/`−` |
| Bàn phím | Mọi thao tác làm được bằng bàn phím; thứ tự focus theo thứ tự đọc; focus nhìn rõ (`ring`); dialog giữ focus bên trong và trả focus về chỗ cũ khi đóng |
| Nhãn | Mọi ô nhập có `label` liên kết; nút chỉ có icon phải có `aria-label` |
| Lỗi | Thông báo lỗi gắn với ô qua `aria-describedby`; khi gửi form lỗi, focus vào ô lỗi đầu tiên |
| Thay đổi động | Trạng thái thay đổi không do người dùng thao tác (đang xử lý → thành công, kết quả tra cứu) thông báo qua `aria-live="polite"`; lỗi chặn thao tác dùng `role="alert"` |
| Thời gian | Có giới hạn thời gian (OTP, phiên) thì cảnh báo trước và cho kéo dài khi có thể |
| Phóng to | Dùng được khi phóng to 200% và ở 320 px chiều ngang, không cuộn ngang (trừ bảng CMS) |
| Chuyển động | Tôn trọng `prefers-reduced-motion` |
| Ngôn ngữ | `<html lang="vi">` |

Component của shadcn/ui (dựa trên Radix) đã xử lý phần lớn focus và ARIA; đặc tả chỉ cần ghi những gì **riêng** của màn hình.

## 2. Giọng văn
- Xưng hô với khách hàng: "bạn"; câu ngắn, chủ động, lịch sự, không đùa khi nói về tiền.
- Trong CMS: ngắn gọn, chính xác, dùng thuật ngữ nghiệp vụ nội bộ nhất quán (lệnh, đối soát, maker, checker...).
- Một khái niệm một từ, dùng thống nhất toàn sản phẩm (ví dụ chọn "giao dịch" hoặc "lệnh" cho phía khách, không dùng lẫn).

## 3. Thông điệp
**Công thức thông báo lỗi:** chuyện gì đã xảy ra → (vì sao, nếu an toàn để nói) → khách nên làm gì → (mã tra soát nếu lệnh đã được tạo).

| Không nên | Nên |
|---|---|
| "Lỗi hệ thống (500)" | "Chưa thực hiện được. Vui lòng thử lại sau ít phút." |
| "Giao dịch UNKNOWN" | "Giao dịch đang được xử lý. Bạn sẽ nhận thông báo khi có kết quả." |
| "INSUFFICIENT_BALANCE" | "Số dư không đủ để thực hiện giao dịch." + nút "Nạp tiền" |
| "Thao tác thất bại" (mơ hồ) | Nói rõ thao tác nào và kết quả là gì |

- Lấy câu chữ lỗi từ SRS mục 6; chỉ điều chỉnh độ dài cho vừa chỗ hiển thị, không đổi nghĩa.
- Không hiển thị mã lỗi kỹ thuật, stack trace, tên service, `traceId` dạng thô cho khách (CMS được hiển thị `traceId`).
- Nút ghi bằng động từ cụ thể: "Chuyển tiền", "Gửi yêu cầu duyệt", "Thử lại"; tránh "OK", "Đồng ý" ở hành động tiền.
- Nội dung chữ ghi trong bảng "Nội dung chữ" với khoá (`field.amount.label`) để dev đưa vào file i18n.

## 4. Định dạng
| Loại | Định dạng | Ví dụ |
|---|---|---|
| Tiền | Dấu chấm phân cách nghìn, không thập phân, ký hiệu `₫` sau số, có khoảng trắng | `1.000.000 ₫` |
| Tiền vào / ra (lịch sử) | Dấu `+` / `−` trước số, kèm màu | `+100.000 ₫`, `−101.000 ₫` |
| Ngày giờ (khách) | `dd/MM/yyyy HH:mm`; trong ngày có thể dùng "Hôm nay, 14:30" | `04/11/2026 14:30` |
| Ngày giờ (CMS) | `dd/MM/yyyy HH:mm:ss`, giờ Việt Nam, ghi chú múi giờ ở header cột | |
| SĐT | Nhóm `0900 000 002` khi hiển thị; nhập thì chấp nhận có hoặc không có khoảng trắng | |
| Dữ liệu che | Theo định dạng server trả về; UI không tự che | `NGUYEN V*** A` |

Dùng `Intl.NumberFormat('vi-VN')` và `Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })` qua hàm định dạng dùng chung, không tự ghép chuỗi.
