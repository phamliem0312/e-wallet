# Backend — Spring Boot microservices

Nguồn sự thật về danh sách service, DB và luồng là design doc hiện hành. File này là quy ước kỹ thuật chung cho mọi service.

## Phiên bản
Java LTS (21; có thể lên 25), Spring Boot bản stable mới nhất khi scaffold, Gradle Kotlin DSL. Phiên bản do Gradle BOM trong `platform/` quản lý; service không tự khai báo version thư viện.

## Monorepo

```
source-code/
├── platform/          # BOM + starter dùng chung (xem dưới)
├── contracts/         # OpenAPI nội bộ + JSON Schema event + bảng approval type → service
├── services/<tên>/    # 14 service
├── gateways/          # api-gateway, admin-gateway (Spring Cloud Gateway)
├── frontend/          # pnpm workspace: apps/web, apps/cms, packages/api-client
└── deploy/            # Helm chart chung + values theo service/môi trường
```

**`platform` starter** là lý do 14 service vẫn vận hành được với team nhỏ. Mọi thứ lặp lại giữa các service nằm ở đây:
- Security: resource server JWT, kiểm ownership.
- Outbox, relay, inbox.
- Kafka: producer idempotent, consumer + retry + DLT.
- Observability: OpenTelemetry, Micrometer, log JSON có masking.
- `ProblemDetail` và mã lỗi; `Money`, các kiểu ID.
- Kiểm tra chặn `FakeProvider` ở profile `prod`.

Service mới không được tự viết lại các thành phần này.

## Cấu trúc trong một service

```
services/payment/src/main/java/com/ewallet/payment/
├── web/            # REST controller: public (/api/v1) và nội bộ (/internal), tách controller
├── application/    # use case, @Transactional, điều phối saga
├── domain/         # aggregate, state machine, rule, không phụ thuộc Spring
├── infrastructure/ # repository, client gọi service khác, Kafka consumer/producer
└── config/
```
- Controller chỉ map DTO, validate input; logic ở `application`/`domain`.
- `@Transactional` ở application service. **Không gọi HTTP hay đối tác bên trong DB transaction đang giữ lock.**
- Client gọi service khác sinh từ OpenAPI trong `contracts/`; bọc trong một interface ở `infrastructure` để test được.

## Dữ liệu
PostgreSQL, mỗi service một database và một DB user; Flyway migration riêng cho từng service. Chi tiết bảng và quy ước schema: dùng skill `data-model-design`.

## Giao tiếp
**Đồng bộ (REST nội bộ)**
- Spring `RestClient` + Resilience4j: timeout mặc định 2 giây (gọi ngân hàng qua `bank-gateway` có timeout riêng); circuit breaker theo service đích; retry tối đa 3 lần có jitter, **chỉ cho lời gọi idempotent**.
- Tìm service qua Kubernetes DNS; mTLS do service mesh lo.
- Header bắt buộc: `traceparent`; JWT người dùng (nếu có) được chuyển tiếp để service đích tự kiểm ownership.

**Bất đồng bộ (Kafka)**
- Ghi event vào `outbox` cùng transaction nghiệp vụ. Relay đọc bằng `FOR UPDATE SKIP LOCKED`, gửi với producer idempotent (`acks=all`), rồi đánh dấu đã gửi.
- Topic `<service>.events`, key = id của aggregate; envelope CloudEvents + `aggregateversion`; JSON Schema trong `contracts/`, kiểm tương thích trên Schema Registry trong CI.
- Consumer: insert `inbox(event_id, consumer)` cùng transaction xử lý. Bản sao dữ liệu chỉ áp event có version mới hơn. Retry 3 lần rồi đẩy vào `<topic>.dlt`; có công cụ replay.

**Tích hợp bên thứ 3**
Chỉ qua interface provider (`BankGateway`, `EkycProvider`, `NotificationSender`, `AmlScreeningProvider`) đặt trong service sở hữu. Kết quả là sealed type, gồm `Unknown`; adapter không throw khi gặp lỗi kỹ thuật.

## Bảo mật
- `api-gateway` kiểm JWT khách (JWKS của `identity`); `admin-gateway` kiểm JWT nhân viên từ IdP. Service kiểm lại JWT và ownership.
- AuthorizationPolicy của mesh liệt kê service được gọi từng `/internal/**`.
- Secret qua secret manager; mã hoá cột nhạy cảm bằng KMS.

## Quan sát & vận hành
- OpenTelemetry agent; trace đi qua HTTP và header Kafka.
- Metric nghiệp vụ, RED cho từng service, consumer lag, số event trong DLT, outbox tồn đọng.
- Job định kỳ dùng `@Scheduled` + ShedLock.

## Test
- Unit test cho domain.
- Integration test với Testcontainers (Postgres, Kafka).
- Contract test giữa các service (Pact hoặc Spring Cloud Contract).
- Kiểm tra tương thích schema event.
- Test chaos cho saga: kill tiến trình ở từng bước.

Chi tiết cách viết test: skill `unit-test-java-nextjs`.
