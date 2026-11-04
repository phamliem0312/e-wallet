# Checklist review thiết kế / PR kiến trúc

Đọc cả code/tài liệu liên quan, không chỉ phần diff. Với mỗi vấn đề, nêu **tình huống cụ thể gây lỗi** (input, thứ tự sự kiện, tiến trình chết ở bước nào). Không mô tả được tình huống thì có lẽ đó không phải vấn đề thật; bỏ qua.

Mức độ:
- 🔴 Chặn: có thể mất hoặc nhân đôi tiền, lộ dữ liệu, sai số dư, không truy vết được.
- 🟠 Nên sửa: lỗi khi tải cao, đồng thời hoặc sự cố; khó vận hành; vi phạm ranh giới service.
- 🟡 Gợi ý: đơn giản hoá, đặt tên, over-engineering.

## Tiền & nhất quán (kiểm đầu tiên)
- [ ] Mọi thay đổi số dư đi qua `ledger`, trong một DB transaction? Có luồng nào cần transaction xuyên service không?
- [ ] Lệnh từ client: **tra idempotency key trước** khi kiểm OTP, phí, trạng thái ví? (Nếu không: gửi lại sau khi thành công nhận lỗi → khách chuyển lần nữa.)
- [ ] Lời gọi nội bộ có tác dụng phụ có id idempotent và được retry an toàn?
- [ ] Saga: bước "đã gọi đối tác" được ghi **trước** khi gọi? Recovery ở bước đó chỉ hỏi trạng thái, không gửi lại?
- [ ] Timeout/không rõ kết quả → `UNKNOWN`, giữ nguyên tiền đang giữ, không tự hoàn tiền? `NOT_FOUND` từ đối tác không tự coi là thất bại?
- [ ] Đối tác đã trừ tiền nhưng bước ghi sổ bị từ chối về nghiệp vụ → có đường an toàn (tài khoản treo, alert), không retry vô hạn?
- [ ] Kiểm chủ sở hữu ở service giữ dữ liệu (liên kết ngân hàng thuộc đúng khách, lệnh thuộc đúng người)?
- [ ] Có quyết định về tiền dựa trên bản sao dữ liệu (eventual) không?
- [ ] Race giữa thao tác vòng đời (khoá, đóng ví) và lệnh tiền đang chạy?
- [ ] Gọi HTTP hay đối tác bên trong DB transaction đang giữ lock?

## Ranh giới service
- [ ] Có service đọc/ghi DB của service khác, hoặc khoá ngoại xuyên service?
- [ ] Chức năng mới đặt đúng service sở hữu dữ liệu (đối chiếu design §5)? Có service mới nào thiếu lý do?
- [ ] Chiều gọi đồng bộ: có vòng gọi? Chuỗi gọi trên đường xử lý lệnh tiền dài bao nhiêu hop?
- [ ] `/internal/**` có lọt qua gateway không? AuthorizationPolicy đã liệt kê bên gọi mới chưa?

## Event & bất đồng bộ
- [ ] Event ghi qua outbox cùng transaction (không dual-write DB + Kafka)?
- [ ] Event có key partition đúng aggregate, có `aggregateversion`; consumer áp bản sao theo version (event đến lệch thứ tự)?
- [ ] Consumer idempotent (inbox), có retry + DLT, có alert khi DLT có event?
- [ ] Event chứa dữ liệu nhạy cảm dạng rõ (OTP, CCCD, số tài khoản)?
- [ ] Schema event thay đổi tương thích ngược? Đã cập nhật danh mục event + consumer?
- [ ] Event dùng cho quyết định nhạy thời gian (OTP, chốt lệnh) mà chịu được trễ không?

## Bảo mật
- [ ] Token không ở `localStorage`; rule tiền không nằm ở frontend.
- [ ] Dữ liệu nhạy cảm không xuất hiện trong log, response, URL, event.
- [ ] Rate limit, step-up cho thao tác rủi ro; audit cho thao tác tiền và thao tác của nhân viên; maker–checker cho điều chỉnh tiền.

## Vận hành
- [ ] Metric và alert cho luồng mới: `UNKNOWN` tồn đọng, consumer lag, DLT, outbox tồn, lệch đối soát.
- [ ] Service phụ thuộc chết thì luồng này cư xử ra sao (an toàn = từ chối, không làm sai)?
- [ ] Thay đổi API/event/DB triển khai được theo kiểu rolling (expand → migrate → contract)?

## Khả năng kiểm thử
- [ ] Mỗi nhánh lỗi quan trọng (timeout đối tác, chết giữa saga, phụ thuộc chậm, event trùng/đến muộn) có cách tái hiện ở môi trường test?
- [ ] Cơ chế ép lỗi bị cấm ở `prod`?
- [ ] Đồng hồ (`Clock`) và sinh ID inject được, để test kỳ ngày/tháng và biên thời gian?

## Next.js
- [ ] Trang dữ liệu tài chính không bị cache tĩnh/ISR?
- [ ] `Idempotency-Key` giữ nguyên khi bấm lại, sinh mới sau khi lệnh `FAILED`?
- [ ] Response `202`/timeout hiển thị "Đang xử lý", không báo "thất bại"?

## Format output
```
## Kết quả review: <tên>

### 🔴 Chặn
1. **<tiêu đề>** — `<file:line hoặc mục tài liệu>`
   - Vấn đề: ...
   - Tình huống: ...
   - Sửa: ...

### 🟠 Nên sửa
### 🟡 Gợi ý

**Kết luận:** OK để triển khai | Cần sửa các mục 🔴 trước
```
Mức nào không có mục thì bỏ heading đó. Nếu sửa trực tiếp vào tài liệu, ghi vào lịch sử thay đổi của tài liệu đó.
