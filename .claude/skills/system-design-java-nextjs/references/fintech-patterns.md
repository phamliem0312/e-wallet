# Pattern fintech / ví điện tử

Lý do chung: lỗi trong hệ thống tiền không chỉ là bug — nó là mất tiền thật, khiếu nại, và vi phạm quy định. Thiết kế phải đảm bảo **không mất, không nhân đôi, luôn truy vết được**.

## 1. Ledger double-entry
- Bảng `ledger_account` (ví user, tài khoản nội bộ: `cash_in_transit`, `fee_revenue`, `bank_settlement_<bank>`...).
- Bảng `ledger_entry` bất biến (chỉ INSERT): `id, journal_id, account_id, direction (DEBIT/CREDIT), amount (>0), currency, created_at`.
- Bảng `journal` (một nghiệp vụ = một journal): `id, type, reference_id, created_at`. Ràng buộc: tổng DEBIT = tổng CREDIT trong một journal (kiểm ở code trong cùng transaction + job kiểm tra định kỳ).
- Số dư: cột `balance` trên `wallet`/`ledger_account` cập nhật **trong cùng transaction** với việc insert entry (đọc nhanh), và luôn tái tạo được từ entry (kiểm chứng). Không có đường nào sửa `balance` mà không có entry.
- Sửa sai bằng **bút toán đảo** (reversal), không UPDATE/DELETE entry.

Ví dụ chuyển 100.000đ A→B, phí 1.000đ:
| account | DEBIT | CREDIT |
|---|---|---|
| wallet_A | 101.000 | |
| wallet_B | | 100.000 |
| fee_revenue | | 1.000 |

## 2. Idempotency
- Client gửi `Idempotency-Key`; server lưu bảng `idempotency_record(key, user_id, request_hash, status, response, created_at)` với **unique (user_id, key)**.
- Cùng key + cùng request hash → trả lại response cũ. Cùng key + request khác → 422/409.
- Insert record và thực hiện nghiệp vụ trong **cùng transaction** (hoặc insert trước với status IN_PROGRESS rồi cập nhật) để hai request song song không cùng chạy.
- Với đối tác ngoài: gửi kèm mã tham chiếu duy nhất của mình (`external_ref` unique) để họ cũng chống trùng.

## 3. Đồng thời khi trừ tiền
- `SELECT ... FOR UPDATE` trên ví (lock theo thứ tự id tăng dần nếu khoá nhiều ví), kiểm số dư, ghi entry, cập nhật balance, commit.
- Hoặc câu điều kiện nguyên tử: `UPDATE wallet SET balance = balance - :amt WHERE id = :id AND balance >= :amt` rồi kiểm số dòng.
- Constraint DB `CHECK (balance >= 0)` làm lưới an toàn cuối.
- Ví "nóng" (merchant nhận nhiều): cân nhắc sub-account/sharding số dư hoặc ghi có bất đồng bộ.

## 4. State machine giao dịch
`CREATED → PENDING → SUCCEEDED | FAILED`, thêm `UNKNOWN` khi gọi đối tác bị timeout (không biết thành công hay chưa).
- Chuyển trạng thái chỉ theo bảng cho phép; cập nhật có điều kiện `WHERE status = :expected`.
- `UNKNOWN` **không** được tự coi là FAILED và hoàn tiền — phải truy vấn lại đối tác hoặc chờ reconcile. Hoàn tiền nhầm khi đối tác đã ghi nhận = mất tiền.
- Tiền giữ tạm (hold/reserve) khi PENDING: chuyển sang tài khoản `hold` thay vì trừ thẳng, release/capture khi có kết quả.

## 5. Outbox & event
- Ghi event vào bảng `outbox` của service, trong cùng transaction với thay đổi dữ liệu; relay đẩy lên Kafka. Không dual-write DB + Kafka.
- Event có `aggregateversion`; consumer áp bản sao dữ liệu theo version vì event có thể đến lệch thứ tự.
- Consumer idempotent (bảng `inbox(event_id, consumer)`), vì giao nhận là at-least-once.

## 6. Saga (luồng tiền nhiều service)
- Dồn mọi thao tác cần nguyên tử về tiền vào **một transaction của `ledger`**: saga khi đó chỉ phải bù trừ cho các bước trước ghi sổ (ví dụ giữ chỗ hạn mức).
- Orchestration: `payment` điều phối, lưu trạng thái và bước saga (`saga_step`) trong DB của nó; dễ biết lệnh đang ở đâu và dễ debug hơn choreography.
- Ghi bước "đã gọi đối tác" **trước** khi gọi; recovery từ bước đó chỉ hỏi trạng thái.
- Mỗi bước và mỗi hành động bù đều idempotent theo id lệnh, nên recovery chạy lại từ bất kỳ bước nào cũng an toàn.

## 7. Đối soát (reconciliation)
- Job hàng ngày so khớp giao dịch nội bộ với file/API của ngân hàng/cổng theo `external_ref`; ghi lệch vào bảng `recon_discrepancy` để xử lý thủ công/tự động.
- Job kiểm tra nội bộ: tổng mọi entry = 0 theo currency; balance = sum(entry) cho từng ví.

## 8. Bảo mật & compliance
- KYC theo cấp (hạn mức giao dịch theo mức xác thực), giới hạn hạn mức ngày/tháng.
- OTP/step-up cho giao dịch vượt ngưỡng, đổi thiết bị.
- Audit log bất biến cho thao tác admin và mọi thay đổi trạng thái tiền: ai, lúc nào, từ IP/thiết bị nào, trước/sau.
- Không lưu số thẻ đầy đủ — dùng tokenization của cổng (tránh phạm vi PCI-DSS). Mã hoá cột nhạy cảm (CCCD, số tài khoản).
- Lưu trữ dữ liệu và log theo quy định hiện hành (kiểm tra với bộ phận pháp chế; không khẳng định số năm cụ thể nếu chưa xác minh).
- Rate limit theo user/IP/thiết bị; phát hiện bất thường (velocity check) cho chống gian lận.

## 9. Tiền tệ & số
- VND không có phần lẻ: lưu `bigint` đơn vị đồng. Đa tiền tệ: `bigint` minor unit + `currency` hoặc `NUMERIC(19,4)`; Java `BigDecimal`/`long`, không `double`.
- Quy tắc làm tròn phí ghi rõ (HALF_UP/HALF_EVEN) và ai chịu phần lẻ.
- Thời gian: lưu UTC (`timestamptz`), hiển thị theo Asia/Ho_Chi_Minh; ngày chốt sổ/hạn mức theo giờ VN.
