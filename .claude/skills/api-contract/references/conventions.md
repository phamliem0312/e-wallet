# Quy ước hợp đồng API & event

Nếu `docs/design/*-api.md` của dự án ghi khác, theo dự án và báo mâu thuẫn.

## 1. Path & method
- Khách: `/api/v1/...` · Nhân viên: `/admin/v1/...` · Nội bộ: `/internal/...` (không có version trong path; tương thích bằng quy tắc §7) · Callback đối tác: `/callbacks/{bankCode}`.
- Danh từ số nhiều, `kebab-case`: `/bank-links`, `/transfers`. Hành động không phải CRUD: sub-resource động từ, ví dụ `/bank-links/{sessionId}/confirm`, `/transfers/preview`.
- `GET` không có tác dụng phụ. `POST` tạo hoặc thực hiện lệnh. `PUT` thay toàn bộ, `PATCH` sửa một phần (hiếm dùng cho tiền). `DELETE` huỷ/gỡ.
- `operationId` duy nhất, `camelCase`, dạng động từ + danh từ (`createTransfer`, `previewTransfer`): client sinh ra dùng tên này.

## 2. Field & kiểu
- JSON `camelCase`. Thời gian ISO-8601 có offset (`format: date-time`).
- **Tiền:** `integer`, đơn vị đồng, `format: int64`, `minimum` phù hợp; tên field rõ nghĩa (`amount`, `fee`, `total`). Không dùng số thập phân hay chuỗi.
- ID: `string`, `format: uuid`. Mã nghiệp vụ (`externalRef`, `bankCode`): `string` có `pattern`.
- Enum: `string` + `enum`. **Client phải chịu được giá trị enum mới** (hiển thị mặc định thay vì crash). Ghi điều này trong `description` của enum có thể mở rộng (trạng thái, `failureCode`).
- Dữ liệu cá nhân: chỉ trả dạng đã che, field tên `...Masked` hoặc `...Display`. Không bao giờ trả CCCD, số tài khoản đầy đủ, token liên kết, OTP.
- Field tuỳ chọn: không có trong `required`. **Không dùng `null` để mang nghĩa**: vắng mặt nghĩa là không có.

## 3. Lệnh tiền
- Header `Idempotency-Key` (`format: uuid`) **bắt buộc**; thiếu → `400 IDEMPOTENCY_KEY_REQUIRED`.
- **Từ chối trước khi tạo lệnh** (validation, xác thực, phí đổi, trạng thái ví, rate limit) → 4xx + Problem. Gửi lại với cùng khoá được.
- **Lệnh đã được tạo** → `200` (`SUCCEEDED`/`FAILED`) hoặc `202` (`PROCESSING`/`UNKNOWN`) + `order`. Lý do thất bại ở `order.failureCode`; dữ liệu để dựng thông điệp ở `order.failureDetail` (ví dụ `remaining`).
- Gửi lại cùng khoá + cùng nội dung → trả lại **đúng** response của lệnh (cùng `order.id`); khác nội dung → `409 IDEMPOTENCY_KEY_REUSED`.
- Mẫu xem trước → tạo lệnh: `POST .../preview` trả `fee`, `total`, `requiredFactor`; lệnh gửi kèm `expectedFee`.

## 4. Lỗi
- RFC 9457 Problem Details (`application/problem+json`): `type`, `title`, `status`, `code`, `traceId`, `detail?`, `errors?` (lỗi theo field: `[{field, code, message}]`).
- `code` lấy từ **sổ mã lỗi** trong `common.yaml` (`ErrorCode`); mã mới phải thêm vào sổ, và phải có trong SRS §6 của tính năng.
- Mỗi operation liệt kê đúng các mã nó có thể trả (trong `description` của response hoặc `x-error-codes`), để đối chiếu với SRS.
- `title`/`detail` không chứa dữ liệu nhạy cảm. Thông điệp cho người dùng do frontend dựng từ `code` + dữ liệu, không dùng `title` của server.

## 5. Danh sách & phân trang
- Cursor: `?limit=` (mặc định 20, tối đa 100) + `?cursor=` (chuỗi opaque) → `{ items, nextCursor }`, `nextCursor` vắng mặt khi hết.
- Bộ lọc là query param đặt tên rõ (`status`, `from`, `to`); không nhận dữ liệu nhạy cảm qua query (SĐT trong query của tra cứu người nhận là ngoại lệ có chủ đích: có rate limit, không log query).

## 6. Bảo mật
- `securitySchemes`: `customerJwt` (bearer, do BFF gắn), `staffJwt` (bearer, IdP nhân viên), mTLS cho nội bộ (ghi ở `description`; mesh xử lý).
- Mỗi operation khai báo `security`. Endpoint trả dữ liệu của chủ sở hữu ghi rõ trong `description`: "chỉ trả dữ liệu của người gọi; tài nguyên của người khác → 404".
- Header `traceparent` khai báo một lần trong `common.yaml` và dùng ở mọi operation.

## 7. Tương thích
**Được phép** (không phá tương thích):
- Thêm endpoint.
- Thêm field tuỳ chọn vào request.
- Thêm field vào response/event.
- Thêm giá trị enum ở response, nếu enum đã ghi "có thể mở rộng".
- Nới ràng buộc của request.

**Phá tương thích**, phải có version mới hoặc giai đoạn chuyển đổi:
- Xoá hay đổi tên field hoặc endpoint.
- Đổi kiểu dữ liệu.
- Thêm field bắt buộc vào request.
- Siết ràng buộc của request.
- Đổi nghĩa của field.
- Đổi mã HTTP hay `code` của một tình huống.

Event: theo quy tắc tương tự trên Schema Registry (BACKWARD). Phá tương thích → `v2.json` và publish song song `v1`/`v2` trong thời gian chuyển đổi.

## 8. Event
- Envelope CloudEvents: `specversion`, `id`, `source`, `type`, `time`, `subject` (id aggregate), `aggregateversion`, `traceparent`, `dataschema`, `data`. JSON Schema mô tả phần `data`.
- `type` dạng quá khứ (`PaymentSucceeded`). Key Kafka = id aggregate.
- `data` mang đủ field cho **mọi consumer đã biết**, để consumer không phải gọi ngược lại producer. Không mang dữ liệu nhạy cảm dạng rõ (OTP, CCCD, số tài khoản).

## Checklist
- [ ] Ba bảng đối chiếu (dữ liệu, lỗi, event) không còn dòng thiếu chưa xử lý
- [ ] Mỗi lệnh tiền: `Idempotency-Key` bắt buộc; 200/202 kèm `order`; 4xx chỉ trước khi tạo lệnh; `409 IDEMPOTENCY_KEY_REUSED`
- [ ] Mỗi operation có `operationId`, `security`, danh sách mã lỗi khớp SRS §6
- [ ] Tiền là `integer`/`int64`; thời gian `date-time`; ID `uuid`
- [ ] Request đóng (`additionalProperties: false`); không có dữ liệu nhạy cảm dạng rõ trong response/event
- [ ] Enum mở rộng được có ghi chú cho client
- [ ] Mỗi schema có `example` dùng số liệu của AC
- [ ] Lint qua (hoặc ghi rõ là chưa lint); không có thay đổi phá tương thích ngoài ý muốn
- [ ] Đã cập nhật api doc và open-questions
