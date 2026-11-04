# Checklist ca test cho code tiền

Rà danh sách này cho mọi code chạm tới số dư, phí, hạn mức, trạng thái lệnh hoặc lời gọi ngân hàng. Không phải code nào cũng cần đủ mọi mục; bỏ mục nào thì nói rõ lý do.

## Số tiền
- [ ] `amount = 0` và số âm bị từ chối
- [ ] `amount = 1` (đơn vị nhỏ nhất)
- [ ] Giá trị rất lớn (gần `Long.MAX_VALUE / 10_000`) không bị tràn khi nhân với basis point
- [ ] Không có `double`/`float` trong phép tính; nếu thấy thì báo là bug
- [ ] Nạp tiền: `fee >= amount` bị từ chối (credit vào ví sẽ âm)

## Phí
- [ ] Làm tròn đúng quy tắc đã cấu hình (mặc định làm tròn lên đến đồng)
- [ ] Dưới `min_fee` → bằng min; trên `max_fee` → bằng max
- [ ] Biên của khoảng `amount_from` / `amount_to` (đúng biên, biên ± 1)
- [ ] Rule có `bank_code` cụ thể thắng rule null; xét theo `priority`
- [ ] `effective_from` / `effective_to`: đúng thời điểm chuyển giao
- [ ] `expectedFee` lệch với phí tính lại → `FEE_CHANGED`, không tạo order

## Hạn mức
- [ ] Đúng ngưỡng thì được; ngưỡng + 1 thì `LIMIT_EXCEEDED`
- [ ] Kỳ ngày/tháng tính theo **giờ VN**: giao dịch lúc 23:59:59 và 00:00:00 rơi vào hai kỳ khác nhau
- [ ] Ba tầng cùng áp dụng; tầng chặt nhất quyết định; tầng trần pháp lý không bị vượt
- [ ] Reserve gọi lại cùng `orderId` không trừ thêm
- [ ] Release chỉ một lần; release reservation không tồn tại là no-op
- [ ] Lệnh `UNKNOWN` vẫn giữ mức đã dùng
- [ ] Chưa có tier của khách → coi như tier 0

## Idempotency
- [ ] Cùng key + cùng nội dung → trả order cũ, không tạo order mới, không gọi thêm downstream
- [ ] Cùng key + nội dung khác → `IDEMPOTENCY_KEY_REUSED`
- [ ] **Retry sau khi thành công vẫn nhận kết quả cũ, kể cả khi challenge OTP đã bị dùng** (tra key trước khi kiểm challenge)
- [ ] Thiếu header `Idempotency-Key` → 400

## Sổ cái & số dư
- [ ] Mọi journal cân (Σ D = Σ C); journal lệch bị từ chối và không ghi gì
- [ ] Số dư không âm; `held <= balance`; `available = balance - held`
- [ ] Hold → capture giảm cả `held` lẫn `balance`; hold → release chỉ giảm `held`
- [ ] Capture hold đã release (hoặc ngược lại) bị từ chối
- [ ] Ví bị khoá: chặn ghi nợ, **vẫn cho ghi có**; ví đã đóng: chặn cả hai chiều
- [ ] Tài khoản hệ thống chia bucket: tổng các bucket đúng sau nhiều giao dịch

## State machine lệnh
- [ ] Mỗi chuyển trạng thái hợp lệ đều có test; chuyển trạng thái không hợp lệ bị từ chối
- [ ] `UPDATE` có điều kiện trạng thái cũ: hai đường cùng chốt một order (callback và resolver) → chỉ một bên thắng, không ghi sổ hai lần
- [ ] `SUCCEEDED`/`FAILED` là trạng thái cuối, không đổi được nữa

## Gọi ngân hàng / provider
- [ ] `Success` → ghi sổ; `Failed` → không ghi sổ, release hold và hạn mức
- [ ] `Unknown` / `Pending` / timeout → order `UNKNOWN`, **giữ hold**, **không** hoàn tiền
- [ ] `NotFoundAtBank` không tự chuyển thành `FAILED`
- [ ] `NotFoundLocal` + void thành công → `FAILED`; void trả trạng thái khác → làm theo trạng thái đó
- [ ] Recovery ở step `BANK_CALLED` **không bao giờ** gửi lại `debit`/`credit`
- [ ] Liên kết ngân hàng không thuộc `customerId` → `BANK_LINK_INVALID`, không gọi ngân hàng
- [ ] Ngân hàng đã trừ tiền nhưng `ledger` từ chối ghi có → ghi vào `SUSPENSE`, order `SUCCEEDED` + `CREDITED_TO_SUSPENSE`, không retry vô hạn
- [ ] Adapter gặp lỗi kỹ thuật thì trả `Unknown`, không throw

## Event
- [ ] Event được phát đúng loại, đúng dữ liệu, đúng `aggregateversion`
- [ ] Nghiệp vụ rollback thì không có event
- [ ] Consumer: giao trùng là no-op; event có version cũ bị bỏ qua

## Bảo mật trong test
- [ ] Khách A không đọc hay thao tác được order, ví, liên kết của khách B
- [ ] Log và response không chứa CCCD, số tài khoản đầy đủ, OTP (assert trên log appender test nếu cần)
