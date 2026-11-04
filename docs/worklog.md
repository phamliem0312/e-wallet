# Nhật ký công việc

Ghi theo thứ tự thời gian, mục mới nhất ở trên cùng. Mỗi phần việc xong thì thêm một mục: làm gì, kiểm chứng ra sao (kết quả thật), quyết định đáng nhớ, việc còn dở. Không lặp lại nội dung tài liệu; chỉ trỏ tới nó.

---

## 2026-11-04 — M1: nền tảng `platform` + hạ tầng local

**Làm:**
- Khung Gradle (Java 21, Spring Boot 3.5.7), `buildSrc` giữ phiên bản thư viện.
- `platform-starter-core`: `Money`, UUIDv7, `Sensitive`, `ErrorCode`/`PlatformException`, chặn `@FakeProvider` ở profile `prod`.
- `platform-starter-web`: Problem (RFC 9457) có `code`/`traceId`; tham số `IdempotencyKey` bắt buộc.
- `platform-starter-messaging`: outbox + relay, inbox, consumer idempotent, retry + DLT, dọn bảng, migration `outbox`/`inbox`/`shedlock`.
- `docker-compose.yml` (Postgres, Kafka KRaft, Schema Registry, Redis) và script tạo 14 database.
- `common.yaml`: thêm mã `INTERNAL_ERROR`. `.gitattributes`. `source-code/README.md`. Mục "Lệnh" trong CLAUDE.md.

**Kiểm chứng:**
- `gradle clean build` trong container `gradle:8.14-jdk21`: qua, 20/20 test (core 6, web 7, messaging 7 với Postgres + Kafka thật qua Testcontainers).
- Phá thử phần chặn trùng của inbox: đúng test `redeliveredEventIsHandledOnce` fail, rồi hoàn tác.
- Compose: 4 service healthy; Kafka gửi/nhận được 1 message; Redis `PONG`; Schema Registry trả danh sách rỗng.
- Lint Redocly 3 file OpenAPI sau khi sửa `common.yaml`: hợp lệ.

**Quyết định / phát hiện:**
- Lỗi tìm ra khi kiểm tra: `payment_svc` vào được `ledger_db` (PUBLIC có CONNECT mặc định). Đã thêm `revoke connect ... from public`, kiểm lại bị từ chối.
- Nâng Testcontainers lên 1.21.4 (override trong `buildSrc`) vì Docker Engine 29 từ chối API version của 1.21.3.
- Không viết service `identity`: chưa có SRS cho đăng ký/đăng nhập, mà CLAUDE.md cấm code tính năng chưa có SRS được duyệt. Luồng outbox → Kafka → consumer được chứng minh bằng test tích hợp trong starter.

**Chưa kiểm / còn dở:**
- `./gradlew` chưa chạy thử (mạng chèn chứng chỉ TLS khi tải Gradle từ GitHub); mọi build dùng container.
- Chưa có: security resource server, observability starter (chưa có `traceparent` trong event), serializer Schema Registry, `api-gateway`, frontend, Helm, CI, mesh.
- OQ-027 vẫn mở, chặn phần hạ tầng/CI của M1.

---

## 2026-11-04 — README.md

Thêm `README.md` ở gốc repo (commit `78003a2`). Chưa chạy thử lệnh lint trong README bằng cú pháp PowerShell `${PWD}`.

---

## 2026-11-04 — Bộ tài liệu thiết kế và skill (commit `38318fb`)

**Làm:** requirements v0.2 (110 yêu cầu), ADR-0001 (14 microservice), design/data model/API, SRS index (24 tính năng) + đặc tả F-PAY-03, UI F-PAY-03 + design system, 55 test case + ma trận truy vết, `open-questions.md` (28 câu mở), hợp đồng OpenAPI/event cho phần P2P, 8 skill trong `.claude/skills/`, CLAUDE.md.

**Kiểm chứng:** DDL data model chạy được trên Postgres 16; hợp đồng OpenAPI qua Redocly lint; schema event qua Ajv strict 2020-12 (kèm thử từ chối example thiếu trường).

**Phát hiện đã sửa:** partition làm hỏng unique idempotency của `payment_order`; thiếu ràng buộc trong data model; API doc mâu thuẫn 422 với 200+`failureCode`; 3 lỗi trong skill SRS.

**Còn dở:** 28 câu hỏi mở, 6 câu P1 (OQ-002, 003, 004, 006, 024, 027); SRS F-PAY-03 v0.2 chưa được duyệt.
