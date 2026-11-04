# Template Design Doc

Dùng đúng thứ tự các mục dưới. Mục không áp dụng thì ghi "Không áp dụng — <lý do>" thay vì xoá, để người review biết là đã cân nhắc.

```markdown
# <Tên tính năng/hệ thống>

- Trạng thái: Draft | In review | Approved
- Tác giả: <tên>
- Ngày: <yyyy-mm-dd>

## 1. Bối cảnh & mục tiêu
Vấn đề cần giải quyết, ai bị ảnh hưởng. 3–5 câu.

## 2. Phạm vi
- Trong phạm vi: ...
- Ngoài phạm vi: ... (nêu rõ để chặn scope creep)

## 3. Yêu cầu
### Chức năng
- FR1 ...
### Phi chức năng
| Hạng mục | Mục tiêu | Ghi chú |
|---|---|---|
| TPS đỉnh | ... | giả định / số liệu thật |
| Latency p99 | ... | |
| Availability | ... | |
| Nhất quán | strong cho số dư; eventual cho notification | |
| Compliance | KYC, audit, lưu trữ dữ liệu tại VN... | |

## 4. Kiến trúc tổng thể
Mermaid flowchart: Browser → Next.js (BFF) → gateway → các service liên quan → DB của từng service / Kafka → hệ thống ngoài. Với tài liệu cho một tính năng, chỉ vẽ các service tham gia.
Giải thích ngắn vai trò từng khối.

## 5. Service & ranh giới
| Service | Trách nhiệm trong luồng này | Sở hữu dữ liệu | Gọi đồng bộ | Event phát / nghe |
|---|---|---|---|---|
Dùng service đã có trong design doc chính; service mới phải có lý do (và ADR nếu thay đổi kiến trúc).

### 5.1 Giao tiếp
- Lời gọi đồng bộ mới: bên gọi → bên được gọi, endpoint `/internal/**`, idempotent theo id nào, timeout.
- Event mới: topic, key, `aggregateversion`, consumer và việc consumer làm.
- Kiểm: không có vòng gọi; không đọc DB của service khác; không có dữ liệu nhạy cảm dạng rõ trong event.

## 6. Luồng chính
Mỗi luồng quan trọng một sequence diagram Mermaid, gồm cả nhánh lỗi (timeout ngân hàng, trùng request, không đủ số dư).

## 7. API
Bảng endpoint: method, path, idempotent?, auth, request/response tóm tắt, mã lỗi.
Error format thống nhất (RFC 9457 Problem Details).

## 8. Data model
Ở mức kiến trúc: service nào lưu gì, ràng buộc chống trùng nằm ở đâu (idempotency key, external ref, ghi sổ). DDL chi tiết dùng skill `data-model-design` và cập nhật tài liệu data model của dự án; ở đây chỉ liên kết.

## 9. Nhất quán, lỗi & phục hồi
Transaction boundary (trong từng service), các bước saga và bước nào cần bù trừ, ghi bước trước khi gọi đối tác, outbox/inbox, retry/backoff, trạng thái UNKNOWN và đường chốt qua đối soát, hành vi khi từng service phụ thuộc hoặc Kafka không khả dụng.

## 10. Bảo mật
AuthN/AuthZ, bảo vệ dữ liệu nhạy cảm (mã hoá, masking log), rate limit, chống replay, OTP/step-up cho giao dịch lớn.

## 11. Quan sát & vận hành
Metric chính (business + kỹ thuật), log có correlation ID, alert, dashboard, runbook cho sự cố tiền lệch.

### 11.1 Khả năng kiểm thử
Với mỗi nhánh lỗi quan trọng ở mục 6 và 9, ghi cách **tái hiện nó ở môi trường test**:

| Tình huống cần test | Cách tái hiện | Chỉ có ở profile |
|---|---|---|
| Đối tác timeout / từ chối / callback trùng | Kịch bản của fake provider (ví dụ số tiền kết thúc bằng 13 thì timeout) | test, dev |
| Tiến trình chết giữa saga ở bước X | Fault injection theo bước (ví dụ header hoặc cấu hình `fault.payment.after-step=LIMIT_RESERVED`) | test |
| Service phụ thuộc chậm hơn timeout | Độ trễ cấu hình được ở client hoặc fake | test |
| Kafka/consumer chậm | Tạm dừng consumer group | test |

Một nhánh lỗi không tái hiện được thì cũng không test được, và test case của nó sẽ bị chặn. Cơ chế ép lỗi phải bị cấm ở `prod` giống `FakeProvider`.

## 12. Triển khai & migration
Thứ tự rollout, feature flag, Flyway migration, kế hoạch rollback.

## 13. Phương án đã cân nhắc
Ít nhất 1 phương án thay thế và lý do không chọn.

## 14. Rủi ro & câu hỏi mở
```

Lưu ý khi viết:
- Diagram Mermaid phải hợp lệ cú pháp (tránh ký tự `()` `:` không đóng ngoặc trong label; bọc label có ký tự đặc biệt bằng `"..."`).
- Con số quy mô: nếu là giả định, đánh dấu `(giả định)`.
- Doc dài vừa đủ cho phạm vi: một tính năng nhỏ không cần 14 mục đầy đặn — các mục không quan trọng viết 1–2 dòng.
