# Scaffold

Hai tình huống: **dựng nền tảng M1** (lần đầu) và **thêm một service** (sau M1). Nguyên tắc chung: sinh khung **chạy được** + **một lát cắt end-to-end**. Không sinh hàng loạt service rỗng: code rỗng là nợ, người đọc phải đoán nó để làm gì.

## Trước khi sinh
1. Kiểm `source-code/` đã có gì; không ghi đè file có sẵn mà không hỏi.
2. Đọc design doc §12 (cấu trúc monorepo, thứ tự mốc) và ADR hiện hành.
3. Có mạng/CLI thì dùng công cụ chính thức để lấy phiên bản đúng (Spring Initializr cho service, `create-next-app` cho frontend). Không chạy được thì viết tay và ghi rõ phiên bản cần kiểm lại.

## A. Nền tảng M1

Sinh theo thứ tự này. Mỗi bước phải build được trước khi sang bước sau.

1. **Gradle multi-project:** `settings.gradle.kts` include `platform:*`, `services:*`, `gateways:*`; version catalog.
2. **`platform/`:**
   - `platform-bom`: quản lý phiên bản.
   - `platform-starter-core`: `Money`, `ProblemDetail` + mã lỗi, masking log, chặn `FakeProvider` ở prod.
   - `platform-starter-web`: security resource server, chuyển tiếp `traceparent`.
   - `platform-starter-messaging`: outbox + relay, inbox, consumer có retry/DLT.
   - `platform-starter-observability`.
   - Mỗi starter có test riêng.
3. **`contracts/`:** cấu trúc thư mục OpenAPI và JSON Schema; task Gradle sinh client; bước CI kiểm tương thích schema.
4. **Service đầu tiên `identity`:** chỉ đăng ký + đăng nhập + JWKS, dùng đủ các starter; một event `CustomerRegistered` qua outbox; Flyway migration đầu tiên.
5. **`api-gateway`:** route `/api/v1/auth/**` tới `identity`, kiểm JWT, rate limit.
6. **`frontend/apps/web`:** trang đăng nhập gọi qua gateway; cookie httpOnly.
7. **`docker-compose.yml` cho local:** Postgres, Kafka + Schema Registry, Redis, MinIO, Keycloak; script tạo DB cho từng service.
8. **`deploy/`:** Helm chart chung + values cho `identity`, `api-gateway`.
9. **CI:** build, test, Testcontainers, kiểm schema, quét bảo mật, build image, theo từng service (lọc theo đường dẫn).

Lát cắt end-to-end của M1: **đăng ký → đăng nhập trên web → `CustomerRegistered` được consumer test nhận**.

## B. Thêm một service

1. Xác nhận service có trong design §5 hoặc đã có ADR. Chưa có thì dừng lại, quay về chế độ Design doc/ADR.
2. Tạo `services/<tên>/` theo cấu trúc trong `backend-spring.md`, dùng các starter của `platform`; không thêm dependency trùng chức năng với starter.
3. OpenAPI của `/internal/**` và JSON Schema của event (nếu có) thêm vào `contracts/` **trước** khi code.
4. Flyway migration đầu tiên (bảng theo `data-model-design`), kèm `outbox`, `inbox`, `shedlock`.
5. Helm values, route gateway (nếu có API public), AuthorizationPolicy cho `/internal/**`.
6. Một use case end-to-end có test: unit, integration (Testcontainers), contract.

## Sau khi sinh
- Cập nhật `source-code/README.md`: yêu cầu (JDK, Node, Docker), lệnh chạy local, lệnh test.
- **Chạy build và test nếu môi trường cho phép; báo kết quả thật** (pass/fail kèm lỗi). Không chạy được thì nói rõ là chưa kiểm.
- Liệt kê bước tiếp theo theo mốc ở design §12.4, thay vì sinh trước.
