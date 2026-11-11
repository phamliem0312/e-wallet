# source-code

Monorepo backend (Java 21, Spring Boot 3.5, Gradle Kotlin DSL) và hợp đồng API. Trạng thái: **nền tảng M1 đang dựng**; đã có `identity` (F-ACC-01) và `api-gateway`. Chưa có frontend.

```
platform/
  platform-starter-core       Money, UUIDv7, Sensitive, ErrorCode/PlatformException, chặn @FakeProvider ở profile prod
  platform-starter-web        Problem (RFC 9457) + code + traceId, tham số IdempotencyKey bắt buộc, kiểm JWT (resource server)
  platform-starter-messaging  outbox + relay, inbox, consumer idempotent, retry + DLT, migration outbox/inbox/shedlock
  platform-starter-observability  actuator + Prometheus + tracing W3C (chỉ gom dependency)
services/
  identity                    đăng ký, đăng nhập, phiên, mật khẩu, thiết bị, challenge OTP (F-ACC-01); cổng 8101
gateways/
  api-gateway                 định tuyến, kiểm JWT (JWKS của identity), rate limit theo IP (Redis), giới hạn kích thước; cổng 8080
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

## Chạy `identity` ở local

```
docker compose up -d --wait postgres kafka redis
./gradlew :services:identity:bootRun        # cần JDK 21; không có thì chạy bằng container Gradle như mục trên
```

- OTP và thông báo không gửi đi đâu: provider giả giữ trong bộ nhớ. Xem tại `GET http://localhost:8101/internal/dev/messages?recipient=%2B84900000001` (không có ở profile `prod`).
- Không cấu hình `AUTH_JWT_PRIVATE_JWK` và `AUTH_HMAC_KEY` thì service sinh khoá tạm: token và thiết bị tin cậy mất hiệu lực mỗi lần khởi động lại. Profile `prod` bắt buộc có hai khoá này.
- Mọi tham số `auth.*` trong `application.yml` (hạn OTP, số lần khoá, hạn phiên, giới hạn tần suất...) là số minh hoạ của SRS, chưa chốt (OQ-035, OQ-036).
- `/internal/**` không cần JWT ở tầng ứng dụng (mesh mTLS sẽ chặn); ở local ai tới được cổng 8101 cũng gọi được.
- Chưa có adapter thật tới `notification` (mốc M5), chưa có luồng khuôn mặt (F-ACC-02) nên đổi SĐT chưa đi hết được.

## Cách service dùng các starter

1. Thêm `implementation(project(":platform:platform-starter-web"))` và `...-messaging` vào build của service.
2. Cấu hình Flyway lấy cả migration của service lẫn của platform: `spring.flyway.locations=classpath:db/migration,classpath:db/platform` (bảng outbox/inbox/shedlock; version `V0_1` nên chạy trước migration của service).
3. Phát event: `outboxService.publish(topic, aggregateId, type, aggregateVersion, data)` **trong** transaction nghiệp vụ.
4. Nhận event: `processor.process(value, "<tên-handler>", event -> ...)` trong `@KafkaListener`. Handler lỗi thì thử lại rồi vào `<topic>.dlt`; event sai định dạng vào thẳng DLT.
5. Controller lệnh tiền khai báo tham số `IdempotencyKey`; tra khoá trong DB là bước đầu của use case.
6. Provider giả đánh dấu `@FakeProvider`.
7. Thêm `spring-boot-starter-oauth2-resource-server` thì service tự kiểm JWT của khách; đường dẫn công khai khai báo ở `ewallet.security.public-paths`. Service khác `identity` đặt `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` trỏ tới JWKS của `identity` và `jws-algorithms: ES256`.

## Chưa làm trong M1 (theo design §12.4)

`platform-bom` riêng (hiện dùng BOM Spring Boot trong `buildSrc`), phần còn lại của observability (log JSON, `traceparent` vào event, exporter OTLP), sinh client từ OpenAPI, `frontend/`, Helm, CI, mesh. Serializer Schema Registry cho event: hiện gửi JSON thường, Schema Registry mới chỉ có trong compose.
