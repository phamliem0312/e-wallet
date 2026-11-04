# Pattern — Web khách hàng

Người dùng là khách hàng phổ thông, đa số dùng trình duyệt điện thoại, nhiều người lo lắng khi chuyển tiền. Mục tiêu: **ít bước, chắc chắn, không bao giờ để khách nghi ngờ tiền của mình đang ở đâu.**

## 1. Quy tắc an toàn giao dịch tiền (bắt buộc cho mọi màn hình có thao tác tiền)

1. **Luôn có bước xác nhận riêng** trước khi gửi lệnh, hiển thị đủ: người nhận/nguồn tiền (đã che), số tiền, phí, **tổng bị trừ**, lời nhắn. Nút xác nhận nói rõ hành động và số tiền: "Chuyển 101.000 ₫", không phải "OK". Lý do: khách đọc lại lần cuối đúng con số sẽ bị trừ.
2. **Không gửi trùng:** nút bị khoá ngay khi bấm và hiển thị trạng thái đang gửi. `Idempotency-Key` sinh khi vào bước xác nhận và **giữ nguyên** khi khách bấm lại hoặc mạng lỗi rồi thử lại. Sau một lệnh `FAILED`, "Thử lại" phải tạo **khoá mới**.
3. **Kết quả chưa chắc chắn = "Đang xử lý"**, không phải "Thất bại". Response `202`, mất mạng hay timeout khi chờ kết quả: hiển thị "Giao dịch đang được xử lý. Bạn sẽ nhận thông báo khi có kết quả", kèm mã giao dịch; tự hỏi lại kết quả định kỳ; có nút "Xem lịch sử". Báo nhầm "thất bại" làm khách chuyển lần nữa và mất tiền gấp đôi.
4. **Phí hoặc số tiền thay đổi giữa chừng** (`FEE_CHANGED`): quay về bước xác nhận, **làm nổi bật** con số đã đổi (ví dụ "Phí đã thay đổi từ 1.000 ₫ thành 1.500 ₫"), bắt khách xác nhận lại.
5. **Số dư khả dụng nhìn thấy được** ở bước nhập số tiền. Vượt số dư thì báo ngay tại ô nhập, trước khi gửi (server vẫn kiểm lại).
6. **Ô nhập tiền:**
   - Bàn phím số (`inputMode="numeric"`), tự thêm dấu chấm phân cách nghìn khi gõ, không cho phần thập phân (VND), hiển thị `₫` ở cuối.
   - Dán chuỗi "1.000.000" hoặc "1000000" đều hiểu đúng.
   - Không dùng `type="number"`.
7. **Xác thực tăng cường:** dùng `InputOTP` 6 ô; hiển thị nơi nhận mã (đã che), đếm ngược thời gian gửi lại, số lần thử còn lại khi đã sai. Có thể tự gửi khi đủ 6 số, nhưng chỉ một lần (không gửi lại vì gõ thêm).
8. **Màn hình kết quả** có mã giao dịch (sao chép được), thời điểm, số tiền, trạng thái; hành động tiếp theo rõ ràng ("Về trang chủ", "Chuyển tiếp", "Xem chi tiết").
9. **Lỗi có mã tra soát:** mọi lỗi sau khi lệnh đã được tạo hiển thị mã giao dịch để khách đưa cho CSKH.
10. **Quay lại hoặc tải lại trang khi đang xử lý** không được tạo lệnh mới: trang kết quả đọc trạng thái lệnh theo mã, không gửi lại form.

## 2. Bố cục & điều hướng
- Mobile-first: một cột; nút hành động chính cố định ở đáy màn hình trên mobile (vùng ngón cái), có khoảng đệm an toàn (`pb-[env(safe-area-inset-bottom)]`).
- Thanh trên: nút quay lại + tiêu đề ngắn. Trang chủ: số dư nổi bật ở đầu, sau đó các hành động chính (Nạp, Rút, Chuyển), rồi giao dịch gần đây.
- Luồng nhiều bước hiển thị vị trí (ví dụ "Bước 2/3") khi có từ 3 bước trở lên.
- Không mở tab mới cho luồng giao dịch.

## 3. Form
- Nhãn luôn hiển thị phía trên ô (không chỉ dùng placeholder làm nhãn).
- Kiểm tra khi rời ô (blur) và khi gửi; không báo lỗi khi khách đang gõ dở. Ô tra cứu (ví dụ SĐT người nhận) tra cứu sau 500 ms ngừng gõ hoặc khi rời ô.
- Lỗi hiển thị ngay dưới ô, màu `destructive` **kèm icon và chữ**; khi gửi form có lỗi, focus vào ô lỗi đầu tiên.
- Giữ dữ liệu khách đã nhập khi có lỗi từ server.

## 4. Trạng thái màn hình
- **Đang tải:** `Skeleton` đúng hình dạng nội dung; không chặn cả trang bằng spinner.
- **Rỗng:** giải thích và gợi ý hành động ("Chưa có giao dịch nào. Nạp tiền để bắt đầu").
- **Lỗi tải:** nói rõ chưa tải được, nút "Thử lại"; giữ nội dung cũ nếu có.
- **Mất mạng:** banner "Không có kết nối mạng"; khoá nút gửi lệnh tiền.
- **Phiên sắp hết hạn** (NFR-S-02): dialog cảnh báo trước 60 giây, có nút "Tiếp tục phiên". Hết hạn khi đang ở luồng tiền → về đăng nhập, sau đó quay lại đúng màn hình kết quả nếu lệnh đã gửi.

## 5. Dữ liệu nhạy cảm
- Số tài khoản, SĐT người khác, tên người nhận: hiển thị dạng đã che từ server; UI không tự che hay tự bỏ che.
- Số dư: hiển thị mặc định. Tính năng ẩn số dư chỉ làm khi SRS yêu cầu (ghi câu hỏi mở nếu thấy cần).
- Không đưa dữ liệu nhạy cảm vào URL (query string), tiêu đề trang hay toast.

## 6. Tránh mẫu thiết kế lừa dối
- Phí luôn hiển thị trước khi xác nhận, không ẩn trong chữ nhỏ.
- Ô đồng ý (consent) không được tích sẵn; mỗi mục đích một ô (NFR-L-03).
- Nút huỷ hoặc quay lại có độ nổi bật hợp lý, không bị giấu.
