# Pattern dữ liệu tài chính

Mỗi pattern ghi: vấn đề → cách giải bằng schema → điều code vẫn phải làm (schema không thay được).

## 1. Tiền
- `bigint` đơn vị đồng; `CHECK (amount > 0)` cho số tiền giao dịch, `CHECK (fee >= 0)`.
- Lưu **cả phí đã áp dụng và id rule phí** trên giao dịch (`fee`, `fee_rule_id`), không tính lại từ biểu phí hiện tại: biểu phí đổi thì lịch sử phải giữ nguyên.
- Số dư không âm: `CHECK (balance >= 0)`, `CHECK (held >= 0 AND held <= balance)`. Tài khoản hệ thống được phép âm thì dùng cờ trên dòng để `CHECK` biết (`allow_negative or balance >= 0`).

## 2. Idempotency
- Lệnh từ client: `UNIQUE (customer_id, idempotency_key)` trên chính bảng lệnh, cộng cột `request_hash` để phát hiện cùng khoá mà khác nội dung.
- Lệnh gửi đối tác: `external_ref` `UNIQUE`, **tạo trước khi gọi** đối tác.
- Ghi sổ: `UNIQUE (reference_type, reference_id, purpose)` trên journal, để một nghiệp vụ không bao giờ được ghi sổ hai lần.
- Consumer event: bảng `inbox (event_id, consumer)` làm khoá chính.
- *Code vẫn phải:* insert và xử lý trong cùng transaction; khi vi phạm unique thì đọc bản ghi cũ và trả lại.
- ⚠️ **Partition làm yếu unique idempotency.** PostgreSQL buộc `UNIQUE` trên bảng partition phải chứa cột partition. Nếu bảng lệnh partition theo `created_at`, khoá thành `(customer_id, idempotency_key, created_at)`; lần gửi lại vắt qua ranh giới partition (23:59:59 → 00:00:01 cuối tháng) có `created_at` mới nên **tạo lệnh thứ hai**. Cách xử lý:
  - Không partition bảng chứa khoá idempotency.
  - Hoặc tách khoá ra bảng riêng không partition.
  - Hoặc lấy cột partition từ giá trị xác định (không phải thời điểm insert), như `wallet_activity.created_at = payment_order.created_at`.

## 3. State machine
- `status varchar + CHECK` liệt kê đủ trạng thái; ghi bảng chuyển trạng thái hợp lệ trong tài liệu (lấy từ SRS 3.4).
- Cột thời điểm cho trạng thái quan trọng (`decided_at`, `closed_at`) hoặc bảng lịch sử trạng thái nếu cần truy vết từng bước.
- Cột phục vụ khôi phục (`saga_step`, `next_check_at`, `check_attempts`) kèm partial index cho hàng đợi.
- *Code vẫn phải:* `UPDATE ... WHERE status = :expected` để hai tiến trình không cùng chốt một lệnh.

## 4. Sổ cái
- `journal` + `entry` chỉ thêm; `entry.amount > 0`, `direction in ('D','C')`; sửa sai bằng journal đảo (`reversal_of`).
- Số dư lưu riêng, cập nhật cùng transaction với entry. Tài khoản bị ghi liên tục: chia nhiều dòng số dư (bucket) để giảm tranh chấp lock.
- Σ D = Σ C không kiểm được bằng `CHECK` → kiểm trong transaction và đối chiếu hằng ngày; ghi rõ trong tài liệu.

## 5. Giữ tiền (hold)
- Bảng `hold` với `reference_id UNIQUE`, `status in ('ACTIVE','CAPTURED','RELEASED')`; số tiền đang giữ phản ánh trong cột `held` của số dư.

## 6. Cấu hình có hiệu lực theo thời gian (phí, hạn mức, ngưỡng)
- `effective_from`, `effective_to` (null = vô hạn), `approval_id` (maker–checker), không sửa dòng đã có hiệu lực.
- Nếu yêu cầu "không chồng lấn hiệu lực" cho cùng phạm vi: dùng exclusion constraint (`btree_gist` + `tstzrange`), hoặc ghi rõ là kiểm ở ứng dụng.

## 7. Hạn mức đã dùng
- Bảng tổng hợp theo `(wallet_id, scope, period_key)`; cộng dồn bằng `INSERT ... ON CONFLICT DO UPDATE ... WHERE used + :amt <= :max` (nguyên tử).
- `period_key` tính theo giờ Việt Nam ở ứng dụng; lưu dạng chuỗi hoặc `date`, không suy ra lúc truy vấn.
- Bảng reservation theo `orderId` để giữ chỗ, commit hoặc release đúng một lần.

## 8. Đối soát & truy vết
- Mọi giao dịch với đối tác có `external_ref` (của mình) và `bank_ref` (của đối tác), cùng index để so khớp.
- Log gọi đối tác chỉ thêm, đã che dữ liệu nhạy cảm, partition theo tháng.
- `trace_id` trên các bảng quan trọng để nối với log.

## 9. Dữ liệu cá nhân
- Không lưu dạng rõ: CCCD, số tài khoản đầy đủ, token liên kết → `*_enc` (AES-GCM, khoá từ KMS) + `*_hash` (HMAC, dùng để tra cứu và unique).
- Ảnh (CCCD, khuôn mặt) để ở object storage, DB chỉ lưu key.
- Cột hiển thị đã che (`*_masked`) tách riêng để không phải giải mã khi chỉ cần hiển thị.
- Bản ghi đồng ý (consent) chỉ thêm, có phiên bản điều khoản.
- Ghi rõ với từng bảng: thời hạn lưu trữ, cột bị ẩn danh khi hết hạn.
- Event và projection không chứa dữ liệu nhạy cảm dạng rõ (chỉ hash hoặc giá trị đã che).

## 10. Audit
- Bảng audit chỉ thêm (`REVOKE UPDATE, DELETE`), có `actor`, `action`, `target`, `before`/`after` (`jsonb`, đã che), `ip`, `trace_id`, và chuỗi hash nếu cần phát hiện bị sửa.

## 11. Đa ngân hàng, đa tiền tệ
- Thứ biến thiên theo đối tác (ngân hàng) là **dữ liệu** (bảng `bank`, tài khoản sổ cái theo `bank_code`), không phải cột riêng theo từng ngân hàng. Thêm ngân hàng = thêm dòng, không đổi schema.
- Có cột `currency` ngay từ đầu dù hiện chỉ có VND.
