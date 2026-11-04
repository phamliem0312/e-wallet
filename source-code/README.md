# source-code

Monorepo backend (Java 21, Spring Boot 3.5, Gradle Kotlin DSL) và hợp đồng API. Trạng thái: **nền tảng M1 đang dựng**, chưa có service nghiệp vụ nào.

```
platform/
  platform-starter-core       Money, UUIDv7, Sensitive, ErrorCode/PlatformException, chặn @FakeProvider ở profile prod
  platform-starter-web        Problem (RFC 9457) + code + traceId, tham số IdempotencyKey bắt buộc
  platform-starter-messaging  outbox + relay, inbox, consumer idempotent, retry + DLT, migration outbox/inbox/shedlock
contracts/                    OpenAPI + JSON Schema event (nguồn sự thật của hợp đồng)
buildSrc/                     cấu hình Gradle dùng chung; phiên bản thư viện lấy từ BOM của Spring Boot ở đây
deploy/local/                 script khởi tạo database cho docker compose
docker-compose.yml            Postgres, Kafka (KRaft), Schema Registry, Redis
```

## Yêu cầu

- **JDK 21** (bắt buộc; JDK 8/11/17 không build được), Docker.
- Gradle: dùng `./gradlew`.

## Lệnh

Chạy trong thư mục `source-code/`:

```
./gradlew build                                       # build + toàn bộ test
./gradlew :platform:platform-starter-messaging:test   # test tích hợp: Postgres + Kafka thật qua Testcontainers
docker compose up -d --wait                           # hạ tầng local (đổi port bằng POSTGRES_PORT, KAFKA_PORT, SCHEMA_REGISTRY_PORT, REDIS_PORT)
docker compose down -v                                # dừng và xoá dữ liệu
```

Lint hợp đồng OpenAPI (cần Docker):

```
docker run --rm -v "${PWD}/contracts:/spec" -w /spec redocly/cli:latest lint openapi/payment/public.yaml
```

### Build không cần cài JDK 21

Dùng container Gradle. Lệnh dưới là cách đã dùng để kiểm tra nền tảng này:

```
docker run --rm -v "${PWD}:/p" -v ewallet-gradle-cache:/home/gradle/.gradle \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /p gradle:8.14-jdk21 gradle build --no-daemon
```

## Lưu ý

- `gradle-wrapper.properties` trỏ `services.gradle.org`, nơi chuyển hướng sang GitHub. Mạng nào chặn hoặc chèn chứng chỉ TLS cho GitHub thì wrapper không tải được Gradle: cài Gradle 8.14 và chạy `gradle` trực tiếp, hoặc dùng container ở trên. **Wrapper chưa được chạy thử**.
- Docker Engine 29 từ chối API version cũ nên cần Testcontainers >= 1.21.4; `buildSrc` đã ghi đè phiên bản (bỏ khi Spring Boot quản lý bản mới hơn).
- Test messaging in cảnh báo "Connection refused" ở cuối: container dừng trước khi relay dừng. Vô hại.

## Cách service dùng các starter

1. Thêm `implementation(project(":platform:platform-starter-web"))` và `...-messaging` vào build của service.
2. Cấu hình Flyway lấy cả migration của service lẫn của platform: `spring.flyway.locations=classpath:db/migration,classpath:db/platform` (bảng outbox/inbox/shedlock; version `V0_1` nên chạy trước migration của service).
3. Phát event: `outboxService.publish(topic, aggregateId, type, aggregateVersion, data)` **trong** transaction nghiệp vụ.
4. Nhận event: `processor.process(value, "<tên-handler>", event -> ...)` trong `@KafkaListener`. Handler lỗi thì thử lại rồi vào `<topic>.dlt`; event sai định dạng vào thẳng DLT.
5. Controller lệnh tiền khai báo tham số `IdempotencyKey`; tra khoá trong DB là bước đầu của use case.
6. Provider giả đánh dấu `@FakeProvider`.

## Chưa làm trong M1 (theo design §12.4)

`platform-bom` riêng (hiện dùng BOM Spring Boot trong `buildSrc`), security resource server (cần JWKS của `identity`), observability starter (OpenTelemetry, log JSON, `traceparent` vào event), sinh client từ OpenAPI, service `identity`, `api-gateway`, `frontend/`, Helm, CI, mesh. Serializer Schema Registry cho event: hiện gửi JSON thường, Schema Registry mới chỉ có trong compose.
