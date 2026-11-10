# Nhật ký công việc

Ghi theo thứ tự thời gian, mục mới nhất ở trên cùng. Mỗi phần việc xong thì thêm một mục: làm gì, kiểm chứng ra sao (kết quả thật), quyết định đáng nhớ, việc còn dở. Không lặp lại nội dung tài liệu; chỉ trỏ tới nó.

---

## 2026-11-10 — Quyết định phạm vi; test case F-ACC-01

**Quyết định của chủ dự án (qua hộp hỏi):** làm toàn bộ dự án theo mốc M1 → M7; SRS Draft coi là đã duyệt để code (chỗ chưa quyết thành tham số cấu hình, giữ trong open-questions); tự commit và push sau mỗi phần đã kiểm chứng. Đã ghi vào CLAUDE.md.

**Làm:**
- `docs/test-cases/F-ACC-01-dang-ky-dang-nhap.md`: 64 TC (UNIT 6, INTEGRATION 3, API 45, E2E 5, NFR 5); thêm mục F-ACC-01 vào `traceability.md`.
- SRS F-ACC-01 lên v0.4: sửa AC-07 (tiền đề "hai challenge hợp lệ cùng SĐT" mâu thuẫn FR-12 và `uq_challenge_active`).
- `docs/open-questions.md`: OQ-052 (`action`/`target` của audit), 51 câu mở.

**Kiểm chứng (script):** 64 TC; cả 43 FR, 10 BR, 15 ST, 9 EV, 7 NFR và 53 AC đều được ít nhất một TC trích.

**Còn dở:** TC-ACC-011 bị chặn (OQ-024); TC-ACC-004, 065, 067 chặn một phần (quy tắc che tên, luồng khuôn mặt). Ca E2E chưa có chi tiết màn hình vì chưa có đặc tả UI. Chưa có code test nào.

---

## 2026-11-04 — Cập nhật tiến độ và commit

- Thêm bảng "Tiến độ" vào `README.md`, cập nhật dòng trạng thái ở `README.md` và `CLAUDE.md` (trước đó vẫn ghi "chưa có code ứng dụng").
- Commit và push gộp ba phần: SRS F-ACC-01 v0.3 (sửa), data model `identity`, hợp đồng API/event F-ACC-01.

---

## 2026-11-04 — Hợp đồng API & event cho F-ACC-01

**Làm:**
- `contracts/openapi/identity/public.yaml` v0.2: 13 endpoint mới (đăng ký, đăng nhập, xác minh đăng nhập thiết bị mới, làm mới phiên, đăng xuất, quên/đặt lại mật khẩu, đổi mật khẩu, đổi SĐT, thiết bị, đồng ý); `createChallenge` mở rộng (`factor` tuỳ chọn, thêm `CHANGE_PHONE`).
- `contracts/openapi/identity/internal.yaml` (xác minh challenge giao dịch), `contracts/openapi/notification/internal.yaml` (gửi OTP), `contracts/events/identity/CustomerRegistered/v1.json`, `CustomerUpdated/v1.json`.
- `common.yaml`: 6 mã lỗi mới (`INVALID_CREDENTIALS`, `ACCOUNT_TEMP_LOCKED`, `ACCOUNT_LOCKED`, `PHONE_ALREADY_REGISTERED`, `CONSENT_REQUIRED`, `OTP_SEND_FAILED`), `Problem503`, mô tả `params` mới.
- `docs/design/2026-11-04-ewallet-api.md`: bảng Auth & identity viết lại, bảng mã lỗi, hai dòng API nội bộ, danh mục event. SRS F-ACC-01 lên v0.3 (mục 4.2 trỏ tới hợp đồng). `docs/open-questions.md`: OQ-047..051 (50 câu mở; P1 vẫn 6).

**Đối chiếu (chỉ dựa trên SRS và các service tiêu thụ, vì chưa có đặc tả UI cho F-ACC-01):**
- Nhu cầu dữ liệu, 17 dòng: ✅ 3, ➕ 10, ❓ 4.
- Mã lỗi (SRS §6), 15 mã: ✅ 8, ➕ 6 mã mới, ➖ 1 (`FORBIDDEN` do CSRF là của BFF, không có endpoint `identity` nào trả).
- Event, 9 dòng: ➕ 5, ❓ 2, ➖ 2 (audit và cảnh báo vận hành không thuộc hợp đồng này).

**Kiểm chứng:**
- Redocly lint: 5 file OpenAPI hợp lệ (payment, wallet, identity public và internal, notification internal). Lần đầu 2 file nội bộ thiếu `security`, đã sửa. Hai lỗi cố ý (example sai kiểu, bỏ response 4xx) đều bị lint bắt.
- Ajv strict 2020-12: 4 schema event compile được, example hợp lệ, example thiếu trường bắt buộc bị từ chối; thêm 5 ca vi phạm của `CustomerRegistered` (SĐT chưa chuẩn hoá, 8 chữ số, `displayMasked` rỗng hoặc dài 65, `customerId` không phải UUID) đều bị từ chối, field lạ được chấp nhận.
- oasdiff so với bản trước của `identity/public.yaml`: 19 "breaking", gồm 18 dòng cùng một thay đổi (6 mã `ErrorCode` mới × 3 response của `/challenges`) và 1 dòng `createChallenge` đổi thành `oneOf`.

**Thay đổi phá tương thích:** không phá ở mức dữ liệu truyền. 18 dòng là thêm giá trị vào `ErrorCode`, đã khai báo "có thể mở rộng", quy ước cho phép. `oneOf` giữ request cũ `{purpose: P2P, payload}` hợp lệ, nhưng kiểu của client sinh tự động sẽ khác; chưa có client nào sinh từ v0.1 nên chấp nhận.

**Quyết định / phát hiện:**
- `verifyChallenge` nhận `payload` thay vì `payloadHash` (api doc cũ): để `payment` không phải biết cách chuẩn hoá để băm giống `identity`; trả thêm `factor`.
- Lỗ hổng ở luồng tiền: không có gì chặn client nộp challenge OTP khi lệnh đòi `FACE` (OQ-049). Hợp đồng đã cho `payment` đủ dữ liệu để chặn, nhưng F-PAY-03 chưa nói.
- `identity` phải tính `displayMasked` (tên đầy đủ không được lên Kafka) nên phụ thuộc quy tắc che tên chưa chốt (OQ-047, OQ-009).
- `createChallenge` cho `CHANGE_PHONE` gửi OTP tới SĐT mới (FR-35), khác `P2P` luôn gửi tới SĐT đăng ký của khách (FR-41).
- Thêm `notification/internal.yaml` dù không có trong yêu cầu ban đầu, vì `identity` không code được nếu thiếu hợp đồng gửi OTP.

**Chưa kiểm / còn dở:**
- Luồng khuôn mặt (nộp ảnh, xác thực) chưa có hợp đồng: thuộc F-ACC-02, OQ-028. `/me/phone` nhận `faceChallengeId` đã xác thực đạt mà chưa có cách để challenge đó đạt.
- Chưa có hợp đồng: `GET /me` (OQ-048), `POST /me/consents` (OQ-041), thông báo bảo mật không phải OTP (OQ-051), nội dung điều khoản (OQ-050).
- Chưa sinh client, chưa có contract test. Chưa có đặc tả UI của F-ACC-01.

---

## 2026-11-04 — Data model `identity` theo SRS F-ACC-01

**Làm:**
- Viết lại mục `identity` trong `docs/design/2026-11-04-ewallet-data-model.md` (dòng 440–821): ERD, 10 truy vấn chính, khối lượng, ghi chú thiết kế từng bảng, ẩn danh dữ liệu cá nhân.
- Thay đổi chính: `customer` thêm `version`/`updated_at` (event cần `aggregateversion`) và kiểm chuẩn hoá SĐT, họ tên; `device.trusted` → `trusted_at`; `refresh_token` thêm hạn tuyệt đối của family; `otp_challenge` thêm `superseded_at`, 13 `CHECK` chặn tổ hợp vô nghĩa và `uq_challenge_active` (tối đa một challenge còn sống cho cùng đối tượng và nội dung lệnh, bảo vệ FR-12 ở DB); `consent` chỉ thêm (`REVOKE UPDATE, DELETE`).
- Cập nhật SRS F-ACC-01 lên v0.2 (ghi chú ST-10, mở rộng OQ-039). `docs/open-questions.md`: thêm OQ-045, OQ-046 (45 câu mở; P1 vẫn 6).

**Kiểm chứng:**
- DDL tách từ chính tài liệu, chạy trên PostgreSQL 16 với `identity_svc` làm chủ bảng: tạo được 6 bảng, 4 index.
- 75 ca đạt, 0 lỗi: 48 ca vi phạm bị chặn đúng loại lỗi và đúng tên ràng buộc/cột, 11 ca hợp lệ, 16 kiểm tra hành vi của các câu truy vấn nguyên tử (xoay vòng token, thu hồi family, lượt nhập OTP, claim `consumerRef`, bộ đếm khoá).
- Lần chạy đầu 2 ca fail do lỗi bộ kiểm tra (Postgres trả chuỗi rỗng thay vì NULL cho tên ràng buộc ở lỗi `NOT NULL`), đã sửa bộ kiểm tra; DDL không đổi.
- Bỏ thử `uq_challenge_active`: đúng 3 ca fail. Role ẩn danh `consent` (SELECT + UPDATE (ip, user_agent)) sửa được hai cột đó, bị từ chối khi sửa `granted` hoặc xoá.

**Quyết định / phát hiện:**
- Đếm số lần gửi OTP và `resendAfter` để ở Redis, không ở DB: nếu đếm theo hàng `otp_challenge` thì SĐT chưa đăng ký không có hàng, hành vi giới hạn tần suất khác nhau và dò được tài khoản (mâu thuẫn FR-02, FR-32). Đổi lại, Redis khởi động lại thì mất bộ đếm (OQ-046).
- `code_hash` là HMAC có khoá và có `id` trong dữ liệu băm, vì mã chỉ có 10^6 giá trị; băm không khoá bị dò ngược ngay nếu lộ DB. Cụ thể hoá thêm so với design §10 ("hash").
- Bỏ cột `email` (không FR nào dùng), `trusted_at` thay `trusted`: không phá tương thích vì chưa có dữ liệu thật.

**Chưa kiểm / còn dở:**
- Cách ẩn danh `customer.phone` chưa quyết (đụng `ck_customer_phone` và OQ-039).
- Bảng đếm gửi OTP bền (OQ-046) và gắn challenge `CHANGE_PHONE` với SĐT mới (OQ-045) chưa quyết.
- Chưa có Flyway migration; chỉ là thiết kế.

---

## 2026-11-04 — SRS F-ACC-01 Đăng ký & đăng nhập (Draft v0.1)

**Làm:**
- `docs/srs/features/F-ACC-01-dang-ky-dang-nhap.md`: 8 use case, 43 FR, 10 BR, 15 ST (tài khoản, challenge, refresh token), 9 EV, 7 NFR, 53 AC, 15 câu hỏi mở.
- Phạm vi gồm cả cơ chế challenge OTP dùng chung (FR-09..14, FR-41, FR-42), vì F-PAY-03 đã giao phần này cho F-ACC-01.
- `docs/srs/index.md`: trỏ tới spec, sửa cột "liên quan" của F-ACC-01 (bỏ `FR-LIM`, thêm `FR-ID-04`, `NFR-S-01..04`, `NFR-L-03`), ghi lịch sử.
- `docs/open-questions.md`: thêm OQ-030..044 (28 → 43 câu mở; P1 vẫn 6).

**Kiểm chứng (bằng script, chưa có người duyệt):**
- Cả 77 ID (43 FR + 10 BR + 15 ST + 9 EV) đều có ít nhất một AC; không AC nào trỏ tới ID không tồn tại; AC đánh số liên tục 1..53.
- Lần chạy đầu bắt được hai chỗ hở và đã sửa: ST-03 chưa có AC, ma trận ghi ST-06 được phủ nhưng AC-01 và AC-23 chưa trỏ tới nó.
- ID OQ-030..044 khớp 15 câu hỏi trong spec (Q1..Q15), không trùng.

**Quyết định / phát hiện:**
- Phụ thuộc vòng: đổi SĐT (FR-ID-06) cần xác thực khuôn mặt của F-ACC-02, trong khi F-ACC-02 cần F-ACC-01. Cách xử lý: chỉ UC-06 phụ thuộc F-ACC-02 khi chạy.
- Lỗ hổng nghiệp vụ tìm ra khi viết: khách chưa eKYC không có ảnh mặt để so nên không đổi được SĐT (OQ-033); `customer.phone` duy nhất kể cả tài khoản đã đóng nên SĐT không đăng ký lại được (OQ-039); nhiều tab làm mới cùng refresh token có thể làm mất phiên (OQ-037); ví mở bất đồng bộ nên ngay sau đăng ký có thể chưa có ví (OQ-042).
- Mặc định bảo thủ đã chọn và gắn nhãn `[Giả định]`/`[Cần xác nhận]`: chống dò SĐT đã đăng ký, không gửi OTP khi sai mật khẩu, kiểm trạng thái tài khoản sau mật khẩu, tài khoản `CLOSED` báo `INVALID_CREDENTIALS`.
- Mọi giá trị tham số `auth.*` (trừ `auth.access.ttl` = 5 phút từ design) chưa chốt; số trong AC là minh hoạ.

**Chưa kiểm / còn dở:**
- Spec chưa được duyệt; 15 câu hỏi mở, trong đó 7 câu P2 chặn từng FR (OQ-030, 031, 032, 033, 037, 039, 042).
- API doc và contract chưa khớp spec: thiếu 4 endpoint (quên/đặt lại mật khẩu, đổi mật khẩu, đổi SĐT), `register/verify` thiếu `termsVersion` và `deviceFingerprint`, 6 mã lỗi mới chưa có trong `common.yaml`, `purpose` của `/challenges` mới có `P2P`. Việc cho skill `api-contract`.
- Data model chưa có trạng thái `SUPERSEDED` cho challenge (ST-10); việc cho `data-model-design`.
- Chưa có UI spec, test case, hay hợp đồng cho F-ACC-01.
- AC-08 (ép lỗi ghi outbox) cần cơ chế ép lỗi, vẫn bị OQ-024 chặn.

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
