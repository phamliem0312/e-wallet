# Ví dụ mẫu: trích đoạn đặc tả "Chuyển tiền ví–ví"

Đây là **trích đoạn rút gọn** để thấy mức chi tiết và cách ghi nhãn. Bản đầy đủ do chính quy trình này tạo ra nằm ở `docs/srs/features/F-PAY-03-chuyen-tien-vi-vi.md` (tham khảo khi cần xem một spec hoàn chỉnh, gồm cả mục 3.4, 3.5, 4, 5, 8, 9). Số liệu và hành vi lấy từ `docs/requirements.md` và `docs/design/`; khi viết thật, đọc lại nguồn thay vì chép từ đây. Ví dụ này cố ý để quy tắc che tên là tham số có câu hỏi mở thay vì tự đặt ra.

---

# F-PAY-03 — Chuyển tiền ví–ví

| | |
|---|---|
| Yêu cầu nguồn | FR-P2P-01..04 |
| Cross-cutting áp dụng | CC-MONEY, CC-SEC, CC-AUDIT |
| Tài liệu thiết kế | design §6.3 (luồng P2P), api §2 (Lệnh tiền) |

## 1.2 Phạm vi
- Trong phạm vi: chuyển tiền từ ví này sang ví khác trong hệ thống EWallet bằng số điện thoại người nhận.
- Ngoài phạm vi: chuyển ra ngân hàng (xem F-PAY-02 Rút tiền), chuyển tiền cho người chưa có ví.

## 3.1 Use case (trích)

#### F-PAY-03.UC-01 — Chuyển tiền cho người nhận bằng SĐT
- **Tiền điều kiện:** Người gửi đã đăng nhập; ví người gửi `ACTIVE`; cấp KYC ≥ mức tối thiểu cho P2P `[Cần xác nhận Q4]`.
- **Hậu điều kiện (thành công):** Lệnh `SUCCEEDED`; số dư người gửi giảm `amount + fee`; số dư người nhận tăng `amount`; hạn mức người gửi được tính; cả hai nhận thông báo; lệnh hiện trong lịch sử của cả hai.
- **Hậu điều kiện (thất bại):** Lệnh `FAILED` kèm `failureCode`; số dư không đổi; hạn mức đã dùng được hoàn lại.
- **Luồng chính:**
  1. Người gửi nhập SĐT người nhận và số tiền.
  2. Hệ thống hiển thị tên người nhận đã che một phần, phí, tổng tiền trừ, và yêu cầu xác thực (nếu có).
  3. Người gửi xác nhận (kèm OTP nếu được yêu cầu).
  4. Hệ thống thực hiện chuyển tiền và hiển thị kết quả `SUCCEEDED`.
- **Luồng thay thế:** `A1` (bước 2) Số tiền vượt ngưỡng xác thực tăng cường: hệ thống yêu cầu OTP (hoặc xác thực khuôn mặt theo F-ACC-02) trước bước 3.
- **Luồng lỗi:** `E1` (bước 4) Số dư không đủ: lệnh `FAILED`, `failureCode = INSUFFICIENT_BALANCE`. `E2` (bước 4) Vượt hạn mức: lệnh `FAILED`, `LIMIT_EXCEEDED`. `E3` (bước 3) Phí đã đổi so với bước 2: hệ thống từ chối với `FEE_CHANGED`, không tạo lệnh; người gửi xem lại phí.

## 3.2 Yêu cầu chức năng (trích)

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-PAY-03.FR-01 | Hệ thống phải cho phép người gửi xác định người nhận bằng số điện thoại đăng ký ví. | M | FR-P2P-01 |
| F-PAY-03.FR-02 | Trước khi người gửi xác nhận, hệ thống phải hiển thị tên người nhận đã che một phần theo quy tắc `p2p.recipient_mask_rule`, và không được hiển thị tên đầy đủ. | M | FR-P2P-01; quy tắc che [Cần xác nhận Q4] |
| F-PAY-03.FR-03 | Trước khi người gửi xác nhận, hệ thống phải hiển thị phí và tổng số tiền sẽ bị trừ. | M | FR-FEE-01; [Thiết kế api §2] |
| F-PAY-03.FR-04 | Khi nhận lệnh có `Idempotency-Key` đã được dùng bởi cùng người gửi với cùng nội dung, hệ thống phải trả lại kết quả của lệnh đầu tiên và không được tạo lệnh mới. | M | NFR-C-03 |
| F-PAY-03.FR-05 | Khi nhận lệnh có `Idempotency-Key` đã dùng nhưng nội dung khác, hệ thống phải từ chối với mã `IDEMPOTENCY_KEY_REUSED`. | M | NFR-C-03; [Thiết kế api §1] |
| F-PAY-03.FR-06 | Hệ thống phải thực hiện trừ ví người gửi, cộng ví người nhận và ghi nhận phí như một thao tác không thể dở dang: không có trạng thái trong đó chỉ một phần được áp dụng. | M | FR-P2P-02 |
| F-PAY-03.FR-07 | Hệ thống không được để số dư ví nhỏ hơn 0 trong mọi trường hợp, kể cả khi nhiều lệnh được gửi đồng thời. | M | FR-P2P-02; NFR-C-02 |
| F-PAY-03.FR-08 | Khi số tiền vượt `stepup.p2p.otp_threshold`, hệ thống phải yêu cầu xác thực tăng cường trước khi thực hiện lệnh. | M | FR-P2P-04; [Cần xác nhận Q4] |
| F-PAY-03.FR-09 | Hệ thống nên cho phép người gửi kèm lời nhắn tối đa `p2p.note.max_length` ký tự và từ chối lời nhắn chứa ký tự điều khiển. | S | FR-P2P-03; [Giả định] về ký tự bị cấm |

## 3.3 Quy tắc nghiệp vụ (trích)

| ID | Quy tắc | Tham số | Nguồn |
|---|---|---|---|
| F-PAY-03.BR-01 | Phí = giới hạn trong [`min_fee`, `max_fee`] của (`fixed_fee` + làm tròn lên đến đồng của `amount × percent_bp / 10.000`). | `fee.p2p.*` | FR-FEE-01, 03 |
| F-PAY-03.BR-02 | Người gửi và người nhận phải khác nhau. | — | [Giả định] |
| F-PAY-03.BR-03 | Ví người nhận phải ở trạng thái `ACTIVE`; ví `FROZEN` hoặc `CLOSED` không nhận được tiền. | — | [Thiết kế §6.9] |

> Ví dụ BR-01: `amount = 1.000.001`, `percent_bp = 10`, `fixed_fee = 0`, `min_fee = 1.000`, `max_fee = 20.000` → `⌈1.000,001⌉ = 1.001` → phí = 1.001.

## 6. Danh mục lỗi (trích)

| Mã lỗi | Điều kiện | HTTP / nơi trả | Thông điệp (vi) | Thử lại |
|---|---|---|---|---|
| `INSUFFICIENT_BALANCE` | Số dư khả dụng < amount + fee | `order.failureCode` (200) | "Số dư không đủ để thực hiện giao dịch." | Có, với khoá mới |
| `LIMIT_EXCEEDED` | Vượt hạn mức giao dịch/ngày/tháng | `order.failureCode` (200) | "Giao dịch vượt hạn mức. Hạn mức còn lại: {remaining} đ." | Không, tới khi hạn mức làm mới |
| `FEE_CHANGED` | `expectedFee` ≠ phí tính lại | 409 | "Phí đã thay đổi. Vui lòng xem lại." | Có, sau khi xem lại |
| `RECIPIENT_UNAVAILABLE` | Ví người nhận không `ACTIVE` | 422 | "Không thể chuyển tiền cho người nhận này." | Không |
| `IDEMPOTENCY_KEY_REUSED` | Cùng khoá, nội dung khác | 409 | (lỗi lập trình phía client, không hiển thị) | Không |

## 7. Tiêu chí chấp nhận (trích)

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-PAY-03.AC-01 | FR-06, BR-01 | Ví A `ACTIVE` số dư 1.000.000; ví B `ACTIVE`; phí P2P = 1.000 | A chuyển 100.000 cho B | Lệnh `SUCCEEDED`; số dư A = 899.000; số dư B tăng 100.000; doanh thu phí tăng 1.000 |
| F-PAY-03.AC-02 | FR-07 | Ví A số dư 100.000 (phí 0) | A gửi 2 lệnh chuyển 100.000 đồng thời, hai khoá khác nhau | Đúng một lệnh `SUCCEEDED`, lệnh còn lại `FAILED INSUFFICIENT_BALANCE`; số dư A = 0 |
| F-PAY-03.AC-03 | FR-04 | A đã chuyển thành công với khoá K1 | A gửi lại đúng nội dung với K1 | Nhận lại cùng lệnh; số dư không đổi thêm |
| F-PAY-03.AC-04 | FR-05 | A đã dùng khoá K1 với amount 100.000 | A gửi K1 với amount 200.000 | Bị từ chối `IDEMPOTENCY_KEY_REUSED`; không có lệnh mới |
| F-PAY-03.AC-05 | BR-02 | Ví A `ACTIVE` | A chuyển cho chính số điện thoại của A | Bị từ chối; không có lệnh nào được tạo |

## 8. Ma trận truy vết (trích)

| Yêu cầu nguồn | FR | BR | AC | Ghi chú |
|---|---|---|---|---|
| FR-P2P-01 | FR-01, FR-02 | BR-03 | (AC cho FR-01, 02) | |
| FR-P2P-02 | FR-06, FR-07 | — | AC-01, AC-02 | |
| FR-P2P-03 | FR-09 | — | (AC) | Mức S; ký tự bị cấm là giả định |
| FR-P2P-04 | FR-08 | — | (AC) | Chờ Q4 |

## 9. Câu hỏi mở (trích)

| # | Câu hỏi / Giả định | Ảnh hưởng | Ai quyết định |
|---|---|---|---|
| Q4 | Ngưỡng OTP và ngưỡng sinh trắc học cho P2P; cấp KYC tối thiểu | FR-08, UC-01 tiền điều kiện | Pháp chế / Rủi ro |
| Q5 | Có cho phép chuyển cho SĐT chưa có ví không? (hiện ngoài phạm vi) | 1.2 | Product |
