# Test case — F-ACC-01 Đăng ký & đăng nhập

- Nguồn: [SRS F-ACC-01](../srs/features/F-ACC-01-dang-ky-dang-nhap.md) v0.4 (FR, BR, ST, EV, NFR, AC) · hợp đồng [identity/public.yaml](../../source-code/contracts/openapi/identity/public.yaml), [identity/internal.yaml](../../source-code/contracts/openapi/identity/internal.yaml) · [data model §identity](../design/2026-11-04-ewallet-data-model.md)
- Ngày: 2026-11-10
- Phạm vi: F-ACC-01.FR-01..43, BR-01..10, ST-01..15, EV-01..09, NFR-01..07, AC-01..53. Chưa có đặc tả UI nên ca E2E chỉ nêu luồng, chưa có câu chữ màn hình.

## Tóm tắt
| Mức | Số TC |
|---|---|
| UNIT | 6 |
| INTEGRATION | 3 (1 BLOCKED) |
| API | 45 (2 BLOCKED một phần: TC-ACC-065, 067) |
| E2E | 5 |
| NFR | 5 |
| **Cộng** | **64** |

## Trạng thái tự động hoá (2026-11-10)

- **Đã có test tự động, đang pass** (`source-code/services/identity/src/test/`): UNIT TC-ACC-001..006 (`DomainRulesTest`); INTEGRATION TC-ACC-010, 012 và API TC-ACC-020..026, 030..036, 040..055, 060..063, 065..068, 070..072, 075, 076, 080 (`IdentityApiTest`, tên test chứa ID).
- **Mới phủ một phần:** TC-ACC-032 (thiếu dòng c), TC-ACC-055 (chỉ nhánh `LOCKED`), TC-ACC-081 (mới kiểm audit của đăng ký và dùng lại refresh token), TC-ACC-065/067 (khuôn mặt dựng bằng ghi DB).
- **Chưa có:** TC-ACC-011 (BLOCKED), E2E TC-ACC-085..089 (chưa có web), NFR TC-ACC-090..094.

## Dữ liệu & môi trường test

**Cấu hình mặc định** (giá trị minh hoạ của SRS §7, chưa chốt trừ `auth.access.ttl`; xem OQ-036):

| Tham số | Giá trị test |
|---|---|
| `auth.otp.ttl` / `max_attempts` / `resend_after` | 3 phút / 5 / 30 giây |
| `auth.otp.max_sends` / `send_window` | 5 / 1 giờ |
| `auth.login.max_failures` / `lock_duration` | 5 / 15 phút |
| `auth.access.ttl` | 5 phút (đã chốt) |
| `auth.refresh.ttl` / `auth.session.idle_timeout` / `reuse_grace` | 30 ngày / 30 phút / 0 |
| `auth.password.min_length` / `max_length` | 8 / 128 |
| `auth.terms.current_version` | `2026-11` |
| `auth.ratelimit.login.ip` | 20 lần / phút |

**Môi trường:**
- Mỗi TC dùng SĐT riêng (`+849000xxxxx`) và IP riêng (header `X-Forwarded-For`) để chạy song song và không dính giới hạn tần suất của nhau.
- Đồng hồ điều khiển được (inject `Clock`) cho các ca biên thời gian.
- OTP và thông báo đọc từ provider giả trong bộ nhớ (`GET /internal/dev/messages?recipient=`), chỉ có ở profile không phải `prod`.
- Helper: `register(phone)` (đăng ký hoàn chỉnh, trả phiên), `login(phone, password, device)`.
- Thao tác của tính năng khác (khoá/mở khoá tài khoản của F-BO-02, đóng tài khoản của F-ACC-03, xác thực khuôn mặt của F-ACC-02) được dựng bằng cách ghi thẳng DB trong test, cho tới khi tính năng đó có.

## Test case

### UNIT

### TC-ACC-001 — Chuẩn hoá và kiểm định dạng SĐT
- **Yêu cầu:** BR-01 · **AC:** AC-02 · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** EP, BVA

| # | Đầu vào | Mong đợi |
|---|---|---|
| a | `0900000001` | `+84900000001` |
| b | `+84900000001` | `+84900000001` |
| c | `090000001` (9 chữ số) | không hợp lệ |
| d | `09000000011` (11 chữ số) | không hợp lệ |
| e | `+8490000000a` | không hợp lệ |
| f | chuỗi rỗng, `null` | không hợp lệ |
| g | `84900000001` (thiếu `+`) | không hợp lệ |
| h | `0900 000 001` (có khoảng trắng) | không hợp lệ (OQ-016 chưa cho phép) |

- **Tự động hoá:** `identity` › `PhoneNumbersTest`

### TC-ACC-002 — Chính sách mật khẩu
- **Yêu cầu:** BR-02 · **AC:** AC-39 · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Tiền điều kiện:** `min_length` = 8, `max_length` = 128; SĐT khách `+84900000001`

| # | Mật khẩu | Mong đợi |
|---|---|---|
| a | 7 ký tự | từ chối |
| b | 8 ký tự | chấp nhận |
| c | 128 ký tự | chấp nhận |
| d | 129 ký tự | từ chối |
| e | `0900000001` | từ chối (bằng SĐT dạng `0`) |
| f | `+84900000001` | từ chối (bằng SĐT dạng `+84`) |
| g | 8 ký tự tiếng Việt có dấu (`mậtkhẩuă`) | chấp nhận (đếm theo ký tự Unicode) |
| h | 8 khoảng trắng | chấp nhận (không áp quy tắc thành phần, không trim) |

- **Tự động hoá:** `identity` › `PasswordPolicyTest`

### TC-ACC-003 — Chuẩn hoá họ tên
- **Yêu cầu:** BR-03 · **AC:** AC-40 · **Mức:** UNIT · **Ưu tiên:** P2 · **Kỹ thuật:** EP, BVA

| # | Đầu vào | Mong đợi |
|---|---|---|
| a | `  Nguyễn   Văn A ` | `Nguyễn Văn A` |
| b | rỗng, toàn khoảng trắng | không hợp lệ |
| c | 128 ký tự | chấp nhận |
| d | 129 ký tự | không hợp lệ |
| e | `A\nB`, `A\tB`, `A\u0007B` | không hợp lệ (ký tự điều khiển) |

- **Tự động hoá:** `identity` › `FullNamesTest`

### TC-ACC-004 — Che nơi nhận OTP và tên hiển thị
- **Yêu cầu:** FR-01 (`destinationMasked`), EV-01 (`displayMasked`) · **Mức:** UNIT · **Ưu tiên:** P2 · **Kỹ thuật:** EP
- **Trạng thái:** BLOCKED một phần (OQ-009, OQ-047: quy tắc che tên chưa chốt; dòng c–e là giả định từ ví dụ duy nhất của data model)

| # | Đầu vào | Mong đợi |
|---|---|---|
| a | SĐT `+84900000001` | `0900 *** 001` (theo ví dụ của hợp đồng) |
| b | Kết quả a | không chứa 3 chữ số giữa `000` liền sau `0900` ở dạng rõ ngoài phần đã nêu |
| c | Tên `Nguyễn Văn A` | `NGUYEN V*** A` (ví dụ của data model) |
| d | Tên hai từ `Trần Bình` | không chứa `BINH` đầy đủ |
| e | Tên một từ `Bình` | không chứa `BINH` đầy đủ |

- **Tự động hoá:** `identity` › `MaskingTest`

### TC-ACC-005 — Xác định thiết bị mới
- **Yêu cầu:** BR-04 · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** bảng quyết định
- **Tiền điều kiện:** `auth.device.trust_ttl` = 90 ngày ở dòng d, e; không đặt ở dòng a–c

| # | Trạng thái thiết bị của tài khoản | Mong đợi |
|---|---|---|
| a | Chưa có dòng cho dấu vân tay này | mới |
| b | Có dòng, `trusted_at` = null (đã thu hồi) | mới |
| c | Có dòng, `trusted_at` 400 ngày trước, không đặt TTL | đã biết |
| d | Có dòng, `trusted_at` 89 ngày trước | đã biết |
| e | Có dòng, `trusted_at` 91 ngày trước | mới |

- **Tự động hoá:** `identity` › `DeviceTrustTest`

### TC-ACC-006 — Băm OTP gắn với challenge
- **Yêu cầu:** FR-09; data model (`code_hash` = HMAC có khoá, có `id`) · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** EP
- **Bước / mong đợi:** cùng mã `123456` với hai `challengeId` khác nhau cho hai giá trị băm khác nhau; cùng mã và cùng id cho cùng giá trị; độ dài băm 32 byte; mã sinh ra luôn đúng 6 chữ số (kể cả khi bắt đầu bằng `0`), kiểm trên 10.000 lần sinh.
- **Tự động hoá:** `identity` › `OtpCodesTest`

### INTEGRATION

### TC-ACC-010 — Hai yêu cầu hoàn tất đăng ký đồng thời chỉ tạo một tài khoản
- **Yêu cầu:** FR-06, BR-10 · **AC:** AC-07 · **Mức:** INTEGRATION · **Ưu tiên:** P1 · **Kỹ thuật:** đồng thời
- **Tiền điều kiện:** challenge C1 hợp lệ; hai luồng cùng gửi hoàn tất với C1 và OTP đúng.
- **Kết quả mong đợi:** đúng một `201`; luồng còn lại `409 PHONE_ALREADY_REGISTERED` hoặc `422 CHALLENGE_INVALID`; đúng 1 dòng `customer`, 1 dòng outbox `CustomerRegistered`.
- **Tự động hoá:** `identity` › `IdentityApiTest`

### TC-ACC-011 — Đăng ký là thao tác không dở dang
- **Yêu cầu:** FR-05 · **AC:** AC-08 · **Mức:** INTEGRATION · **Ưu tiên:** P1
- **Trạng thái:** BLOCKED (OQ-024: chưa có cơ chế ép lỗi ghi outbox)
- **Kết quả mong đợi:** khi ghi outbox lỗi, không có dòng `customer`, `credential`, `consent`, `device` nào.

### TC-ACC-012 — Hai lần làm mới đồng thời bằng cùng refresh token
- **Yêu cầu:** FR-25, FR-26 · **Mức:** INTEGRATION · **Ưu tiên:** P1 · **Kỹ thuật:** đồng thời
- **Bước:** hai luồng cùng gọi `POST /auth/refresh` với R1.
- **Kết quả mong đợi:** đúng một luồng `200`; luồng còn lại `401 UNAUTHENTICATED`; với `reuse_grace` = 0 cả family bị thu hồi (token mới của luồng thắng cũng không dùng được). Hành vi này là điều OQ-037 đang hỏi.
- **Tự động hoá:** `identity` › `IdentityApiTest`

### API — Đăng ký

### TC-ACC-020 — Bắt đầu đăng ký gửi OTP và trả challenge
- **Yêu cầu:** FR-01, FR-09, ST-06, EV-03 · **AC:** AC-01 · **Mức:** API · **Ưu tiên:** P1
- **Bước:** `POST /auth/register` `{phone: "0900000001"}` lúc T.
- **Kết quả mong đợi:** `201`; có `challengeId`, `factor` = `OTP`, `expiresAt` = T + 3 phút, `resendAfter` = T + 30 giây, `attemptsRemaining` = 5, `destinationMasked` không chứa SĐT đầy đủ. Provider giả nhận đúng 1 OTP 6 chữ số tới `+84900000001`. Chưa có tài khoản.

### TC-ACC-021 — SĐT đã đăng ký nhận phản hồi giống hệt, không có OTP
- **Yêu cầu:** FR-02, EV-04 · **AC:** AC-03 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `201` với cùng tập trường như TC-020; không OTP nào được gửi; 1 thông báo "đã đăng ký" tới SĐT đó, không chứa mã; hoàn tất đăng ký với `challengeId` này trả `422 CHALLENGE_INVALID`.

### TC-ACC-022 — Hoàn tất đăng ký tạo tài khoản, đồng ý, thiết bị, phiên và event
- **Yêu cầu:** FR-03, FR-04, FR-05, FR-07, ST-01, EV-01 · **AC:** AC-04 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `201 AuthSession` (4 trường). DB: 1 `customer` `ACTIVE`, tier 0, `version` = 1; `password_hash` bắt đầu `$argon2id$`; 1 `consent` (`TERMS`, `2026-11`, granted, có IP và user agent); 1 `device` có `trusted_at`. Kafka `identity.events`: đúng 1 `CustomerRegistered` có `customerId`, `phone` E.164, `displayMasked`, `aggregateversion` = 1; không có mật khẩu hay họ tên đầy đủ.

### TC-ACC-023 — Thiếu hoặc sai phiên bản điều khoản
- **Yêu cầu:** FR-04, BR-06 · **AC:** AC-05 · **Mức:** API · **Ưu tiên:** P1

| # | `termsVersion` | Mong đợi |
|---|---|---|
| a | vắng mặt | `422 CONSENT_REQUIRED`, `params.currentTermsVersion` = `2026-11` |
| b | `2026-01` | như a |

Cả hai: không có tài khoản; challenge còn dùng được, `attemptsRemaining` vẫn 5 (gửi lại đúng thì `201`).

### TC-ACC-024 — Mật khẩu sai chính sách không tiêu hao lượt OTP
- **Yêu cầu:** BR-06, BR-02 · **AC:** AC-06 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** OTP đúng + mật khẩu 7 ký tự → `400 VALIDATION_ERROR`, `errors[].field` = `password`; gửi lại cùng OTP với mật khẩu hợp lệ → `201`.

### TC-ACC-025 — Hoàn tất lại với challenge đã dùng
- **Yêu cầu:** FR-08, ST-11 · **AC:** AC-10 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `422 CHALLENGE_INVALID`; vẫn 1 tài khoản; không có phiên mới.

### TC-ACC-026 — Thiết bị đăng ký là thiết bị đã biết
- **Yêu cầu:** FR-07 · **AC:** AC-09 · **Mức:** API · **Ưu tiên:** P2
- **Kết quả mong đợi:** đăng xuất rồi đăng nhập lại cùng `deviceFingerprint` → `200`, không có OTP.

### API — OTP và challenge

### TC-ACC-030 — OTP hết hạn đúng tại thời điểm hết hạn
- **Yêu cầu:** FR-09, ST-08 · **AC:** AC-11 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Kết quả mong đợi:** OTP đúng tại T + 2:59 → chấp nhận; tại T + 3:00 (challenge khác) → `422 CHALLENGE_INVALID`.

### TC-ACC-031 — OTP đã dùng không dùng lại được
- **Yêu cầu:** FR-09, ST-07, ST-11 · **AC:** AC-12 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `POST /auth/login/verify` lần hai với cùng challenge → `422 CHALLENGE_INVALID`.

### TC-ACC-032 — Challenge không dùng được cho mục đích hoặc đối tượng khác
- **Yêu cầu:** FR-10 · **AC:** AC-13 · **Mức:** API · **Ưu tiên:** P1

| # | Challenge | Dùng ở | Mong đợi |
|---|---|---|---|
| a | `REGISTER` của SĐT X | `/auth/login/verify` | `CHALLENGE_INVALID` |
| b | `REGISTER` của SĐT X | `/auth/password/reset` | `CHALLENGE_INVALID` |
| c | `LOGIN_NEW_DEVICE` của khách A | `/auth/register/verify` | `CHALLENGE_INVALID` |
| d | `RESET_PASSWORD` của khách A | `/auth/login/verify` | `CHALLENGE_INVALID` |

### TC-ACC-033 — Hết lượt nhập thì mã đúng cũng bị từ chối
- **Yêu cầu:** FR-11, ST-09 · **AC:** AC-14 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Kết quả mong đợi:** 5 lần sai trả `params.attemptsRemaining` = 4, 3, 2, 1, 0; lần thứ 6 với mã đúng → `422 CHALLENGE_INVALID`.

### TC-ACC-034 — Gửi lại OTP: chặn trước 30 giây, sau đó thay challenge cũ
- **Yêu cầu:** FR-12, ST-10 · **AC:** AC-15 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Kết quả mong đợi:** T + 29 giây → `429 RATE_LIMITED`, `Retry-After` = 1; T + 30 giây → `201` challenge C2; OTP của C1 → `CHALLENGE_INVALID`; OTP của C2 được chấp nhận.

### TC-ACC-035 — Trần số lần gửi OTP cho một SĐT
- **Yêu cầu:** FR-13 · **AC:** AC-16 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** lần gửi thứ 6 trong 1 giờ (mỗi lần cách đủ 30 giây) → `429 RATE_LIMITED`; không có OTP thứ 6. SĐT chưa đăng ký và đã đăng ký bị chặn như nhau.

### TC-ACC-036 — Không giao được OTP
- **Yêu cầu:** FR-14 · **AC:** AC-17 · **Mức:** API · **Ưu tiên:** P1
- **Tiền điều kiện:** provider giả được đặt ở chế độ lỗi.
- **Kết quả mong đợi:** `503 OTP_SEND_FAILED`; không có challenge dùng được; gửi lại ngay sau khi provider hoạt động → `201` (lần lỗi không tính vào trần và không khoá gửi lại).

### API — Đăng nhập

### TC-ACC-040 — Đăng nhập từ thiết bị đã biết
- **Yêu cầu:** FR-15, FR-19, FR-23 · **AC:** AC-18 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `200 AuthSession`; không có OTP; `device.last_seen_at` = thời điểm đăng nhập; DB không chứa dấu vân tay dạng rõ.

### TC-ACC-041 — Sai SĐT và sai mật khẩu không phân biệt được
- **Yêu cầu:** FR-16 · **AC:** AC-19 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** hai phản hồi cùng `401`, cùng `code` = `INVALID_CREDENTIALS`, cùng `type`, `title`, `detail`; chỉ `traceId` khác.

### TC-ACC-042 — Khoá tạm sau 5 lần sai
- **Yêu cầu:** FR-17, BR-07 · **AC:** AC-20 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Kết quả mong đợi:** lần 1–5 sai → `401 INVALID_CREDENTIALS`; lần 6 đúng mật khẩu → `403 ACCOUNT_TEMP_LOCKED`, `params.retryAfterSeconds` ≤ 900, `failed_count` vẫn 5; sau 15 phút, đúng mật khẩu → `200`, `failed_count` = 0.

### TC-ACC-043 — Đăng nhập đúng đặt lại bộ đếm
- **Yêu cầu:** BR-07 · **AC:** AC-21 · **Mức:** API · **Ưu tiên:** P2
- **Kết quả mong đợi:** sai 4, đúng 1, sai 4 → không lần nào `ACCOUNT_TEMP_LOCKED`.

### TC-ACC-044 — Tài khoản `LOCKED` và `CLOSED`
- **Yêu cầu:** FR-18, ST-05 · **AC:** AC-22 · **Mức:** API · **Ưu tiên:** P1

| # | Trạng thái | Mật khẩu | Mong đợi |
|---|---|---|---|
| a | `LOCKED` | đúng | `403 ACCOUNT_LOCKED`, không token |
| b | `LOCKED` | sai | `401 INVALID_CREDENTIALS` |
| c | `CLOSED` | đúng | `401 INVALID_CREDENTIALS`, giống SĐT không tồn tại |

### TC-ACC-045 — Thiết bị mới phải qua OTP
- **Yêu cầu:** FR-20, BR-04, ST-06 · **AC:** AC-23 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `422 CHALLENGE_REQUIRED`; `params` có `challengeId`, `factor`, `expiresAt`, `destinationMasked`, `resendAfter`, `attemptsRemaining`; 1 OTP tới SĐT của tài khoản; không token.

### TC-ACC-046 — Xác minh OTP thiết bị mới
- **Yêu cầu:** FR-21, FR-22, FR-23, BR-09, EV-05 · **AC:** AC-24 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `200 AuthSession`; thiết bị có `trusted_at`; đúng 1 thông báo thiết bị mới, không chứa mật khẩu/OTP; lần đăng nhập kế tiếp từ thiết bị đó không đòi OTP.

### TC-ACC-047 — Sai mật khẩu từ thiết bị mới không gửi OTP
- **Yêu cầu:** FR-20 · **AC:** AC-25 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `401 INVALID_CREDENTIALS`; 0 OTP; `failed_count` tăng 1.

### TC-ACC-048 — Thứ tự ưu tiên lỗi khi đăng nhập
- **Yêu cầu:** BR-05 · **AC:** AC-26 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** bảng quyết định

| # | Tình huống | Mong đợi |
|---|---|---|
| a | SĐT sai định dạng + mật khẩu sai | `VALIDATION_ERROR` |
| b | Đang khoá tạm + thiết bị mới + mật khẩu đúng | `ACCOUNT_TEMP_LOCKED` |
| c | `LOCKED` + mật khẩu sai | `INVALID_CREDENTIALS` |
| d | `LOCKED` + mật khẩu đúng + thiết bị mới | `ACCOUNT_LOCKED`, không OTP |

### TC-ACC-049 — Đăng nhập lại sau khi được mở khoá
- **Yêu cầu:** FR-18, ST-03 · **AC:** AC-53 · **Mức:** API · **Ưu tiên:** P2
- **Kết quả mong đợi:** sau khi `LOCKED` → `ACTIVE`: đăng nhập `200`; refresh token cấp trước khi khoá vẫn `401`.

### API — Phiên

### TC-ACC-050 — Access token: hạn 5 phút, ký ES256, kiểm được bằng JWKS
- **Yêu cầu:** FR-24 · **AC:** AC-27 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Kết quả mong đợi:** header JWT `alg` = `ES256`; chữ ký kiểm được bằng khoá trong `GET /.well-known/jwks.json`; có claim `sub`, `sid`, `exp` = `iat` + 300; gọi `GET /me/devices` tại T + 4:59 → `200`, tại T + 5:00 → `401 TOKEN_EXPIRED`.

### TC-ACC-051 — Làm mới xoay vòng token
- **Yêu cầu:** FR-25, ST-12, ST-13 · **AC:** AC-28 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `200` với refresh token R2 ≠ R1 và access token mới; R1 có `used_at`.

### TC-ACC-052 — Dùng lại refresh token thu hồi cả family
- **Yêu cầu:** FR-26, ST-14, EV-09 · **AC:** AC-29 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** dùng lại R1 → `401 UNAUTHENTICATED`; R2 → `401`; bộ đếm `identity.refresh.reuse` tăng 1.

### TC-ACC-053 — Hết hạn do không hoạt động và hạn tuyệt đối
- **Yêu cầu:** FR-27 · **AC:** AC-30 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA

| # | Tình huống | Mong đợi |
|---|---|---|
| a | Làm mới sau 29 phút 59 giây không hoạt động | `200` |
| b | Làm mới sau 30 phút không hoạt động | `401 UNAUTHENTICATED` |
| c | Làm mới mỗi 20 phút tới ngày thứ 30 + 1 giây | `401 UNAUTHENTICATED` |
| d | Token cấp gần hạn tuyệt đối | `refreshTokenExpiresAt` không vượt hạn tuyệt đối |

### TC-ACC-054 — Đăng xuất chỉ thu hồi phiên hiện tại
- **Yêu cầu:** FR-28, ST-14, ST-15 · **AC:** AC-31 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `204`; refresh của D1 → `401`; refresh của D2 → `200`; không có token → `401`.

### TC-ACC-055 — Khoá hoặc đóng tài khoản thu hồi mọi phiên
- **Yêu cầu:** FR-31, ST-02, ST-04 · **AC:** AC-34 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** sau khi tài khoản `LOCKED` (hoặc `CLOSED`): làm mới ở mọi phiên → `401 UNAUTHENTICATED`; đăng nhập → `ACCOUNT_LOCKED` (hoặc `INVALID_CREDENTIALS`).
- **Ghi chú:** việc thu hồi token lúc khoá do F-BO-02 gọi; ở đây kiểm `identity` từ chối làm mới theo trạng thái tài khoản.

### API — Mật khẩu

### TC-ACC-060 — Quên mật khẩu không tiết lộ SĐT có tài khoản
- **Yêu cầu:** FR-32 · **AC:** AC-35 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** hai phản hồi `201` cùng tập trường; chỉ SĐT có tài khoản `ACTIVE` nhận OTP; tài khoản `LOCKED` không nhận OTP nhưng phản hồi giống hệt.

### TC-ACC-061 — Đặt lại mật khẩu thu hồi mọi phiên và gỡ khoá tạm
- **Yêu cầu:** FR-33, BR-08, EV-06 · **AC:** AC-36 · **Mức:** API · **Ưu tiên:** P1
- **Tiền điều kiện:** khách có 2 phiên; `failed_count` = 3.
- **Kết quả mong đợi:** `204`; mật khẩu cũ → `INVALID_CREDENTIALS`; mật khẩu mới đăng nhập được; cả hai refresh token → `401`; `failed_count` = 0, `locked_until` null; đúng 1 thông báo đổi mật khẩu.

### TC-ACC-062 — Đặt lại với mật khẩu sai chính sách không tiêu hao lượt
- **Yêu cầu:** FR-33, BR-06 · **AC:** AC-37 · **Mức:** API · **Ưu tiên:** P2
- **Kết quả mong đợi:** `400 VALIDATION_ERROR`; gửi lại cùng OTP với mật khẩu hợp lệ → `204`.

### TC-ACC-063 — Đổi mật khẩu
- **Yêu cầu:** FR-34, BR-07, BR-08, EV-06 · **AC:** AC-38 · **Mức:** API · **Ưu tiên:** P1

| # | Tình huống | Mong đợi |
|---|---|---|
| a | Mật khẩu hiện tại sai | `401 INVALID_CREDENTIALS`; `failed_count` tăng 1 |
| b | Mật khẩu mới trùng mật khẩu hiện tại | `400 VALIDATION_ERROR` |
| c | Hợp lệ, có phiên D1 (hiện tại) và D2 | `204`; refresh D2 → `401`; refresh D1 → `200`; 1 thông báo |
| d | Không có access token | `401 UNAUTHENTICATED` |

### API — Số điện thoại

### TC-ACC-065 — Đổi SĐT thành công
- **Yêu cầu:** FR-35, FR-36, BR-08, EV-02, EV-07 · **AC:** AC-41 · **Mức:** API · **Ưu tiên:** P1
- **Trạng thái:** BLOCKED một phần (OQ-028: chưa có luồng nộp và xác thực khuôn mặt). Ca này dựng challenge `FACE` đã đạt bằng cách ghi DB.
- **Tiền điều kiện:** khách tier 1, có 2 phiên.
- **Kết quả mong đợi:** `204`; đăng nhập bằng SĐT cũ → `INVALID_CREDENTIALS`, bằng SĐT mới → được; phiên khác bị thu hồi; 1 event `CustomerUpdated` có SĐT mới và `aggregateversion` = 2; thông báo tới cả SĐT cũ và mới.

### TC-ACC-066 — Đổi sang SĐT đã thuộc tài khoản khác
- **Yêu cầu:** FR-36, BR-10 · **AC:** AC-42 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `409 PHONE_ALREADY_REGISTERED`; SĐT không đổi; không có event.

### TC-ACC-067 — Chưa qua khuôn mặt thì không gửi OTP tới SĐT mới
- **Yêu cầu:** FR-35 · **AC:** AC-43 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** khách tier 1 chưa có challenge `FACE` đạt: `POST /challenges` `{purpose: CHANGE_PHONE, factor: OTP}` → `422 CHALLENGE_REQUIRED`, 0 OTP; `POST /me/phone` với `faceChallengeId` chưa đạt → `422 CHALLENGE_INVALID`.
- **Ghi chú:** nhánh "khuôn mặt không đạt" của F-ACC-02 chưa test được (OQ-028).

### TC-ACC-068 — Khách chưa eKYC không đổi được SĐT
- **Yêu cầu:** FR-37 · **AC:** AC-44 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** tier 0: `POST /challenges` (`CHANGE_PHONE`, cả `FACE` và `OTP`) và `POST /me/phone` đều `403 KYC_REQUIRED`; SĐT không đổi.

### API — Thiết bị, đồng ý

### TC-ACC-070 — Danh sách thiết bị
- **Yêu cầu:** FR-38 · **AC:** AC-45 · **Mức:** API · **Ưu tiên:** P2
- **Kết quả mong đợi:** 2 mục có `deviceId`, `lastSeenAt`, `isCurrent` (đúng 1 mục `true`); không có trường nào chứa dấu vân tay; không thấy thiết bị của khách khác.

### TC-ACC-071 — Thu hồi thiết bị
- **Yêu cầu:** FR-39, ST-14 · **AC:** AC-46 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** D2 của mình → `204`, refresh của D2 → `401`, đăng nhập lại từ D2 → `CHALLENGE_REQUIRED`; gọi lại → `204`; thiết bị của khách khác hoặc id không tồn tại → `404 NOT_FOUND`, thiết bị đó không đổi.

### TC-ACC-072 — Đồng ý hiện tại theo mục đích
- **Yêu cầu:** FR-40, FR-04 · **AC:** AC-47 · **Mức:** API · **Ưu tiên:** P2
- **Kết quả mong đợi:** với hai bản ghi `TERMS` (`2026-01`, `2026-11`): một mục `TERMS` phiên bản `2026-11`.

### API — Challenge giao dịch

### TC-ACC-075 — Cấp challenge P2P gửi OTP tới SĐT đăng ký
- **Yêu cầu:** FR-41, EV-03 · **AC:** AC-48 · **Mức:** API · **Ưu tiên:** P1
- **Kết quả mong đợi:** `201`; OTP tới SĐT của người gọi, không tới `recipientPhone`; cấp lại cùng payload trước 30 giây → `429`; payload khác → challenge mới sống song song.

### TC-ACC-076 — Xác minh challenge theo `consumerRef`
- **Yêu cầu:** FR-42, ST-07, ST-11 · **AC:** AC-49 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** bảng quyết định

| # | Gọi `POST /internal/challenges/{id}/verify` | Mong đợi |
|---|---|---|
| a | OTP đúng, payload P, `consumerRef` K1 | `200 {factor: OTP}` |
| b | Lại K1, không kèm OTP | `200` |
| c | K2, OTP đúng | `422 CHALLENGE_INVALID` |
| d | (challenge khác) payload khác P | `422 CHALLENGE_INVALID` |
| e | (challenge khác) OTP sai | `422`, `params.attemptsRemaining` = 4 |
| f | (challenge khác) lần đầu không kèm OTP | `422 CHALLENGE_INVALID` |
| g | Challenge của mục đích `LOGIN_NEW_DEVICE` | `422 CHALLENGE_INVALID` |

### API — Giới hạn tần suất, audit

### TC-ACC-080 — Giới hạn tần suất đăng nhập theo IP
- **Yêu cầu:** FR-43 · **AC:** AC-50 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Kết quả mong đợi:** yêu cầu thứ 20 từ một IP trong 1 phút được xử lý; thứ 21 → `429 RATE_LIMITED` kèm `Retry-After` ≥ 1; `failed_count` của tài khoản không đổi ở yêu cầu bị chặn.

### TC-ACC-081 — Ghi audit cho thao tác bảo mật
- **Yêu cầu:** EV-08 · **AC:** AC-51 · **Mức:** API · **Ưu tiên:** P2
- **Kết quả mong đợi:** mỗi thao tác (đăng ký, đăng nhập thành công, đăng nhập sai, khoá tạm, đổi mật khẩu, đặt lại mật khẩu, đổi SĐT, thu hồi thiết bị, dùng lại refresh token) sinh đúng 1 event `AuditRecorded` trên `audit.records` có `actor`, `action`, `ip`, `traceId`; không event nào chứa mật khẩu, OTP hay dấu vân tay.
- **Ghi chú:** `AuditRecorded` chưa có JSON Schema trong `contracts/` (thuộc F-BO-05); ca này kiểm theo danh mục event của api doc §6. Xem TQ1.

### E2E (web)

Chưa có đặc tả UI của F-ACC-01; các ca dưới nêu luồng, chi tiết màn hình bổ sung sau khi chạy `ui-ux-spec`.

### TC-ACC-085 — Đăng ký trên web rồi vào được trang sau đăng nhập
- **Yêu cầu:** UC-01, FR-29 · **AC:** AC-04, AC-32 · **Mức:** E2E · **Ưu tiên:** P1
- **Kết quả mong đợi:** sau khi nhập SĐT, OTP, mật khẩu, họ tên, đồng ý: vào trang sau đăng nhập; cookie phiên có `HttpOnly`, `Secure` (trừ môi trường local không TLS), `SameSite`; `document.cookie`, `localStorage` và nội dung phản hồi tới trình duyệt không chứa token.

### TC-ACC-086 — Đăng nhập thiết bị mới với OTP
- **Yêu cầu:** UC-02 A1 · **AC:** AC-23, AC-24 · **Mức:** E2E · **Ưu tiên:** P1
- **Kết quả mong đợi:** màn hình OTP hiện nơi nhận đã che; nhập đúng thì vào trang sau đăng nhập.

### TC-ACC-087 — Sai mật khẩu hiện một thông điệp chung
- **Yêu cầu:** FR-16 · **AC:** AC-19 · **Mức:** E2E · **Ưu tiên:** P2
- **Kết quả mong đợi:** cùng một câu cho sai SĐT và sai mật khẩu; không lộ thuật ngữ kỹ thuật.

### TC-ACC-088 — Quên mật khẩu
- **Yêu cầu:** UC-04 · **AC:** AC-35, AC-36 · **Mức:** E2E · **Ưu tiên:** P2
- **Kết quả mong đợi:** đặt được mật khẩu mới và đăng nhập lại bằng mật khẩu mới.

### TC-ACC-089 — Yêu cầu đổi trạng thái thiếu CSRF token bị từ chối
- **Yêu cầu:** FR-30 · **AC:** AC-33 · **Mức:** E2E (gọi thẳng BFF) · **Ưu tiên:** P1
- **Kết quả mong đợi:** `POST` tới BFF không kèm hoặc sai CSRF token → `403 FORBIDDEN`, thao tác không được thực hiện; kèm đúng → được xử lý.

### NFR

### TC-ACC-090 — Hiệu năng đăng nhập
- **Yêu cầu:** NFR-01 · **Mức:** NFR (k6) · **Ưu tiên:** P2
- **Kết quả mong đợi:** p99 của `POST /auth/login` (thiết bị đã biết) < 1 giây ở 50 TPS trong 10 phút.
- **Ghi chú:** Argon2id tốn CPU có chủ đích; ca này xác định số pod cần.

### TC-ACC-091 — Không dò được tài khoản qua thời gian phản hồi
- **Yêu cầu:** NFR-03 · **Mức:** NFR · **Ưu tiên:** P1
- **Kết quả mong đợi:** trung vị thời gian của 200 lần đăng nhập với SĐT không tồn tại và 200 lần với SĐT tồn tại + mật khẩu sai lệch nhau < 50 ms.

### TC-ACC-092 — Mật khẩu, OTP, SĐT đầy đủ không lọt vào log và Kafka
- **Yêu cầu:** NFR-02, NFR-07, BR-09 · **AC:** AC-52 · **Mức:** NFR · **Ưu tiên:** P1
- **Kết quả mong đợi:** chạy bộ API test với log mức DEBUG; quét log và mọi message trên `identity.events`, `audit.records`: không có chuỗi mật khẩu đã dùng, OTP đã phát, refresh/access token; SĐT đầy đủ chỉ xuất hiện trong `data.phone` của event `identity.events`.

### TC-ACC-093 — Tiêu đề bảo mật của web
- **Yêu cầu:** NFR-04 · **Mức:** NFR · **Ưu tiên:** P2
- **Kết quả mong đợi:** mọi phản hồi HTML của `web` có `Content-Security-Policy`; mọi phản hồi qua TLS có `Strict-Transport-Security`.

### TC-ACC-094 — Metric và cảnh báo
- **Yêu cầu:** NFR-05, NFR-06, EV-09 · **Mức:** NFR · **Ưu tiên:** P3
- **Kết quả mong đợi:** `/actuator/prometheus` có bộ đếm đăng nhập theo kết quả, khoá tạm, OTP gửi/lỗi theo mục đích, dùng lại refresh token; cảnh báo P1 khi tỷ lệ OTP lỗi vượt ngưỡng trong 5 phút (kiểm trên staging).

## Câu hỏi mở
| # | Câu hỏi | Nguồn | TC bị ảnh hưởng |
|---|---|---|---|
| TQ1 | `AuditRecorded` chưa có schema; `action` và `target` của từng thao tác trong F-ACC-01 lấy giá trị nào? (OQ-052) | SRS EV-08; api doc §6 | TC-ACC-081 |
| — | Cơ chế ép lỗi (OQ-024) | SRS AC-08 | TC-ACC-011 |
| — | Luồng khuôn mặt (OQ-028) | SRS FR-35 | TC-ACC-065, 067 |
| — | Quy tắc che tên (OQ-009, OQ-047) | EV-01 | TC-ACC-004 |
| — | Giá trị tham số `auth.*` (OQ-036); hành vi nhiều tab (OQ-037) | SRS BR, FR-26 | Mọi ca dùng số liệu minh hoạ; TC-ACC-012 |
