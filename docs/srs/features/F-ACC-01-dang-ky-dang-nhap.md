# F-ACC-01 — Đăng ký & đăng nhập

| | |
|---|---|
| Trạng thái | Draft |
| Phiên bản | 0.5 |
| Ngày | 2026-11-04 |
| Yêu cầu nguồn (sở hữu) | FR-ID-01, FR-ID-05, FR-ID-06 |
| Yêu cầu liên quan | FR-ID-04 (cấp định danh mặc định), NFR-S-01, NFR-S-02, NFR-S-03, NFR-S-04, NFR-L-03, NFR-O-02, NFR-P-01 |
| Cross-cutting áp dụng | CC-SEC, CC-COMP, CC-OBS, CC-UX |
| Tài liệu thiết kế | [design §5 (identity), §6.7, §10, R6](../../design/2026-11-04-ewallet-design.md) · [api §2 Auth & identity, §4](../../design/2026-11-04-ewallet-api.md) · [data-model: identity](../../design/2026-11-04-ewallet-data-model.md) |
| Phụ thuộc | F-NTF-01 (gửi OTP và cảnh báo), F-ACC-02 (xác thực khuôn mặt cho đổi SĐT; xem 2.3) |

Nhãn nguồn: `[Thiết kế …]` = lấy từ design, requirements không nói · `[Giả định]` = suy ra, cần người duyệt xác nhận · `[Cần xác nhận Q#]` = chưa có quyết định, xem mục 9.

## 1. Giới thiệu

### 1.1 Mục đích
Cho phép khách hàng tạo tài khoản bằng số điện thoại, đăng nhập an toàn trên web và quản lý thông tin xác thực của mình (mật khẩu, số điện thoại, thiết bị). Đồng thời cung cấp cơ chế challenge (OTP) mà các tính năng khác dùng để yêu cầu xác thực tăng cường.

### 1.2 Phạm vi
- **Trong phạm vi:**
  - Đăng ký bằng SĐT và OTP; ghi nhận đồng ý điều khoản.
  - Đăng nhập bằng mật khẩu, xác thực OTP khi dùng thiết bị mới, ghi nhận và cảnh báo thiết bị mới.
  - Phiên đăng nhập: cấp, làm mới, hết hạn, đăng xuất.
  - Quên mật khẩu, đổi mật khẩu, đổi SĐT.
  - Xem và thu hồi thiết bị; xem các đồng ý đã cho.
  - Cơ chế challenge OTP dùng chung (cấp, gửi, xác minh) cho xác thực tăng cường của lệnh tiền.
- **Ngoài phạm vi:**
  - Định danh eKYC, cấp định danh, chặn trùng CCCD, xác thực khuôn mặt (F-ACC-02).
  - Khoá/mở khoá tài khoản bởi nhân viên (F-BO-02), đăng nhập back-office (F-BO-01).
  - Đóng tài khoản (F-ACC-03).
  - Ngưỡng cần xác thực tăng cường của từng lệnh tiền (do tính năng của lệnh quy định, ví dụ F-PAY-03.BR-07).
  - Thu thập và rút lại đồng ý theo quyền của chủ thể dữ liệu (F-CMP-01); tính năng này chỉ ghi nhận đồng ý lúc đăng ký và cho xem.
  - Nội dung và kênh gửi thông báo (F-NTF-01); tính năng này quy định *khi nào* và *nội dung chính*.

### 1.3 Định nghĩa & viết tắt
| Thuật ngữ | Định nghĩa |
|---|---|
| Tài khoản (customer) | Bản ghi định danh của khách trong `identity`, khoá bởi SĐT. Ví được mở tự động sau đăng ký (theo `CustomerRegistered`) |
| Cấp định danh (tier) | Mức eKYC của tài khoản; `0` = chưa eKYC. Tài khoản mới có tier `0` (FR-ID-04) |
| OTP | Mã dùng một lần, 6 chữ số, gửi qua `NotificationSender` |
| Challenge | Yêu cầu xác thực có mã `challengeId`, gắn với một mục đích (`purpose`) và một đối tượng (SĐT hoặc khách). Có `factor` `OTP` hoặc `FACE` |
| Mục đích (purpose) | `REGISTER`, `LOGIN_NEW_DEVICE`, `RESET_PASSWORD`, `CHANGE_PHONE`, và mục đích giao dịch (`P2P`, `WITHDRAW`...). `RESET_PASSWORD` là bổ sung `[Giả định]` so với data model |
| Thiết bị đã biết | Thiết bị có `fingerprint_hash` đã được ghi cho tài khoản và còn tin cậy (BR-04) |
| Phiên | Cặp access token (JWT, sống ngắn) và refresh token (xoay vòng) cấp cho một thiết bị |
| Token family | Chuỗi các refresh token nối tiếp nhau từ một lần đăng nhập; thu hồi cả chuỗi khi nghi bị đánh cắp |
| `consumerRef` | Khoá idempotency của lệnh đã dùng một challenge |

### 1.4 Tài liệu tham chiếu
[requirements.md](../../requirements.md) §2.1, §3 (NFR-S, NFR-L) · [index SRS](../index.md) · design và api (xem đầu trang) · [F-PAY-03](F-PAY-03-chuyen-tien-vi-vi.md) (nơi dùng challenge) · [contracts/openapi/identity/public.yaml](../../../source-code/contracts/openapi/identity/public.yaml).

## 2. Mô tả tổng quan

### 2.1 Bối cảnh & giá trị
Đăng nhập là cổng vào của mọi luồng tiền: tài khoản bị chiếm thì tiền bị chuyển đi. Đồng thời, chính OTP lại là điểm yếu về chi phí (SMS) và về khả dụng (`notification` sập thì không ai đăng nhập được từ thiết bị mới, design R6). Tính năng này vì vậy phải chống dò tài khoản, chống đoán mật khẩu/OTP, chống lạm dụng gửi SMS, và vẫn dùng được khi người dùng gửi lại yêu cầu do mất mạng.

Tính năng này **không thay đổi số dư**. Mục 7 của quy tắc viết spec (nội dung bắt buộc cho tính năng chạm tới tiền) không áp dụng, trừ phần liên quan đến idempotency của việc xác minh challenge mà lệnh tiền dùng (FR-42, FR-08).

### 2.2 Actor & quyền
| Actor | Mô tả | Được phép |
|---|---|---|
| Khách chưa có tài khoản | Người dùng web chưa đăng ký | Đăng ký |
| Khách | Chủ tài khoản `ACTIVE` | Đăng nhập, đổi mật khẩu/SĐT, quên mật khẩu, quản lý thiết bị của mình, xem đồng ý của mình |
| Dịch vụ giao dịch | `payment`, `bank-gateway` (gọi nội bộ) | Xác minh challenge của lệnh |
| Hệ thống | | Gửi OTP và cảnh báo, thu hồi phiên khi tài khoản bị khoá |

Nhân viên không có thao tác nào trong tính năng này. Việc khoá/mở khoá do F-BO-02 thực hiện; tính năng này chỉ quy định hệ quả lên đăng nhập và phiên (FR-31).

### 2.3 Phụ thuộc
| Loại | Tên | Vai trò |
|---|---|---|
| Tính năng | F-NTF-01 | Chuyển OTP và cảnh báo tới khách (`NotificationSender`) |
| Tính năng | F-ACC-02 | Cung cấp xác thực khuôn mặt (`factor = FACE`) cho đổi SĐT. **Phụ thuộc vòng:** F-ACC-02 cần F-ACC-01 để có tài khoản. Cách giải quyết: UC-01..05, UC-07, UC-08 không phụ thuộc F-ACC-02; chỉ UC-06 cần F-ACC-02 khi chạy |
| Hệ thống ngoài | `NotificationSender` (fake ở giai đoạn 1) | Giao OTP và cảnh báo |
| Cấu hình | Tham số `auth.*` (mục 3.3) | Hầu hết chưa có giá trị chốt |

### 2.4 Giả định & ràng buộc
- `[Thiết kế §10]` Mật khẩu băm Argon2id; OTP 6 chữ số lưu hash; access JWT sống 5 phút, ký ES256; refresh token xoay vòng, phát hiện dùng lại; token nằm trong cookie httpOnly do BFF giữ.
- `[Thiết kế api §2]` Đăng nhập từ thiết bị lạ thì trả `CHALLENGE_REQUIRED` kèm `challengeId`. Thiết kế chọn *chỉ thiết bị mới* cần OTP; requirements ghi "mật khẩu + OTP/2FA" nên cần xác nhận `[Cần xác nhận Q1]`.
- `[Giả định]` Chỉ hỗ trợ SĐT Việt Nam (BR-01) `[Cần xác nhận Q15]`.
- `[Giả định]` Thiết bị dùng để đăng ký được coi là thiết bị đã biết, vì người dùng vừa chứng minh sở hữu SĐT trên thiết bị đó (FR-07).
- `[Giả định]` Mọi tham số `auth.*` là cấu hình triển khai, không chỉnh qua CMS (FR-BO-09 chỉ nêu hạn mức, phí, ngưỡng xác thực tăng cường).
- `[Ràng buộc]` `customer.phone` là duy nhất trên toàn bảng, kể cả tài khoản `CLOSED` (data model). Hệ quả cho SĐT của tài khoản đã đóng: `[Cần xác nhận Q10]`.
- `[Ràng buộc]` Ví được mở bất đồng bộ qua event `CustomerRegistered`, nên ngay sau đăng ký có thể chưa có ví `[Cần xác nhận Q13]`.
- `[Ràng buộc]` Mọi con số trong các AC ở mục 7 là cấu hình *minh hoạ*, không phải giá trị đã chốt.

## 3. Yêu cầu chức năng

### 3.1 Use case

| ID | Tên | Actor | Mục tiêu |
|---|---|---|---|
| F-ACC-01.UC-01 | Đăng ký tài khoản | Khách chưa có tài khoản | Tạo tài khoản sau khi chứng minh sở hữu SĐT |
| F-ACC-01.UC-02 | Đăng nhập | Khách | Có phiên đăng nhập; thiết bị mới phải qua OTP |
| F-ACC-01.UC-03 | Duy trì và kết thúc phiên | Khách, Hệ thống | Làm mới, hết hạn, đăng xuất |
| F-ACC-01.UC-04 | Quên mật khẩu | Khách | Đặt lại mật khẩu bằng OTP |
| F-ACC-01.UC-05 | Đổi mật khẩu | Khách | Đổi mật khẩu khi đã đăng nhập |
| F-ACC-01.UC-06 | Đổi số điện thoại | Khách | Đổi SĐT sau khi xác thực khuôn mặt |
| F-ACC-01.UC-07 | Quản lý thiết bị và xem đồng ý | Khách | Xem, thu hồi thiết bị; xem đồng ý đã cho |
| F-ACC-01.UC-08 | Cấp và xác minh challenge giao dịch | Khách, Dịch vụ giao dịch | Cung cấp xác thực tăng cường cho lệnh tiền |

#### F-ACC-01.UC-01 — Đăng ký tài khoản
- **Tiền điều kiện:** Khách chưa đăng nhập; có SĐT Việt Nam nhận được tin nhắn.
- **Hậu điều kiện (thành công):** Tài khoản `ACTIVE`, tier `0`; mật khẩu đã băm; đồng ý `TERMS` đã ghi; thiết bị hiện tại là thiết bị đã biết; có phiên đăng nhập; event `CustomerRegistered` sẽ được phát (ví được mở theo sau).
- **Hậu điều kiện (thất bại):** Không có tài khoản, không có đồng ý, không có thiết bị, không có event; không có phiên.
- **Luồng chính:**
  1. Khách nhập SĐT.
  2. Hệ thống chuẩn hoá SĐT (BR-01), tạo challenge `REGISTER`, gửi OTP, trả `challengeId` và thông tin hiển thị (nơi nhận đã che, hạn dùng, thời điểm được gửi lại, số lần nhập còn lại).
  3. Khách nhập OTP, mật khẩu, họ tên, và đồng ý điều khoản (phiên bản hiện hành).
  4. Hệ thống kiểm tra theo BR-06, tạo tài khoản, ghi đồng ý và thiết bị, và cấp phiên.
- **Luồng thay thế:**
  - `A1` (bước 2) SĐT đã có tài khoản: hệ thống trả cùng dạng phản hồi như SĐT mới, không gửi OTP, gửi thông báo "đã đăng ký" tới SĐT đó (FR-02).
  - `A2` (bước 3) Khách chưa nhận được mã: xin gửi lại sau `auth.otp.resend_after` (FR-12).
- **Luồng lỗi:**
  - `E1` (bước 2) SĐT sai định dạng: `VALIDATION_ERROR`.
  - `E2` (bước 2) Không giao được OTP cho dịch vụ thông báo: `OTP_SEND_FAILED`.
  - `E3` (bước 4) Dữ liệu sai (mật khẩu không đạt chính sách, họ tên sai): `VALIDATION_ERROR`, challenge giữ nguyên.
  - `E4` (bước 4) Thiếu đồng ý điều khoản: `CONSENT_REQUIRED`.
  - `E5` (bước 4) OTP sai, hết hạn, hết lượt: `CHALLENGE_INVALID`.
  - `E6` (bước 4) Một yêu cầu khác cùng SĐT đã tạo tài khoản trước: `PHONE_ALREADY_REGISTERED`.

#### F-ACC-01.UC-02 — Đăng nhập
- **Tiền điều kiện:** Khách có tài khoản.
- **Hậu điều kiện (thành công):** Có phiên đăng nhập; thiết bị được ghi hoặc cập nhật `last_seen_at`; bộ đếm sai được đặt lại.
- **Hậu điều kiện (thất bại):** Không cấp token; bộ đếm sai tăng 1 nếu lỗi là sai mật khẩu.
- **Luồng chính (thiết bị đã biết):**
  1. Khách nhập SĐT và mật khẩu; client gửi kèm dấu vân tay thiết bị.
  2. Hệ thống kiểm tra theo BR-05 và cấp phiên.
- **Luồng thay thế:**
  - `A1` (bước 2) Thiết bị mới: hệ thống tạo challenge `LOGIN_NEW_DEVICE`, gửi OTP tới SĐT của tài khoản, trả `CHALLENGE_REQUIRED` kèm `challengeId`, không cấp token. Khách nhập OTP đúng thì hệ thống cấp phiên, ghi thiết bị, và gửi cảnh báo thiết bị mới (FR-20..22).
- **Luồng lỗi:**
  - `E1` Sai SĐT hoặc mật khẩu: `INVALID_CREDENTIALS` (FR-16).
  - `E2` Đang bị khoá tạm: `ACCOUNT_TEMP_LOCKED` (FR-17).
  - `E3` Tài khoản `LOCKED`: `ACCOUNT_LOCKED` (FR-18).
  - `E4` OTP sai hoặc hết hạn: `CHALLENGE_INVALID`.
  - `E5` Vượt giới hạn tần suất: `RATE_LIMITED`.

#### F-ACC-01.UC-03 — Duy trì và kết thúc phiên
- **Tiền điều kiện:** Có phiên.
- **Hậu điều kiện (thành công):** Làm mới: cặp token mới, refresh token cũ thành đã dùng. Đăng xuất: cả token family bị thu hồi.
- **Hậu điều kiện (thất bại):** Token không được cấp; nếu phát hiện dùng lại refresh token thì cả family bị thu hồi.
- **Luồng chính:**
  1. Access token hết hạn; BFF dùng refresh token để xin cặp mới.
  2. Hệ thống xoay vòng và trả cặp mới.
- **Luồng thay thế:** `A1` Khách đăng xuất: hệ thống thu hồi family hiện tại (FR-28).
- **Luồng lỗi:**
  - `E1` Refresh token đã dùng, đã thu hồi hoặc hết hạn: `UNAUTHENTICATED`, khách đăng nhập lại.
  - `E2` Không hoạt động quá `auth.session.idle_timeout`: phiên kết thúc (FR-27).

#### F-ACC-01.UC-04 — Quên mật khẩu
- **Tiền điều kiện:** Khách chưa đăng nhập, nhớ SĐT.
- **Hậu điều kiện (thành công):** Mật khẩu mới có hiệu lực; mọi phiên cũ bị thu hồi; bộ đếm sai và khoá tạm được xoá; có thông báo.
- **Hậu điều kiện (thất bại):** Mật khẩu cũ giữ nguyên.
- **Luồng chính:**
  1. Khách nhập SĐT.
  2. Hệ thống trả phản hồi không cho biết SĐT có tài khoản hay không (FR-32); nếu có tài khoản `ACTIVE` thì gửi OTP.
  3. Khách nhập OTP và mật khẩu mới.
  4. Hệ thống đặt mật khẩu mới (FR-33).
- **Luồng lỗi:** `E1` OTP sai/hết hạn: `CHALLENGE_INVALID`. `E2` Mật khẩu không đạt chính sách: `VALIDATION_ERROR`, challenge giữ nguyên. `E3` `RATE_LIMITED`, `OTP_SEND_FAILED`.

#### F-ACC-01.UC-05 — Đổi mật khẩu
- **Tiền điều kiện:** Đã đăng nhập.
- **Hậu điều kiện (thành công):** Mật khẩu mới có hiệu lực; các phiên khác bị thu hồi, phiên hiện tại giữ; có thông báo.
- **Hậu điều kiện (thất bại):** Mật khẩu cũ giữ nguyên.
- **Luồng chính:** Khách nhập mật khẩu hiện tại và mật khẩu mới; hệ thống kiểm tra và đổi (FR-34).
- **Luồng lỗi:** `E1` Mật khẩu hiện tại sai: `INVALID_CREDENTIALS` (tính vào bộ đếm sai). `E2` Mật khẩu mới không hợp lệ hoặc trùng mật khẩu hiện tại: `VALIDATION_ERROR`.

#### F-ACC-01.UC-06 — Đổi số điện thoại
- **Tiền điều kiện:** Đã đăng nhập; có dữ liệu khuôn mặt để so sánh (FR-37); F-ACC-02 sẵn sàng.
- **Hậu điều kiện (thành công):** SĐT mới thay SĐT cũ; phát `CustomerUpdated`; các phiên khác bị thu hồi; thông báo tới cả SĐT cũ và mới.
- **Hậu điều kiện (thất bại):** SĐT không đổi.
- **Luồng chính:**
  1. Khách nhập SĐT mới.
  2. Hệ thống yêu cầu xác thực khuôn mặt (challenge `CHANGE_PHONE`, `factor = FACE`).
  3. Khách xác thực khuôn mặt đạt.
  4. Hệ thống gửi OTP tới SĐT mới; khách nhập OTP.
  5. Hệ thống đổi SĐT (FR-35, FR-36).
- **Luồng lỗi:** `E1` Khuôn mặt không đạt: `CHALLENGE_INVALID`. `E2` SĐT mới đã thuộc tài khoản khác: `PHONE_ALREADY_REGISTERED`. `E3` Chưa có dữ liệu khuôn mặt: `KYC_REQUIRED` (FR-37). `E4` OTP sai: `CHALLENGE_INVALID`.

#### F-ACC-01.UC-07 — Quản lý thiết bị và xem đồng ý
- **Tiền điều kiện:** Đã đăng nhập.
- **Hậu điều kiện (thành công):** Xem: không đổi dữ liệu. Thu hồi: thiết bị hết tin cậy, phiên của thiết bị đó bị thu hồi.
- **Luồng chính:** Khách mở danh sách thiết bị (FR-38), chọn thu hồi một thiết bị (FR-39), hoặc mở danh sách đồng ý (FR-40).
- **Luồng lỗi:** `E1` Thiết bị không thuộc khách hoặc không tồn tại: `NOT_FOUND`.

#### F-ACC-01.UC-08 — Cấp và xác minh challenge giao dịch
- **Tiền điều kiện:** Khách đã đăng nhập; tính năng giao dịch xác định lệnh cần xác thực tăng cường (ví dụ F-PAY-03.BR-07).
- **Hậu điều kiện (thành công):** Challenge gắn với nội dung lệnh; xác minh thành công thì challenge được dùng cho đúng một `consumerRef`.
- **Hậu điều kiện (thất bại):** Không có lệnh nào được thực hiện nhờ challenge này.
- **Luồng chính:**
  1. Khách (qua client) xin challenge cho một nội dung lệnh.
  2. Hệ thống tạo challenge, hash nội dung, gửi OTP tới SĐT đăng ký của khách (FR-41).
  3. Khách nhập OTP; client gửi lệnh kèm `challengeId` và OTP.
  4. Dịch vụ giao dịch gọi `identity` xác minh (FR-42).
- **Luồng thay thế:** `A1` Lệnh được gửi lại với cùng `Idempotency-Key` (cùng `consumerRef`): xác minh lại vẫn thành công, không đòi OTP mới.
- **Luồng lỗi:** `E1` OTP sai, hết hạn, hết lượt, nội dung lệnh khác, hoặc `consumerRef` khác: `CHALLENGE_INVALID`. `E2` Không giao được OTP: `OTP_SEND_FAILED`.

### 3.2 Yêu cầu chức năng

**Đăng ký**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-ACC-01.FR-01 | Khi khách gửi SĐT hợp lệ (BR-01) để đăng ký, hệ thống phải tạo challenge `REGISTER`, gửi OTP tới SĐT đó, và trả `challengeId`, nơi nhận đã che, thời điểm hết hạn, thời điểm sớm nhất được gửi lại, số lần nhập còn lại. | M | FR-ID-01; [Thiết kế api §2, OQ-019] |
| F-ACC-01.FR-02 | Khi SĐT gửi để đăng ký đã có tài khoản, hệ thống không được gửi OTP, phải trả phản hồi cùng mã trạng thái và cùng cấu trúc như SĐT mới, và phải gửi thông báo EV-04 tới SĐT đó. Challenge trong phản hồi không được dùng được ở bước hoàn tất. | M | [Giả định]; [Cần xác nhận Q2] |
| F-ACC-01.FR-03 | Khi khách hoàn tất đăng ký với `challengeId`, OTP đúng, mật khẩu (BR-02), họ tên (BR-03) và đồng ý điều khoản, hệ thống phải tạo đúng một tài khoản ở trạng thái `ACTIVE`, tier `0`, với mật khẩu băm Argon2id. | M | FR-ID-01; FR-ID-04; [Thiết kế §10] |
| F-ACC-01.FR-04 | Hệ thống không được tạo tài khoản nếu khách chưa đồng ý điều khoản (`TERMS`) ở phiên bản `auth.terms.current_version`; khi tạo, hệ thống phải ghi đồng ý gồm mục đích, phiên bản điều khoản, kết quả đồng ý, thời điểm, địa chỉ IP và user agent. Bản ghi đồng ý chỉ được thêm, không được sửa hay xoá. | M | NFR-L-03; [Thiết kế §6.7; data model identity.consent] |
| F-ACC-01.FR-05 | Việc tạo tài khoản, ghi mật khẩu, ghi đồng ý, ghi thiết bị và ghi event `CustomerRegistered` phải là một thao tác không thể dở dang: hoặc cùng thành công, hoặc không có gì được ghi. | M | [Thiết kế §6 (outbox)] |
| F-ACC-01.FR-06 | Khi hai yêu cầu hoàn tất đăng ký cho cùng SĐT chạy đồng thời, hệ thống phải tạo đúng một tài khoản; yêu cầu còn lại phải bị từ chối với `PHONE_ALREADY_REGISTERED`. | M | [Thiết kế data model: `customer.phone` duy nhất] |
| F-ACC-01.FR-07 | Khi đăng ký thành công, hệ thống phải cấp phiên và ghi thiết bị hiện tại là thiết bị đã biết của tài khoản. | M | [Giả định] |
| F-ACC-01.FR-08 | Khi nhận yêu cầu hoàn tất đăng ký với challenge đã dùng, hệ thống phải từ chối với `CHALLENGE_INVALID`, không tạo thêm tài khoản và không cấp phiên. Khách đã đăng ký xong mà không nhận được phản hồi thì đăng nhập bằng SĐT và mật khẩu. | M | [Giả định] |

**OTP và challenge (dùng chung)**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-ACC-01.FR-09 | Hệ thống phải sinh OTP gồm 6 chữ số bằng bộ sinh số ngẫu nhiên an toàn mật mã, lưu dạng hash, đặt thời hạn `auth.otp.ttl`, và chỉ chấp nhận mã còn hạn: mã hợp lệ khi thời điểm kiểm tra nhỏ hơn thời điểm hết hạn. | M | NFR-S-03; [Thiết kế §10] |
| F-ACC-01.FR-10 | Challenge phải gắn với một mục đích và một đối tượng (SĐT cho đăng ký, quên mật khẩu và đổi SĐT; khách cho đăng nhập thiết bị mới và giao dịch). Challenge không được dùng cho mục đích khác hoặc đối tượng khác. | M | [Thiết kế data model identity.otp_challenge] |
| F-ACC-01.FR-11 | Mỗi challenge chỉ được nhập sai tối đa `auth.otp.max_attempts` lần; khi hết lượt, challenge phải bị vô hiệu kể cả khi sau đó nhập đúng mã. Sau mỗi lần nhập sai, hệ thống phải trả số lần còn lại. | M | NFR-S-03; [Thiết kế api §2] |
| F-ACC-01.FR-12 | Hệ thống không được gửi lại OTP sớm hơn `auth.otp.resend_after` kể từ lần gửi trước (trả `RATE_LIMITED` kèm `Retry-After`). Mỗi lần gửi lại phải tạo challenge mới và vô hiệu challenge cũ cùng mục đích và đối tượng. | M | NFR-S-03; [Thiết kế api §2]; [Giả định] về việc vô hiệu challenge cũ |
| F-ACC-01.FR-13 | Hệ thống không được gửi quá `auth.otp.max_sends` OTP trong `auth.otp.send_window` cho cùng một SĐT và mục đích; vượt thì trả `RATE_LIMITED`. | M | NFR-S-03 (chống lạm dụng gửi SMS); [Cần xác nhận Q7] |
| F-ACC-01.FR-14 | Nếu hệ thống không bàn giao được OTP cho dịch vụ thông báo (lỗi hoặc quá thời gian chờ), hệ thống phải trả `OTP_SEND_FAILED`, vô hiệu challenge vừa tạo, và không tính lần đó vào giới hạn của FR-13. | M | [Thiết kế R6]; [Giả định] |

**Đăng nhập**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-ACC-01.FR-15 | Hệ thống phải cho phép khách đăng nhập bằng SĐT, mật khẩu và dấu vân tay thiết bị. | M | FR-ID-05 |
| F-ACC-01.FR-16 | Khi SĐT không có tài khoản, hoặc mật khẩu sai, hệ thống phải trả cùng mã lỗi `INVALID_CREDENTIALS`, cùng mã trạng thái và cùng nội dung, để người gọi không phân biệt được hai trường hợp. | M | NFR-S-03; [Giả định] |
| F-ACC-01.FR-17 | Hệ thống phải đếm số lần nhập sai mật khẩu liên tiếp theo tài khoản. Khi đạt `auth.login.max_failures`, hệ thống phải khoá đăng nhập trong `auth.login.lock_duration` và trả `ACCOUNT_TEMP_LOCKED` kèm số giây còn lại. Trong thời gian khoá, hệ thống không được kiểm tra mật khẩu và không được tăng bộ đếm. Đăng nhập đúng mật khẩu phải đặt bộ đếm về 0. | M | NFR-S-03; [Thiết kế data model identity.credential]; [Cần xác nhận Q7] |
| F-ACC-01.FR-18 | Khi tài khoản ở trạng thái `LOCKED` và mật khẩu đúng, hệ thống phải từ chối với `ACCOUNT_LOCKED` và không cấp token. Khi tài khoản `CLOSED`, hệ thống phải trả `INVALID_CREDENTIALS` như tài khoản không tồn tại. | M | [Giả định] |
| F-ACC-01.FR-19 | Khi mật khẩu đúng và thiết bị là thiết bị đã biết, hệ thống phải cấp phiên mà không yêu cầu OTP. | M | FR-ID-05; [Thiết kế api §2]; [Cần xác nhận Q1] |
| F-ACC-01.FR-20 | Khi mật khẩu đúng và thiết bị là thiết bị mới (BR-04), hệ thống phải tạo challenge `LOGIN_NEW_DEVICE`, gửi OTP tới SĐT của tài khoản và trả `CHALLENGE_REQUIRED` kèm `challengeId`, không cấp token. Hệ thống không được gửi OTP khi mật khẩu sai. | M | FR-ID-05; [Thiết kế api §2]; [Giả định] về việc không gửi OTP khi sai mật khẩu |
| F-ACC-01.FR-21 | Khi khách nhập đúng OTP của challenge `LOGIN_NEW_DEVICE`, hệ thống phải cấp phiên và ghi thiết bị là thiết bị đã biết. | M | FR-ID-05 |
| F-ACC-01.FR-22 | Sau khi đăng nhập thành công từ thiết bị mới, hệ thống phải gửi cảnh báo EV-05 tới khách. | M | FR-ID-05 |
| F-ACC-01.FR-23 | Hệ thống phải lưu dấu vân tay thiết bị dưới dạng HMAC, không lưu bản gốc, và cập nhật thời điểm thấy gần nhất mỗi lần đăng nhập thành công. | M | FR-ID-05; NFR-L-03; [Thiết kế data model identity.device] |

**Phiên**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-ACC-01.FR-24 | Hệ thống phải cấp access token JWT sống `auth.access.ttl` (5 phút), ký ES256, và công bố khoá công khai qua JWKS. | M | [Thiết kế §10] |
| F-ACC-01.FR-25 | Khi làm mới phiên, hệ thống phải cấp cặp token mới và đánh dấu refresh token vừa dùng là đã dùng; mỗi refresh token chỉ được dùng đúng một lần. | M | [Thiết kế §10] |
| F-ACC-01.FR-26 | Khi nhận refresh token đã dùng (ngoài khoảng `auth.refresh.reuse_grace`), hệ thống phải thu hồi toàn bộ token family của nó và từ chối với `UNAUTHENTICATED`. | M | [Thiết kế §10]; [Cần xác nhận Q8] |
| F-ACC-01.FR-27 | Hệ thống phải kết thúc phiên khi refresh token quá `auth.refresh.ttl` kể từ lúc đăng nhập, hoặc khi không có hoạt động quá `auth.session.idle_timeout`. | M | NFR-S-02; [Cần xác nhận Q7] |
| F-ACC-01.FR-28 | Khi khách đăng xuất, hệ thống phải thu hồi token family hiện tại; không được thu hồi phiên của thiết bị khác. | M | [Thiết kế api §2] |
| F-ACC-01.FR-29 | Cookie phiên phải có thuộc tính `HttpOnly`, `Secure` và `SameSite = auth.cookie.same_site`. Access token và refresh token không được xuất hiện trong nội dung phản hồi gửi tới trình duyệt, trong URL, hay trong `localStorage`. | M | NFR-S-02; [Thiết kế §10] |
| F-ACC-01.FR-30 | Mọi yêu cầu thay đổi trạng thái gửi từ trình duyệt (POST, PUT, PATCH, DELETE) phải kèm CSRF token hợp lệ; thiếu hoặc sai thì trả `FORBIDDEN` và không thực hiện. | M | NFR-S-02 |
| F-ACC-01.FR-31 | Khi tài khoản chuyển sang `LOCKED` hoặc `CLOSED`, hệ thống phải thu hồi mọi refresh token của tài khoản và từ chối làm mới. Access token đã cấp hết hiệu lực chậm nhất sau `auth.access.ttl`. | M | [Giả định]; [Thiết kế §10] |

**Mật khẩu và số điện thoại**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-ACC-01.FR-32 | Khi khách gửi SĐT để quên mật khẩu, hệ thống phải trả cùng mã trạng thái và cùng cấu trúc cho SĐT có và không có tài khoản; chỉ tài khoản `ACTIVE` mới nhận OTP (challenge `RESET_PASSWORD`). | M | FR-ID-06; [Giả định]; [Cần xác nhận Q2] |
| F-ACC-01.FR-33 | Khi khách nhập đúng OTP và mật khẩu mới hợp lệ, hệ thống phải đặt mật khẩu mới, thu hồi mọi phiên của tài khoản, đặt bộ đếm sai về 0, gỡ khoá tạm, và gửi thông báo EV-06. Mật khẩu mới không hợp lệ không được làm tiêu hao lượt nhập OTP (BR-06). | M | FR-ID-06; [Cần xác nhận Q5] |
| F-ACC-01.FR-34 | Khi đổi mật khẩu, hệ thống phải yêu cầu mật khẩu hiện tại đúng (sai thì tính vào bộ đếm của FR-17), từ chối mật khẩu mới trùng mật khẩu hiện tại, thu hồi mọi phiên khác (giữ phiên hiện tại), và gửi thông báo EV-06. | M | FR-ID-06; [Giả định] |
| F-ACC-01.FR-35 | Khi đổi SĐT, hệ thống phải yêu cầu hai bước theo thứ tự: xác thực khuôn mặt đạt (challenge `CHANGE_PHONE`, `factor = FACE`, do F-ACC-02 thực hiện), rồi OTP gửi tới SĐT mới. | M | FR-ID-06 |
| F-ACC-01.FR-36 | Khi đổi SĐT thành công, hệ thống phải thay SĐT của tài khoản, từ chối SĐT mới đã thuộc tài khoản khác (`PHONE_ALREADY_REGISTERED`), thu hồi mọi phiên khác, phát `CustomerUpdated` (EV-02) và gửi thông báo EV-07 tới cả SĐT cũ và SĐT mới. Từ lúc đổi, SĐT cũ không dùng để đăng nhập được. | M | FR-ID-06; [Thiết kế api §6] |
| F-ACC-01.FR-37 | Khi tài khoản chưa có dữ liệu khuôn mặt để so sánh, hệ thống không được cho phép đổi SĐT và phải trả `KYC_REQUIRED`. | M | [Giả định]; [Cần xác nhận Q4] |

**Thiết bị và đồng ý**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-ACC-01.FR-38 | Hệ thống phải cho khách xem danh sách thiết bị của mình gồm mã thiết bị, nhãn, thời điểm thấy gần nhất và dấu hiệu thiết bị hiện tại; không được trả dấu vân tay thiết bị. | S | FR-ID-05; [Thiết kế api §2 `/me/devices`] |
| F-ACC-01.FR-39 | Khi khách thu hồi một thiết bị của mình, hệ thống phải bỏ tin cậy thiết bị đó và thu hồi các refresh token của nó; lần đăng nhập sau từ thiết bị đó phải qua OTP. Thiết bị không thuộc khách phải trả `NOT_FOUND`. | S | FR-ID-05; [Thiết kế api §2] |
| F-ACC-01.FR-40 | Hệ thống phải cho khách xem đồng ý hiện tại theo từng mục đích, lấy từ bản ghi mới nhất của mỗi mục đích. | S | NFR-L-03; [Thiết kế §6.7] |

**Challenge giao dịch**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-ACC-01.FR-41 | Khi khách đã đăng nhập xin challenge cho một lệnh, hệ thống phải hash nội dung lệnh, gắn vào challenge, và gửi OTP tới SĐT đăng ký của khách. Hệ thống không được gửi OTP tới SĐT do client chỉ định. | M | FR-P2P-04; NFR-L-04; [Thiết kế api §2 `/challenges`] |
| F-ACC-01.FR-42 | Khi dịch vụ giao dịch xác minh challenge, hệ thống phải chấp nhận khi OTP đúng, còn hạn, nội dung lệnh khớp hash, và challenge chưa gắn `consumerRef` khác; phải ghi `consumerRef` vào challenge. Xác minh lại với cùng `consumerRef` phải thành công. Mọi trường hợp còn lại phải trả `CHALLENGE_INVALID`. | M | [Thiết kế api §4 `/internal/challenges/{id}/verify`; data model `consumer_ref`]; F-PAY-03.FR-16, FR-17 |

**Giới hạn tần suất**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-ACC-01.FR-43 | Hệ thống phải giới hạn tần suất các thao tác đăng ký, đăng nhập, xác minh OTP, quên mật khẩu theo từng địa chỉ IP, từng thiết bị và từng SĐT với các ngưỡng `auth.ratelimit.*`; vượt ngưỡng thì trả `RATE_LIMITED` kèm `Retry-After`. | M | NFR-S-03; [Cần xác nhận Q7] |

### 3.3 Quy tắc nghiệp vụ

| ID | Quy tắc | Tham số cấu hình | Nguồn |
|---|---|---|---|
| F-ACC-01.BR-01 | SĐT hợp lệ khi có dạng `0` theo sau 9 chữ số, hoặc `+84` theo sau 9 chữ số. Hệ thống chuẩn hoá về E.164 (`0900000001` và `+84900000001` thành `+84900000001`) trước khi so sánh, tra cứu hay lưu. Hệ thống không kiểm tra đầu số nhà mạng. | — | [Thiết kế api contract `recipientPhone`; data model]; [Giả định] về đầu số; [Cần xác nhận Q15]; khoảng trắng: OQ-016 |
| F-ACC-01.BR-02 | Mật khẩu hợp lệ khi độ dài (tính theo ký tự Unicode) nằm trong [`auth.password.min_length`, `auth.password.max_length`] và không bằng chính SĐT của khách (dạng `0...` hoặc `+84...`). Hệ thống không áp quy tắc thành phần ký tự. | `auth.password.min_length` (chưa chốt), `auth.password.max_length` (128) | [Giả định]; [Cần xác nhận Q6] |
| F-ACC-01.BR-03 | Họ tên: bỏ khoảng trắng đầu cuối, gộp nhiều khoảng trắng liên tiếp thành một, độ dài sau chuẩn hoá từ 1 đến 128 ký tự, không chứa ký tự điều khiển. | — | [Thiết kế data model `full_name varchar(128)`]; [Giả định] về ký tự bị cấm; [Cần xác nhận Q9] |
| F-ACC-01.BR-04 | Thiết bị là *mới* khi `HMAC(dấu vân tay)` chưa có trong các thiết bị đang tin cậy của tài khoản. Thiết bị được tin cậy từ lúc khách nhập đúng OTP (hoặc đăng ký) cho đến khi bị thu hồi (FR-39) hoặc, nếu có cấu hình, quá `auth.device.trust_ttl`. | `auth.device.trust_ttl` (chưa chốt) | [Thiết kế data model identity.device]; [Cần xác nhận Q3] |
| F-ACC-01.BR-05 | Khi một yêu cầu đăng nhập vi phạm nhiều điều kiện, hệ thống trả mã lỗi của điều kiện đầu tiên theo thứ tự: (1) định dạng (`VALIDATION_ERROR`) → (2) giới hạn tần suất (`RATE_LIMITED`) → (3) đang khoá tạm (`ACCOUNT_TEMP_LOCKED`) → (4) SĐT/mật khẩu (`INVALID_CREDENTIALS`) → (5) trạng thái tài khoản (`ACCOUNT_LOCKED`) → (6) thiết bị mới (`CHALLENGE_REQUIRED`). Trạng thái tài khoản đứng sau mật khẩu để người không biết mật khẩu không dò được tài khoản nào đang bị khoá. | — | [Giả định] |
| F-ACC-01.BR-06 | Khi hoàn tất đăng ký hoặc đặt lại mật khẩu, hệ thống kiểm tra theo thứ tự: (1) định dạng và chính sách của mọi trường, gồm mật khẩu, họ tên, đồng ý (`VALIDATION_ERROR`, `CONSENT_REQUIRED`) → (2) challenge và OTP (`CHALLENGE_INVALID`) → (3) SĐT chưa có tài khoản (`PHONE_ALREADY_REGISTERED`). Bước (1) không được tiêu hao lượt nhập OTP và không được làm challenge mất hiệu lực. | — | [Giả định] |
| F-ACC-01.BR-07 | Bộ đếm sai mật khẩu tăng khi mật khẩu sai ở đăng nhập và ở đổi mật khẩu (FR-34); đặt về 0 khi mật khẩu đúng, khi đặt lại mật khẩu, và khi hết thời gian khoá tạm. Nhập sai OTP không tăng bộ đếm này (đã có giới hạn riêng ở FR-11). | `auth.login.max_failures`, `auth.login.lock_duration` | NFR-S-03; [Cần xác nhận Q7] |
| F-ACC-01.BR-08 | "Thu hồi mọi phiên" nghĩa là thu hồi mọi token family của tài khoản. "Thu hồi mọi phiên khác" giữ lại family của phiên đang thực hiện thao tác. | — | [Giả định] |
| F-ACC-01.BR-09 | Thông báo bảo mật (EV-04..EV-07) không được chứa mật khẩu, OTP, hay dấu vân tay thiết bị. Thông báo gửi tới SĐT hiện hành của tài khoản; riêng đổi SĐT gửi tới cả SĐT cũ và mới. | — | NFR-S-04; [Giả định] |
| F-ACC-01.BR-10 | Mỗi SĐT chỉ thuộc về một tài khoản, bất kể trạng thái tài khoản. | — | [Thiết kế data model]; [Cần xác nhận Q10] |

**Tham số cấu hình**

| Tên | Kiểu | Giá trị | Ghi chú |
|---|---|---|---|
| `auth.otp.ttl` | thời lượng | chưa chốt | Contract ví dụ dùng 3 phút |
| `auth.otp.max_attempts` | số nguyên | chưa chốt | Contract ví dụ dùng 5 |
| `auth.otp.resend_after` | thời lượng | chưa chốt | Contract ví dụ dùng 30 giây |
| `auth.otp.max_sends`, `auth.otp.send_window` | số nguyên, thời lượng | chưa chốt | Trần gửi cho cùng SĐT và mục đích |
| `auth.login.max_failures`, `auth.login.lock_duration` | số nguyên, thời lượng | chưa chốt | |
| `auth.access.ttl` | thời lượng | 5 phút | [Thiết kế §10] |
| `auth.refresh.ttl`, `auth.session.idle_timeout` | thời lượng | chưa chốt | |
| `auth.refresh.reuse_grace` | thời lượng | chưa chốt | `0` = nghiêm ngặt |
| `auth.password.min_length`, `auth.password.max_length` | số nguyên | chưa chốt; 128 | |
| `auth.terms.current_version` | chuỗi ≤ 16 ký tự | do Pháp chế/Product cung cấp | |
| `auth.cookie.same_site` | `Lax` hoặc `Strict` | chưa chốt | |
| `auth.device.trust_ttl` | thời lượng | không giới hạn (nếu không đặt) | |
| `auth.ratelimit.<thao tác>.<phạm vi>` | số lần / thời lượng | chưa chốt | Phạm vi: IP, thiết bị, SĐT |

Tất cả là cấu hình triển khai, không chỉnh qua CMS `[Giả định]`. Giá trị chưa chốt phải có trước go-live `[Cần xác nhận Q6, Q7]`.

### 3.4 Trạng thái & chuyển trạng thái

**Tài khoản (`customer.status`)**

| ID | Từ | Sự kiện / Điều kiện | Sang | Ghi chú |
|---|---|---|---|---|
| F-ACC-01.ST-01 | (chưa có) | Đăng ký hoàn tất (FR-03) | `ACTIVE` | Tier `0` |
| F-ACC-01.ST-02 | `ACTIVE` | Nhân viên khoá (F-BO-02) | `LOCKED` | FR-31, FR-18. Không phải khoá tạm do nhập sai mật khẩu |
| F-ACC-01.ST-03 | `LOCKED` | Nhân viên mở khoá (F-BO-02) | `ACTIVE` | |
| F-ACC-01.ST-04 | `ACTIVE`, `LOCKED` | Đóng tài khoản (F-ACC-03) | `CLOSED` | FR-31, FR-18 |
| F-ACC-01.ST-05 | `CLOSED` | bất kỳ | (bị cấm) | Trạng thái cuối |

Trạng thái `PENDING` có trong data model nhưng tính năng này không dùng `[Cần xác nhận Q14]`.

**Challenge**

| ID | Từ | Sự kiện / Điều kiện | Sang | Ghi chú |
|---|---|---|---|---|
| F-ACC-01.ST-06 | (chưa có) | Cấp challenge và gửi OTP thành công | `ISSUED` | FR-01, FR-20, FR-32, FR-41 |
| F-ACC-01.ST-07 | `ISSUED` | OTP đúng | `CONSUMED` | FR-09. Với challenge giao dịch, ghi `consumerRef` (FR-42) |
| F-ACC-01.ST-08 | `ISSUED` | Đến thời điểm hết hạn | `EXPIRED` | FR-09 |
| F-ACC-01.ST-09 | `ISSUED` | Nhập sai đủ `auth.otp.max_attempts` lần | `EXHAUSTED` | FR-11 |
| F-ACC-01.ST-10 | `ISSUED` | Cấp challenge mới cùng mục đích và đối tượng; hoặc không giao được OTP (FR-14) | `SUPERSEDED` | FR-12. Data model có cột `superseded_at` và `uq_challenge_active` bảo vệ bất biến này |
| F-ACC-01.ST-11 | `CONSUMED`, `EXPIRED`, `EXHAUSTED`, `SUPERSEDED` | bất kỳ | (bị cấm) | Ngoại lệ duy nhất: xác minh lại `CONSUMED` với cùng `consumerRef` (FR-42) vẫn thành công và không đổi trạng thái |

**Refresh token**

| ID | Từ | Sự kiện / Điều kiện | Sang | Ghi chú |
|---|---|---|---|---|
| F-ACC-01.ST-12 | (chưa có) | Đăng nhập hoặc làm mới thành công | `ACTIVE` | FR-24, FR-25 |
| F-ACC-01.ST-13 | `ACTIVE` | Được dùng để làm mới | `USED` | FR-25 |
| F-ACC-01.ST-14 | `ACTIVE`, `USED` | Đăng xuất, phát hiện dùng lại, đổi/đặt lại mật khẩu, khoá/đóng tài khoản, thu hồi thiết bị | `REVOKED` | Thu hồi theo family. FR-26, FR-28, FR-31, FR-33, FR-34, FR-36, FR-39 |
| F-ACC-01.ST-15 | `REVOKED` | bất kỳ | (bị cấm) | Trạng thái cuối |

### 3.5 Dữ liệu

| Trường | Kiểu | Bắt buộc | Ràng buộc / Validation | Ví dụ |
|---|---|---|---|---|
| phone | chuỗi | Có | BR-01; lưu E.164, tối đa 15 ký tự | `0900000001` → `+84900000001` |
| otp | chuỗi | Khi nhập mã | Đúng 6 chữ số (`^[0-9]{6}$`) | `123456` |
| challengeId | UUID | Khi dùng challenge | | |
| password | chuỗi | Khi đăng ký, đăng nhập, đặt lại, đổi | BR-02; không trim; không ghi log | |
| currentPassword, newPassword | chuỗi | Khi đổi mật khẩu | BR-02 cho `newPassword` | |
| fullName | chuỗi | Khi đăng ký | BR-03 | `Nguyễn Văn A` |
| deviceFingerprint | chuỗi | Khi đăng ký, đăng nhập | Độ dài 1–256 `[Giả định]`; chỉ lưu HMAC | |
| termsVersion | chuỗi | Khi đăng ký | ≤ 16 ký tự; phải bằng `auth.terms.current_version` | `2026-11` |
| newPhone | chuỗi | Khi đổi SĐT | BR-01 | |
| deviceId | UUID | Khi thu hồi thiết bị | Thuộc khách | |

Dữ liệu nhạy cảm: mật khẩu và OTP không được ghi log, không đưa lên Kafka, không trả về trong phản hồi. SĐT đầy đủ không xuất hiện trong log; hiển thị dạng che (`0900 *** 001`) ở mọi chỗ trả cho client trừ SĐT chính chủ tài khoản. Dấu vân tay thiết bị chỉ lưu dạng HMAC.

### 3.6 Đầu ra phụ: thông báo, sự kiện, audit

| ID | Sự kiện | Kênh / Đích | Nội dung / Trường | Điều kiện |
|---|---|---|---|---|
| F-ACC-01.EV-01 | Tài khoản được tạo | Kafka `identity.events` (`CustomerRegistered`), key = `customerId` | `customerId`, `phone`, `displayMasked`; `aggregateversion`. Không có mật khẩu, họ tên đầy đủ hay OTP | FR-05. Consumer: `wallet` (mở ví), `audit` |
| F-ACC-01.EV-02 | SĐT được đổi | Kafka `identity.events` (`CustomerUpdated`) | `customerId`, `phone`, `displayMasked`; `aggregateversion` | FR-36. Consumer: `wallet` (cập nhật danh bạ tra cứu người nhận), `audit` |
| F-ACC-01.EV-03 | Gửi OTP | `NotificationSender` (nội bộ, **không qua Kafka**) | Mã 6 chữ số, mục đích, thời hạn; kèm lời nhắc không chia sẻ mã | FR-01, FR-12, FR-20, FR-32, FR-35, FR-41 |
| F-ACC-01.EV-04 | SĐT đã đăng ký | Tin nhắn tới SĐT | "Số này đã có tài khoản. Hãy đăng nhập hoặc dùng Quên mật khẩu." Không chứa mã | FR-02 |
| F-ACC-01.EV-05 | Đăng nhập từ thiết bị mới | Thông báo tới khách (kênh theo Q11) | Nhãn thiết bị, thời điểm; hướng dẫn khoá/đổi mật khẩu nếu không phải mình | FR-22 |
| F-ACC-01.EV-06 | Mật khẩu đã đổi hoặc đặt lại | Thông báo tới khách | Thời điểm; hướng dẫn liên hệ hỗ trợ nếu không phải mình | FR-33, FR-34 |
| F-ACC-01.EV-07 | SĐT đã đổi | Thông báo tới SĐT cũ và SĐT mới | Thời điểm; SĐT đã che | FR-36 |
| F-ACC-01.EV-08 | Audit | `AuditRecorded` qua outbox | Ghi: đăng ký; đăng nhập thành công/thất bại (kèm lý do, IP, thiết bị); khoá tạm; đổi/đặt lại mật khẩu; đổi SĐT; thu hồi thiết bị; dùng lại refresh token. Không ghi mật khẩu/OTP | CC-AUDIT |
| F-ACC-01.EV-09 | Cảnh báo vận hành | Alert tới vận hành | Phát hiện dùng lại refresh token; tỷ lệ gửi OTP lỗi vượt ngưỡng; tăng đột biến đăng nhập thất bại | NFR-05, NFR-06 |

## 4. Giao diện ngoài

### 4.1 Giao diện người dùng
Thiết kế chi tiết nằm ở `docs/ui/features/F-ACC-01-*` (chưa viết, chạy `ui-ux-spec`). Các màn hình cần có:

| Màn hình | Mục đích | Hành vi đáng chú ý |
|---|---|---|
| Đăng ký (SĐT → OTP → mật khẩu, họ tên, đồng ý) | UC-01 | Bấm hai lần "Gửi mã" không tạo hai challenge cùng lúc; nút gửi lại bị khoá tới `resendAfter` |
| Đăng nhập | UC-02 | Cùng một thông điệp cho sai SĐT và sai mật khẩu |
| Nhập OTP thiết bị mới | UC-02 `A1` | Hiện nơi nhận đã che, đếm ngược hạn, số lần còn lại |
| Quên mật khẩu (SĐT → OTP → mật khẩu mới) | UC-04 | Thông điệp sau khi gửi SĐT không tiết lộ SĐT có tài khoản |
| Đổi mật khẩu | UC-05 | |
| Đổi SĐT | UC-06 | Phụ thuộc luồng khuôn mặt của F-ACC-02 (OQ-028) |
| Thiết bị / Đồng ý | UC-07 | |

### 4.2 API
Hợp đồng chi tiết (nguồn sự thật khi code): [identity/public.yaml](../../../source-code/contracts/openapi/identity/public.yaml), [identity/internal.yaml](../../../source-code/contracts/openapi/identity/internal.yaml), [notification/internal.yaml](../../../source-code/contracts/openapi/notification/internal.yaml), event [CustomerRegistered](../../../source-code/contracts/events/identity/CustomerRegistered/v1.json) và [CustomerUpdated](../../../source-code/contracts/events/identity/CustomerUpdated/v1.json). Bảng dưới chỉ tóm tắt các điểm có hành vi nghiệp vụ.

| Method | Path | Idempotency | Auth | Request chính | Response / Mã lỗi |
|---|---|---|---|---|---|
| POST | `/auth/register` | Không (không phải lệnh tiền); xem FR-12 | Không | `{phone}` | `201 {challengeId, ...}`; 400, 429, 503 `OTP_SEND_FAILED` |
| POST | `/auth/register/verify` | Không; xem FR-08 | Không | `{challengeId, otp, password, fullName, termsVersion, deviceFingerprint}` | `201` phiên; 400, 409 `PHONE_ALREADY_REGISTERED`, 422 `CHALLENGE_INVALID`/`CONSENT_REQUIRED` |
| POST | `/auth/login` | Không | Không | `{phone, password, deviceFingerprint}` | `200` phiên; `422 CHALLENGE_REQUIRED` kèm `challengeId`; 401 `INVALID_CREDENTIALS`; 403 `ACCOUNT_TEMP_LOCKED`/`ACCOUNT_LOCKED`; 429 |
| POST | `/auth/login/verify` | Không | Không | `{challengeId, otp, deviceFingerprint}` | `200` phiên; 422 `CHALLENGE_INVALID` |
| POST | `/auth/refresh` | Không | Không (BFF gửi refresh token lấy từ cookie) | `{refreshToken}` | `200` phiên mới; 401 `UNAUTHENTICATED` |
| POST | `/auth/logout` | Không | Khách | | `204` |
| POST | `/auth/password/forgot` | Không | Không | `{phone}` | `201 {challengeId, ...}` luôn có hình dạng như nhau (FR-32) |
| POST | `/auth/password/reset` | Không | Không | `{challengeId, otp, newPassword}` | `204`; 400, 422 `CHALLENGE_INVALID` |
| POST | `/me/password` | Không | Khách | `{currentPassword, newPassword}` | `204`; 400, 401 `INVALID_CREDENTIALS` |
| POST | `/me/phone` | Không | Khách | `{newPhone, faceChallengeId, otpChallengeId, otp}` | `204`; 403 `KYC_REQUIRED`, 409 `PHONE_ALREADY_REGISTERED`, 422 `CHALLENGE_INVALID` |
| GET, DELETE | `/me/devices`, `/me/devices/{id}` | DELETE idempotent theo `id` | Khách | | 404 `NOT_FOUND` |
| GET | `/me/consents` | — | Khách | | `200` |
| POST | `/challenges` | Không | Khách | `{purpose, factor?, payload}` với `purpose` ∈ `P2P`, `CHANGE_PHONE` | `201 Challenge`; 400, 403 `KYC_REQUIRED`, 429, 503 |
| POST | `/internal/challenges/{id}/verify` | Theo `(id, consumerRef)` | Nội bộ | `{otp?, payload, consumerRef}` | `200 {factor}` / `422 CHALLENGE_INVALID` |

Hợp đồng bổ sung những điểm SRS chưa nói, đều không thêm nghiệp vụ:
- `AuthSession` có `accessTokenExpiresAt`, `refreshTokenExpiresAt` để BFF lên lịch làm mới và đặt hạn cookie (FR-27, FR-29).
- `CONSENT_REQUIRED` mang `params.currentTermsVersion`; `ACCOUNT_TEMP_LOCKED` mang `params.retryAfterSeconds`; `CHALLENGE_REQUIRED` của đăng nhập mang dữ liệu challenge trong `params`.
- Xác minh challenge nhận `payload` thay vì `payloadHash` (để `payment` không phải biết cách chuẩn hoá) và trả `factor` (OQ-049).
- Chưa có hợp đồng: `GET /me` (OQ-048), thông báo bảo mật EV-04..EV-07 (OQ-051), nội dung điều khoản (OQ-050), rút lại đồng ý (OQ-041).

### 4.3 Hệ thống ngoài / provider
| Hệ thống | Thao tác | Kết quả có thể | Xử lý từng kết quả |
|---|---|---|---|
| `NotificationSender` (qua `notification`) | Gửi OTP | Chấp nhận / Từ chối / Quá thời gian chờ | Chấp nhận: challenge `ISSUED`. Từ chối hoặc quá thời gian chờ: `OTP_SEND_FAILED`, challenge `SUPERSEDED` (FR-14). Khi quá thời gian chờ, tin có thể vẫn đã đến tay khách; khách xin challenge mới và mã cũ không dùng được nữa |
| `NotificationSender` | Gửi cảnh báo, thông báo EV-04..07 | Chấp nhận / Lỗi | Lỗi không làm hỏng thao tác chính (đăng nhập, đổi mật khẩu đã thành công); hệ thống ghi nhận lỗi và thử lại theo chính sách của F-NTF-01 `[Giả định]` |
| `kyc` (face verification) | So khuôn mặt cho `CHANGE_PHONE` | Đạt / Không đạt / Lỗi | Do F-ACC-02 quy định; ở đây chỉ coi `Đạt` là điều kiện của FR-35 |

## 5. Yêu cầu phi chức năng riêng của tính năng

| ID | Loại | Yêu cầu (đo được) | Nguồn |
|---|---|---|---|
| F-ACC-01.NFR-01 | Hiệu năng | p99 của `POST /auth/login` (không tính nhánh cần challenge) < 1 s ở 50 TPS | NFR-P-01; [Giả định]: 50 TPS là 10% của 500 TPS |
| F-ACC-01.NFR-02 | Bảo mật | Mật khẩu băm Argon2id; mật khẩu và OTP không xuất hiện trong log, phản hồi hay Kafka | NFR-S-04; [Thiết kế §10] |
| F-ACC-01.NFR-03 | Bảo mật | Chênh lệch trung vị thời gian phản hồi của `POST /auth/login` giữa SĐT không tồn tại và SĐT tồn tại với mật khẩu sai < 50 ms, để không dò được tài khoản qua thời gian | NFR-S-03; [Giả định] về ngưỡng 50 ms |
| F-ACC-01.NFR-04 | Bảo mật | Mọi phản hồi HTML của `web` có tiêu đề `Content-Security-Policy`; mọi phản hồi có `Strict-Transport-Security` | NFR-S-02; NFR-S-01 |
| F-ACC-01.NFR-05 | Khả dụng | `identity` chạy tối thiểu 2 bản; tỷ lệ gửi OTP lỗi vượt `auth.alert.otp_failure_ratio` trong 5 phút thì phát cảnh báo P1 (EV-09) | [Thiết kế R6]; [Cần xác nhận Q7] |
| F-ACC-01.NFR-06 | Quan sát | Có metric: đăng nhập thành công/thất bại theo lý do, số khoá tạm, OTP gửi/lỗi theo mục đích, số lần phát hiện dùng lại refresh token | NFR-O-02 |
| F-ACC-01.NFR-07 | Dữ liệu cá nhân | Bản ghi đồng ý chỉ thêm; dấu vân tay thiết bị chỉ lưu HMAC; SĐT trong log ở dạng đã che | NFR-L-03; NFR-L-07 |

Mức kiểm chứng: NFR-01 bằng load test; NFR-02 bằng quét log/Kafka trong test (AC-52); NFR-03 bằng test đo thời gian; NFR-04 bằng test phản hồi HTTP; NFR-05, NFR-06 bằng giám sát; NFR-07 bằng AC-04 và kiểm tra schema.

## 6. Danh mục lỗi & thông điệp

Mã lỗi từ `INVALID_CREDENTIALS` đến `OTP_SEND_FAILED` là **mới**, cần thêm vào `common.yaml` khi chạy `api-contract`.

| Mã lỗi | Điều kiện | HTTP | Thông điệp cho người dùng (vi) | Thử lại được? |
|---|---|---|---|---|
| `VALIDATION_ERROR` | Sai định dạng SĐT/OTP/mật khẩu/họ tên; mật khẩu không đạt chính sách | 400 | "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại." (kèm lỗi theo từng trường) | Có |
| `INVALID_CREDENTIALS` | SĐT không có tài khoản, mật khẩu sai, hoặc tài khoản `CLOSED`; mật khẩu hiện tại sai khi đổi | 401 | "Số điện thoại hoặc mật khẩu không đúng." | Có |
| `ACCOUNT_TEMP_LOCKED` | Đang trong thời gian khoá tạm (FR-17) | 403, `params.retryAfterSeconds` | "Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau {mm:ss}." | Có, sau thời gian khoá |
| `ACCOUNT_LOCKED` | Tài khoản `LOCKED` | 403 | "Tài khoản của bạn đang bị khoá. Vui lòng liên hệ hỗ trợ." | Không |
| `CHALLENGE_REQUIRED` | Đăng nhập từ thiết bị mới; hoặc xin OTP đổi SĐT khi khuôn mặt chưa đạt (FR-35) | 422; khi đăng nhập kèm dữ liệu challenge | "Bạn đang đăng nhập trên thiết bị mới. Nhập mã xác thực gửi tới {destinationMasked}." | Có, với mã |
| `CHALLENGE_INVALID` | OTP sai, hết hạn, hết lượt, đã dùng, sai mục đích/nội dung/`consumerRef`; khuôn mặt không đạt | 422, `params.attemptsRemaining` | "Mã xác thực không đúng hoặc đã hết hạn." | Có, nếu còn lượt hoặc xin mã mới |
| `CONSENT_REQUIRED` | Chưa đồng ý điều khoản hiện hành | 422 | "Bạn cần đồng ý với điều khoản sử dụng để tiếp tục." | Có |
| `PHONE_ALREADY_REGISTERED` | Hoàn tất đăng ký hoặc đổi SĐT với SĐT đã thuộc tài khoản khác | 409 | "Số điện thoại này đã được đăng ký." | Không |
| `OTP_SEND_FAILED` | Không bàn giao được OTP cho dịch vụ thông báo | 503 | "Chưa gửi được mã xác thực. Vui lòng thử lại sau ít phút." | Có |
| `RATE_LIMITED` | Vượt giới hạn tần suất, vượt trần gửi OTP, hoặc gửi lại trước `auth.otp.resend_after` | 429, `Retry-After` | "Bạn thao tác quá nhanh. Vui lòng thử lại sau {s} giây." | Có, sau `Retry-After` |
| `UNAUTHENTICATED` | Thiếu, sai, đã thu hồi token; refresh token đã dùng/hết hạn | 401 | "Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại." | Có, sau đăng nhập |
| `TOKEN_EXPIRED` | Access token hết hạn | 401 | (client tự làm mới phiên, không hiện) | Có |
| `FORBIDDEN` | Thiếu hoặc sai CSRF token (do BFF kiểm và trả, không phải `identity`) | 403 | "Không thể thực hiện yêu cầu. Vui lòng tải lại trang." | Có, sau tải lại |
| `KYC_REQUIRED` | Đổi SĐT khi chưa có dữ liệu khuôn mặt | 403 | "Bạn cần hoàn tất xác thực danh tính (eKYC) trước khi đổi số điện thoại." | Không, tới khi eKYC xong |
| `NOT_FOUND` | Thu hồi thiết bị không thuộc khách hoặc không tồn tại | 404 | "Không tìm thấy thiết bị." | Không |

Thông điệp không phân biệt SĐT có hay không có tài khoản ở đăng nhập, quên mật khẩu và bước đầu đăng ký (FR-02, FR-16, FR-32). Ngoại lệ có chủ đích: `PHONE_ALREADY_REGISTERED` chỉ đến tay người đã chứng minh sở hữu SĐT bằng OTP (đăng ký) hoặc đã đăng nhập và qua khuôn mặt (đổi SĐT).

## 7. Tiêu chí chấp nhận

Số liệu trong các AC là cấu hình **minh hoạ**: `auth.otp.ttl` = 3 phút, `auth.otp.max_attempts` = 5, `auth.otp.resend_after` = 30 giây, `auth.otp.max_sends` = 5 trong `auth.otp.send_window` = 1 giờ, `auth.login.max_failures` = 5, `auth.login.lock_duration` = 15 phút, `auth.access.ttl` = 5 phút (đã chốt), `auth.refresh.reuse_grace` = 0, `auth.password.min_length` = 8, `auth.password.max_length` = 128. Không phải giá trị đã chốt (trừ `auth.access.ttl`).

**Đăng ký**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-01 | FR-01, FR-09, BR-01, ST-06, EV-03 | Chưa có tài khoản cho `+84900000001` | Khách gửi SĐT `0900000001` để đăng ký | `201` kèm `challengeId`, `destinationMasked`, `expiresAt` = bây giờ + 3 phút, `resendAfter` = bây giờ + 30 giây, `attemptsRemaining` = 5. `NotificationSender` nhận đúng 1 OTP gồm 6 chữ số gửi tới `+84900000001`. Chưa có tài khoản nào được tạo |
| F-ACC-01.AC-02 | BR-01 | | Gửi `0900000001`, `+84900000001`, `090000001` (9 chữ số), `+8490000000a`, chuỗi rỗng | Hai SĐT đầu cùng chuẩn hoá thành `+84900000001` và được chấp nhận; ba SĐT sau bị từ chối `VALIDATION_ERROR`, không gửi OTP |
| F-ACC-01.AC-03 | FR-02, EV-04 | Đã có tài khoản `+84900000001` | Khách gửi SĐT này để đăng ký | Phản hồi cùng mã trạng thái và cùng tập trường với AC-01; không có OTP nào được gửi; EV-04 được gửi tới `+84900000001`. Hoàn tất đăng ký với `challengeId` này (kèm mã bất kỳ) trả `CHALLENGE_INVALID` |
| F-ACC-01.AC-04 | FR-03, FR-04, FR-05, FR-07, ST-01, EV-01 | Challenge `REGISTER` của `+84900000001` còn hạn, OTP `123456` | Khách hoàn tất với OTP `123456`, mật khẩu hợp lệ, họ tên `Nguyễn Văn A`, đồng ý `TERMS` bản hiện hành | `201` kèm phiên. Có đúng 1 tài khoản `ACTIVE`, tier `0`; mật khẩu lưu dạng Argon2id; 1 bản ghi đồng ý (`TERMS`, phiên bản, granted = true, IP, user agent); thiết bị hiện tại là thiết bị đã biết; đúng 1 event `CustomerRegistered` có `customerId`, `phone`, `displayMasked` và không có mật khẩu/họ tên đầy đủ |
| F-ACC-01.AC-05 | FR-04, BR-06 | Challenge `REGISTER` hợp lệ, `attemptsRemaining` = 5 | Khách hoàn tất với OTP đúng nhưng không đồng ý điều khoản | `422 CONSENT_REQUIRED`; không có tài khoản; challenge còn `ISSUED`, `attemptsRemaining` vẫn 5 |
| F-ACC-01.AC-06 | BR-06, BR-02 | Challenge hợp lệ, `attemptsRemaining` = 5 | Khách gửi OTP đúng và mật khẩu 7 ký tự | `400 VALIDATION_ERROR` có lỗi ở trường `password`; challenge còn `ISSUED`, `attemptsRemaining` = 5; gửi lại với mật khẩu hợp lệ và cùng OTP thì thành công |
| F-ACC-01.AC-07 | FR-06, BR-10 | Một challenge `REGISTER` hợp lệ của `+84900000001`, chưa có tài khoản | Hai yêu cầu hoàn tất với cùng challenge và OTP đúng chạy đồng thời | Đúng 1 yêu cầu `201`; yêu cầu còn lại `409 PHONE_ALREADY_REGISTERED` hoặc `422 CHALLENGE_INVALID`; đúng 1 tài khoản, 1 event `CustomerRegistered` |
| F-ACC-01.AC-08 | FR-05 | Challenge hợp lệ; việc ghi event vào outbox bị ép lỗi (cần cơ chế ép lỗi, xem OQ-024) | Khách hoàn tất đăng ký | Yêu cầu thất bại; không có tài khoản, đồng ý, thiết bị hay event nào được ghi |
| F-ACC-01.AC-09 | FR-07 | Khách vừa đăng ký xong trên thiết bị D | Khách đăng xuất rồi đăng nhập lại bằng SĐT, mật khẩu từ thiết bị D | `200` kèm phiên, không có `CHALLENGE_REQUIRED` |
| F-ACC-01.AC-10 | FR-08, ST-11 | AC-04 đã thành công với challenge C | Gửi lại đúng yêu cầu hoàn tất với C | `422 CHALLENGE_INVALID`; vẫn đúng 1 tài khoản; không cấp phiên mới |

**OTP và challenge**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-11 | FR-09, ST-08 | Challenge cấp lúc T, `expiresAt` = T + 3 phút | Nhập OTP đúng tại T + 2:59 / tại T + 3:00 | Tại T + 2:59: được chấp nhận. Tại T + 3:00: `CHALLENGE_INVALID` (ở hai lần thử độc lập, mỗi lần một challenge mới) |
| F-ACC-01.AC-12 | FR-09, ST-07, ST-11 | Challenge `LOGIN_NEW_DEVICE` đã được nhập đúng OTP | Nhập lại OTP đó lần nữa | `CHALLENGE_INVALID` |
| F-ACC-01.AC-13 | FR-10 | Challenge `REGISTER` cho `+84900000001` | Dùng `challengeId` đó cho đăng nhập thiết bị mới; dùng cho hoàn tất đăng ký với SĐT `+84900000002` | Cả hai bị `CHALLENGE_INVALID` |
| F-ACC-01.AC-14 | FR-11, ST-09 | Challenge hợp lệ, OTP đúng là `123456` | Nhập sai 5 lần liên tiếp, rồi nhập `123456` | Năm lần sai trả `attemptsRemaining` = 4, 3, 2, 1, 0; lần thứ sáu với mã đúng vẫn `CHALLENGE_INVALID` |
| F-ACC-01.AC-15 | FR-12, ST-10 | Challenge C1 cấp lúc T | Xin gửi lại lúc T + 29 giây, rồi lúc T + 30 giây | Lúc T + 29 giây: `429 RATE_LIMITED` kèm `Retry-After` = 1. Lúc T + 30 giây: challenge C2 mới được cấp; nhập OTP đúng của C1 trả `CHALLENGE_INVALID`, OTP của C2 được chấp nhận |
| F-ACC-01.AC-16 | FR-13 | Đã gửi 5 OTP `REGISTER` cho `+84900000001` trong 1 giờ (mỗi lần cách nhau đủ 30 giây) | Gửi lần thứ 6 trong cùng giờ | `429 RATE_LIMITED`; không có OTP thứ 6 |
| F-ACC-01.AC-17 | FR-14 | `notification` trả lỗi hoặc quá thời gian chờ | Khách gửi SĐT để đăng ký | `503 OTP_SEND_FAILED`; challenge vừa tạo không dùng được; số lần gửi tính cho FR-13 không tăng |

**Đăng nhập**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-18 | FR-15, FR-19, FR-23 | Tài khoản `ACTIVE`; thiết bị D là thiết bị đã biết, `last_seen_at` = T0 | Khách đăng nhập đúng SĐT, mật khẩu từ D lúc T1 | `200` kèm phiên, không có OTP nào được gửi; `last_seen_at` = T1; dấu vân tay chỉ có dạng HMAC |
| F-ACC-01.AC-19 | FR-16 | Có tài khoản `+84900000001`; không có `+84900000009` | Đăng nhập `+84900000001` với mật khẩu sai; đăng nhập `+84900000009` | Hai phản hồi cùng `401`, cùng `code` = `INVALID_CREDENTIALS`, cùng `title`, cùng `detail`; chỉ `traceId` khác |
| F-ACC-01.AC-20 | FR-17, BR-07 | Tài khoản `ACTIVE`, bộ đếm sai = 0 | Đăng nhập sai mật khẩu 5 lần, rồi lần thứ sáu với mật khẩu đúng; rồi lần nữa sau 15 phút | Lần 1–5: `INVALID_CREDENTIALS`. Lần 6: `403 ACCOUNT_TEMP_LOCKED` với `retryAfterSeconds` ≤ 900, bộ đếm không tăng. Sau 15 phút: `200`; bộ đếm = 0 |
| F-ACC-01.AC-21 | BR-07 | Bộ đếm sai = 0 | Sai 4 lần, đúng 1 lần, rồi sai 4 lần | Không lần nào bị `ACCOUNT_TEMP_LOCKED` (bộ đếm đã về 0 sau lần đúng) |
| F-ACC-01.AC-22 | FR-18, ST-05 | Tài khoản A `LOCKED`; tài khoản B `CLOSED` | Đăng nhập A với mật khẩu đúng; đăng nhập B với mật khẩu đúng | A: `403 ACCOUNT_LOCKED`, không có token. B: `401 INVALID_CREDENTIALS`, cùng phản hồi với SĐT không tồn tại |
| F-ACC-01.AC-23 | FR-20, BR-04, ST-06 | Tài khoản `ACTIVE`; thiết bị E chưa có trong danh sách | Đăng nhập từ E với mật khẩu đúng | `422 CHALLENGE_REQUIRED` kèm `challengeId`; 1 OTP gửi tới SĐT của tài khoản; không có token |
| F-ACC-01.AC-24 | FR-21, FR-22, FR-23, BR-09, EV-05 | AC-23 đã xảy ra | Khách nhập OTP đúng | `200` kèm phiên; E trở thành thiết bị đã biết (chỉ HMAC); đúng 1 cảnh báo EV-05 gửi tới khách, không chứa mật khẩu/OTP; lần đăng nhập kế tiếp từ E không đòi OTP |
| F-ACC-01.AC-25 | FR-20 | Tài khoản `ACTIVE`; thiết bị E mới | Đăng nhập từ E với mật khẩu sai | `401 INVALID_CREDENTIALS`; không có OTP nào được gửi; bộ đếm sai tăng 1 |
| F-ACC-01.AC-26 | BR-05 | A `ACTIVE` đang khoá tạm; B `LOCKED`; thiết bị mới | (a) SĐT sai định dạng và mật khẩu sai; (b) A bị khoá tạm, từ thiết bị mới, mật khẩu đúng; (c) B với mật khẩu sai; (d) B với mật khẩu đúng | (a) `VALIDATION_ERROR`. (b) `ACCOUNT_TEMP_LOCKED`, không `CHALLENGE_REQUIRED`. (c) `INVALID_CREDENTIALS`, không `ACCOUNT_LOCKED`. (d) `ACCOUNT_LOCKED` |

**Phiên**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-27 | FR-24 | Khách vừa đăng nhập lúc T | Gọi API với access token lúc T + 4:59 / T + 5:00 | Lúc T + 4:59 được chấp nhận; lúc T + 5:00 trả `401 TOKEN_EXPIRED`. Chữ ký ES256 kiểm được bằng khoá công khai trong JWKS |
| F-ACC-01.AC-28 | FR-25, ST-12, ST-13 | Refresh token R1 | Làm mới bằng R1 | Nhận R2 và access token mới; R1 thành `USED` |
| F-ACC-01.AC-29 | FR-26, ST-14 | Sau AC-28, `reuse_grace` = 0 | Dùng lại R1 để làm mới | `401 UNAUTHENTICATED`; R2 cũng bị thu hồi (làm mới bằng R2 trả `UNAUTHENTICATED`); có cảnh báo EV-09 |
| F-ACC-01.AC-30 | FR-27 | `auth.session.idle_timeout` = 30 phút, `auth.refresh.ttl` = 30 ngày | (a) Khách không có hoạt động 30 phút rồi làm mới; (b) khách hoạt động liên tục, làm mới ở ngày thứ 30 và 1 giây | (a) `UNAUTHENTICATED`. (b) `UNAUTHENTICATED` (hết hạn tuyệt đối) |
| F-ACC-01.AC-31 | FR-28, ST-14, ST-15 | Khách đăng nhập hai thiết bị D1, D2 | D1 đăng xuất | Làm mới bằng token của D1 trả `UNAUTHENTICATED`; token của D2 vẫn làm mới được |
| F-ACC-01.AC-32 | FR-29 | Khách đăng nhập từ trình duyệt | Xem phản hồi đăng nhập | `Set-Cookie` có `HttpOnly`, `Secure`, `SameSite` = giá trị cấu hình; body gửi tới trình duyệt không chứa access/refresh token; token không nằm trong URL |
| F-ACC-01.AC-33 | FR-30 | Khách đã đăng nhập | Gửi `POST` đổi mật khẩu không kèm CSRF token; kèm CSRF token sai; kèm CSRF token đúng | Hai lần đầu `403 FORBIDDEN`, mật khẩu không đổi. Lần ba được xử lý |
| F-ACC-01.AC-34 | FR-31, ST-02, ST-04 | Khách có hai phiên; refresh token ở cả hai | Nhân viên khoá tài khoản (`LOCKED`) | Làm mới ở cả hai phiên trả `UNAUTHENTICATED`; access token đang có hết hiệu lực không quá 5 phút sau; đăng nhập mới trả `ACCOUNT_LOCKED`. Tương tự khi tài khoản `CLOSED` |
| F-ACC-01.AC-53 | FR-18, ST-03 | Tài khoản A đang `LOCKED` (AC-22); nhân viên mở khoá (F-BO-02), A về `ACTIVE` | A đăng nhập đúng SĐT, mật khẩu từ thiết bị đã biết | `200` kèm phiên. Refresh token đã bị thu hồi lúc khoá (AC-34) vẫn không dùng được lại |

**Mật khẩu**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-35 | FR-32 | Có tài khoản `ACTIVE` `+84900000001`; không có `+84900000009` | Quên mật khẩu với từng SĐT | Hai phản hồi cùng mã trạng thái và cùng tập trường (chỉ `challengeId` và thời điểm khác); chỉ `+84900000001` nhận OTP |
| F-ACC-01.AC-36 | FR-33, BR-08, EV-06 | Khách có hai phiên; bộ đếm sai = 3; challenge `RESET_PASSWORD` hợp lệ | Nhập OTP đúng và mật khẩu mới hợp lệ | Mật khẩu cũ không còn đăng nhập được; mật khẩu mới đăng nhập được (qua OTP nếu thiết bị mới); cả hai refresh token bị thu hồi; bộ đếm sai = 0; EV-06 gửi 1 lần |
| F-ACC-01.AC-37 | FR-33, BR-06 | Challenge `RESET_PASSWORD` hợp lệ, `attemptsRemaining` = 5 | Gửi OTP đúng kèm mật khẩu mới 7 ký tự | `400 VALIDATION_ERROR`; `attemptsRemaining` vẫn 5; mật khẩu cũ giữ nguyên |
| F-ACC-01.AC-38 | FR-34, BR-07, BR-08, EV-06 | Khách đăng nhập ở D1 (phiên hiện tại) và D2 | (a) Đổi với mật khẩu hiện tại sai; (b) mật khẩu mới trùng mật khẩu hiện tại; (c) đổi hợp lệ | (a) `401 INVALID_CREDENTIALS`, bộ đếm sai tăng 1. (b) `400 VALIDATION_ERROR`. (c) Thành công; phiên D2 bị thu hồi, D1 giữ; EV-06 gửi 1 lần |
| F-ACC-01.AC-39 | BR-02 | `min_length` = 8, `max_length` = 128; SĐT khách `0900000001` | Đặt mật khẩu dài 7 / 8 / 128 / 129 ký tự; đặt mật khẩu `0900000001` | 7: từ chối. 8: chấp nhận. 128: chấp nhận. 129: từ chối. `0900000001`: từ chối |
| F-ACC-01.AC-40 | BR-03 | | Đăng ký với họ tên `  Nguyễn   Văn A ` / rỗng / 129 ký tự / chứa ký tự xuống dòng | Chuẩn hoá thành `Nguyễn Văn A` và chấp nhận / `VALIDATION_ERROR` / `VALIDATION_ERROR` / `VALIDATION_ERROR` |

**Số điện thoại**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-41 | FR-35, FR-36, BR-08, EV-02, EV-07 | Khách có dữ liệu khuôn mặt, hai phiên; `+84900000003` chưa có tài khoản | Khách xác thực khuôn mặt đạt, rồi nhập OTP gửi tới `+84900000003` | SĐT tài khoản là `+84900000003`; đăng nhập bằng SĐT cũ trả `INVALID_CREDENTIALS`; bằng SĐT mới được; phiên khác bị thu hồi; 1 event `CustomerUpdated`; thông báo EV-07 gửi tới cả SĐT cũ và mới |
| F-ACC-01.AC-42 | FR-36, BR-10 | `+84900000004` đã thuộc tài khoản khác | Khách đổi SĐT sang `+84900000004` | `409 PHONE_ALREADY_REGISTERED`; SĐT không đổi; không có event |
| F-ACC-01.AC-43 | FR-35 | Khách có dữ liệu khuôn mặt | Khuôn mặt không đạt | `422 CHALLENGE_INVALID`; không gửi OTP tới SĐT mới; SĐT không đổi |
| F-ACC-01.AC-44 | FR-37 | Khách tier `0`, chưa có dữ liệu khuôn mặt | Khách yêu cầu đổi SĐT | `403 KYC_REQUIRED`; SĐT không đổi |

**Thiết bị và đồng ý**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-45 | FR-38 | Khách có 2 thiết bị, đang dùng D1 | Xem danh sách thiết bị | 2 mục, mỗi mục có mã, nhãn, `lastSeenAt`; D1 được đánh dấu hiện tại; không có dấu vân tay hay HMAC trong phản hồi |
| F-ACC-01.AC-46 | FR-39, ST-14 | Khách có D1 (đang dùng) và D2; thiết bị Z thuộc khách khác | Thu hồi D2; thu hồi Z | D2: refresh token của D2 bị thu hồi; lần đăng nhập sau từ D2 trả `CHALLENGE_REQUIRED`. Z: `404 NOT_FOUND`, Z không đổi |
| F-ACC-01.AC-47 | FR-40, FR-04 | Khách có hai bản ghi đồng ý `TERMS`: phiên bản `2026-01` (granted) và `2026-11` (granted) | Xem đồng ý | Một mục `TERMS` với phiên bản `2026-11` |

**Challenge giao dịch**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-48 | FR-41, EV-03 | Khách A đã đăng nhập, SĐT đăng ký `+84900000001` | A xin challenge `P2P` với `payload` có `recipientPhone` `+84900000002`, `amount` 3.000.000 | `201`; OTP gửi tới `+84900000001` (không phải `+84900000002`); challenge gắn hash của payload |
| F-ACC-01.AC-49 | FR-42, ST-07, ST-11 | Challenge C cấp cho payload P; OTP đúng | (a) Xác minh với `consumerRef` K1 và hash P; (b) xác minh lại với K1; (c) xác minh với `consumerRef` K2; (d) xác minh với hash payload khác | (a) `200`. (b) `200`, không cần OTP mới. (c) `422 CHALLENGE_INVALID`. (d) `422 CHALLENGE_INVALID` |

**Giới hạn tần suất, audit, bảo mật dữ liệu**

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-ACC-01.AC-50 | FR-43 | Ngưỡng đăng nhập theo IP: 20 lần/phút | Một IP gửi 21 yêu cầu đăng nhập trong 1 phút | Yêu cầu thứ 21 trả `429 RATE_LIMITED` kèm `Retry-After`; không có thay đổi bộ đếm sai của tài khoản nào |
| F-ACC-01.AC-51 | EV-08, EV-09 | | Thực hiện: đăng ký; đăng nhập thành công; đăng nhập sai; khoá tạm; đổi mật khẩu; đổi SĐT; thu hồi thiết bị; dùng lại refresh token | Mỗi thao tác có đúng 1 bản ghi audit (kèm IP, thiết bị, lý do khi thất bại); không bản ghi nào chứa mật khẩu, OTP hay dấu vân tay thô; dùng lại refresh token sinh thêm cảnh báo EV-09 |
| F-ACC-01.AC-52 | NFR-02 | Chạy toàn bộ các AC trên với log ở mức DEBUG và theo dõi topic Kafka | Quét log, phản hồi và event | Không tìm thấy chuỗi mật khẩu, OTP đã dùng, hay SĐT đầy đủ trong log |

## 8. Ma trận truy vết

| Yêu cầu nguồn | FR | BR | AC | Ghi chú |
|---|---|---|---|---|
| FR-ID-01 | FR-01..FR-14 | BR-01, 02, 03, 06, 10 | AC-01..AC-17 | |
| FR-ID-05 | FR-15..FR-23, FR-38, FR-39 | BR-04, 05, 07 | AC-18..AC-26, AC-45, AC-46 | Q1 chưa chốt việc 2FA mỗi lần hay chỉ thiết bị mới |
| FR-ID-06 | FR-32..FR-37 | BR-02, 06, 08 | AC-35..AC-44 | Đổi SĐT phụ thuộc F-ACC-02 (khuôn mặt) |
| NFR-S-02 | FR-27, FR-29, FR-30 | — | AC-30, AC-32, AC-33 | NFR-04 kiểm bằng test phản hồi HTTP |
| NFR-S-03 | FR-11, FR-13, FR-17, FR-43 | BR-07 | AC-14, AC-16, AC-20, AC-21, AC-50 | NFR-03 kiểm bằng test thời gian |
| NFR-S-04 | — | BR-09 | AC-52 | NFR-02 |
| NFR-L-03 | FR-04, FR-23, FR-40 | — | AC-04, AC-47 | NFR-07 |
| FR-ID-04 | FR-03 (tier mặc định `0`) | — | AC-04 | Cấp cao hơn do F-ACC-02 |
| FR-P2P-04, NFR-L-04 (cơ chế challenge) | FR-41, FR-42 | — | AC-48, AC-49 | Ngưỡng cần xác thực thuộc F-PAY-03 |
| Từ thiết kế (không có trong requirements) | FR-24..FR-26, FR-28, FR-31 | — | AC-27..AC-29, AC-31, AC-34 | Phiên: [Thiết kế §10] |
| NFR-S-01 | — | — | — | Mã hoá khi truyền/lưu là nền tảng chung (CC-SEC), kiểm ở tầng hạ tầng; riêng HSTS ở NFR-04 |
| NFR-P-01, NFR-O-02 | — | — | — | NFR-01, NFR-06: load test, giám sát (không ép thành AC) |

Phủ ID: ST-01 (AC-04), ST-02/04 (AC-34), ST-03 (AC-53), ST-05 (AC-22), ST-06 (AC-01, AC-23), ST-07 (AC-12, AC-49), ST-08 (AC-11), ST-09 (AC-14), ST-10 (AC-15), ST-11 (AC-10, AC-12, AC-49), ST-12..ST-14 (AC-28, AC-29), ST-15 (AC-31). EV-01 (AC-04), EV-02 (AC-41), EV-03 (AC-01, AC-48), EV-04 (AC-03), EV-05 (AC-24), EV-06 (AC-36, AC-38), EV-07 (AC-41), EV-08 (AC-51), EV-09 (AC-29, AC-51).

Ghi chú: thao tác khoá/mở khoá (ST-02, ST-03) do F-BO-02 thực hiện; AC-34 và AC-53 chỉ kiểm hệ quả lên đăng nhập và phiên, cần test tích hợp liên tính năng khi F-BO-02 có spec.

## 9. Câu hỏi mở & giả định cần xác nhận

Danh sách chung có ID `OQ-` ở [open-questions.md](../../open-questions.md).

| # | Câu hỏi / Giả định | Ảnh hưởng (ID) | Ai quyết định |
|---|---|---|---|
| Q1 | ⚖️ FR-ID-05 ghi "mật khẩu + OTP/2FA". Design chọn OTP chỉ cho thiết bị mới. Quy định NHNN có bắt buộc xác thực hai yếu tố mỗi lần đăng nhập ví không? (OQ-030) | FR-19..FR-21, UC-02, AC-18 | Pháp chế / Rủi ro |
| Q2 | Có để lộ SĐT đã đăng ký không? Đề xuất của spec: không lộ ở đăng ký và quên mật khẩu (FR-02, FR-32), kèm tin nhắn tới SĐT đó. Đổi lại, người dùng thật không thấy lý do vì sao không nhận mã. (OQ-031) | FR-02, FR-32, AC-03, AC-35 | Product / Bảo mật |
| Q3 | Web nhận diện thiết bị bằng gì: vân tay trình duyệt (không ổn định, dễ giả) hay mã thiết bị lưu trong cookie (xoá cookie thì thành thiết bị mới)? Thiết bị tin cậy có hết hạn không (`auth.device.trust_ttl`)? (OQ-032) | BR-04, FR-23, FR-19..21 | Kiến trúc / Bảo mật |
| Q4 | Khách chưa eKYC (tier `0`) không có ảnh mặt để so, nên FR-37 chặn đổi SĐT. Họ đổi SĐT bằng cách nào (ví dụ đến quầy, qua CSKH, hay eKYC trước)? (OQ-033) | FR-37, AC-44, UC-06 | Rủi ro / Product |
| Q5 | Quên mật khẩu chỉ cần OTP tới SĐT. SĐT có thể bị chiếm (SIM swap). Khách đã eKYC có phải xác thực khuôn mặt khi đặt lại mật khẩu không? (OQ-034) | FR-33, UC-04 | Rủi ro |
| Q6 | Chính sách mật khẩu: độ dài tối thiểu, có quy tắc thành phần ký tự không, có chặn danh sách mật khẩu phổ biến không. (OQ-035) | BR-02, AC-39 | Bảo mật / Product |
| Q7 | Giá trị tham số: `auth.otp.*`, `auth.login.max_failures`, `auth.login.lock_duration`, `auth.refresh.ttl`, `auth.session.idle_timeout`, ngưỡng rate limit, `auth.alert.otp_failure_ratio`. Câu hỏi riêng: khoá tạm theo tài khoản cho phép kẻ tấn công khoá tài khoản nạn nhân; có nên khoá theo cặp (tài khoản, thiết bị lạ) không? (OQ-036) | FR-13, FR-17, FR-27, FR-43, NFR-05 | Bảo mật / Product |
| Q8 | Nhiều tab trình duyệt có thể làm mới cùng lúc bằng cùng refresh token, và tab chậm hơn bị coi là "dùng lại" làm mất cả phiên. Chọn: BFF làm mới tuần tự một lần, hay đặt `auth.refresh.reuse_grace` > 0? (OQ-037) | FR-26, AC-29 | Kiến trúc / Bảo mật |
| Q9 | Họ tên tự khai lúc đăng ký (trước eKYC): có cần thu không? Tên này hiển thị ở đâu (tên người nhận P2P đã che, OQ-009), và có thể sai/mạo danh. (OQ-038) | FR-03, BR-03 | Product |
| Q10 | `customer.phone` duy nhất kể cả `CLOSED`: SĐT của tài khoản đã đóng có được đăng ký lại không? Nếu có thì tài khoản cũ xử lý thế nào (ẩn danh SĐT)? Ảnh hưởng cả người nhận SĐT mới tái cấp cho người khác. Mở rộng: SĐT cũ sau khi khách đổi SĐT có được người khác đăng ký ngay không (tiền chuyển theo SĐT sẽ tới chủ mới). (OQ-039) | BR-10, FR-06, F-ACC-03 | Product / Pháp chế |
| Q11 | Kênh gửi cảnh báo bảo mật (thiết bị mới, đổi mật khẩu, đổi SĐT): SMS, thông báo trong ứng dụng, hay email (hiện không thu email)? (OQ-040) | EV-05..EV-07 | Product |
| Q12 | `POST /me/consents` cho phép `granted = false`. Rút lại đồng ý `TERMS` hoặc `BIOMETRIC_PROCESSING` thì hệ quả gì (khoá tài khoản, xoá dữ liệu)? Spec này chỉ cho xem đồng ý. (OQ-041) | FR-40, F-CMP-01 | Pháp chế |
| Q13 | Ví mở bất đồng bộ sau `CustomerRegistered`. Khách vừa đăng ký xong thấy gì trong lúc chưa có ví, và người khác chuyển tiền tới SĐT này trong khoảng đó nhận lỗi `RECIPIENT_NOT_FOUND`. Có chờ ví sẵn sàng trước khi trả kết quả đăng ký không? (OQ-042) | FR-03, FR-05, EV-01 | Kiến trúc / Product |
| Q14 | `customer.status = PENDING` trong data model dùng cho việc gì? Đăng ký hiện tạo thẳng `ACTIVE`. (OQ-043) | ST-01..ST-05 | Kiến trúc |
| Q15 | Chỉ hỗ trợ SĐT Việt Nam, không kiểm đầu số nhà mạng: đúng ý không? (OQ-044; khoảng trắng trong SĐT: OQ-016) | BR-01, AC-02 | Product |

## 10. Lịch sử thay đổi

| Phiên bản | Ngày | Thay đổi (ID) | Lý do |
|---|---|---|---|
| 0.1 | 2026-11-04 | Tạo mới: 8 use case, 43 FR, 10 BR, 15 ST, 9 EV, 7 NFR, 53 AC, 15 câu hỏi mở | Spec đầu tiên cho F-ACC-01 |
| 0.2 | 2026-11-04 | ST-10: ghi chú data model đã có `superseded_at`. Q10 (OQ-039) mở rộng: SĐT cũ sau khi đổi SĐT có được đăng ký lại ngay không. Không đổi FR/BR/AC | Đối chiếu với data model `identity` (skill `data-model-design`) |
| 0.3 | 2026-11-04 | 4.2: trỏ tới hợp đồng trong `contracts/`; `/auth/login/verify` thêm `deviceFingerprint`; `/me/phone` đổi `challengeId` thành `otpChallengeId`; `/challenges` thêm `factor?` và `purpose` `CHANGE_PHONE`; xác minh challenge nhận `payload` và trả `factor`; thay danh sách "chỗ lệch" bằng danh sách điểm hợp đồng bổ sung. §6: ghi `FORBIDDEN` do BFF. Không đổi FR/BR/AC | Viết hợp đồng API bằng skill `api-contract` |
| 0.4 | 2026-11-10 | AC-07: bỏ tiền đề "hai challenge hợp lệ của cùng SĐT" (mâu thuẫn FR-12 và `uq_challenge_active`), thay bằng hai yêu cầu đồng thời với cùng challenge | Phát hiện khi sinh test case (TC-ACC-010) |
| 0.5 | 2026-11-10 | §6: `CHALLENGE_REQUIRED` cũng trả ở `POST /challenges` khi xin OTP đổi SĐT mà khuôn mặt chưa đạt (cụ thể hoá FR-35, AC-43). Không đổi FR/BR/AC | Phát hiện khi code `identity`; hợp đồng `identity/public.yaml` v0.2.1 |
