# F-PAY-03 — Chuyển tiền ví–ví

| | |
|---|---|
| Trạng thái | Draft |
| Phiên bản | 0.2 |
| Ngày | 2026-11-04 |
| Yêu cầu nguồn (sở hữu) | FR-P2P-01, FR-P2P-02, FR-P2P-03, FR-P2P-04 |
| Yêu cầu liên quan | FR-LIM-01..05, FR-FEE-01..03, FR-HS-02..04, NFR-C-02, NFR-C-03, NFR-L-04, NFR-P-02, NFR-S-03 |
| Cross-cutting áp dụng | CC-MONEY, CC-SEC, CC-COMP, CC-OBS, CC-UX |
| Tài liệu thiết kế | [design §6.1, 6.3, 6.9](../../design/2026-11-04-ewallet-design.md) · [api §1, §2](../../design/2026-11-04-ewallet-api.md) · [data-model](../../design/2026-11-04-ewallet-data-model.md) |
| Phụ thuộc | F-ACC-02 (cấp định danh), F-LMT-01 (hạn mức), F-FEE-01 (phí), F-FIN-01 (sổ cái), F-NTF-01 (thông báo) |

Nhãn nguồn: `[Thiết kế …]` = lấy từ design, requirements không nói · `[Giả định]` = suy ra, cần người duyệt xác nhận · `[Cần xác nhận Q#]` = chưa có quyết định, xem mục 9.

## 1. Giới thiệu

### 1.1 Mục đích
Cho phép khách hàng chuyển tiền từ ví của mình sang ví EWallet khác theo số điện thoại, tức thời, không qua ngân hàng, với số dư không bao giờ âm và không bao giờ bị trừ hai lần khi gửi lại yêu cầu.

### 1.2 Phạm vi
- **Trong phạm vi:** tra cứu người nhận, xem trước phí, xác thực tăng cường, thực hiện chuyển tiền, kết quả, thông báo và hiển thị trong lịch sử cho hai bên.
- **Ngoài phạm vi:**
  - Chuyển tiền ra tài khoản ngân hàng (F-PAY-02 Rút tiền).
  - Chuyển tiền cho số điện thoại chưa có ví.
  - Chuyển tiền nhiều người nhận, hẹn giờ, yêu cầu nhận tiền.
  - Cơ chế sinh và xác minh OTP/khuôn mặt (F-ACC-01, F-ACC-02); tính năng này chỉ quy định *khi nào* cần.

### 1.3 Định nghĩa
| Thuật ngữ | Định nghĩa |
|---|---|
| Người gửi / Người nhận | Chủ ví bị trừ tiền / chủ ví được cộng tiền |
| Lệnh (order) | Bản ghi một yêu cầu chuyển tiền, có trạng thái `PROCESSING`, `SUCCEEDED`, `FAILED` |
| Phí | Khoản phí EWallet thu của người gửi, theo F-FEE-01 |
| Xác thực tăng cường | Bước xác thực thêm (`OTP` hoặc `FACE`) khi giá trị vượt ngưỡng |
| Challenge | Yêu cầu xác thực tăng cường, gắn với một nội dung lệnh cụ thể |
| `Idempotency-Key` | Mã duy nhất (UUID) do client sinh cho mỗi lần *dự định* chuyển tiền, dùng để gửi lại an toàn |
| `failureCode` | Mã lý do lệnh thất bại, trong `order.failureCode` |

### 1.4 Tài liệu tham chiếu
[requirements.md](../../requirements.md) §2.5, §7.2, §7.3 · [index SRS](../index.md) · design và api (xem đầu trang).

## 2. Mô tả tổng quan

### 2.1 Bối cảnh & giá trị
Chuyển tiền giữa hai ví là luồng tiền đơn giản nhất (không có đối tác ngoài) nhưng là nơi dễ sai nhất về tính đúng: gửi lại khi mất mạng, hai lệnh đồng thời và đổi phí giữa chừng đều có thể làm mất hoặc nhân đôi tiền.

### 2.2 Actor & quyền
| Actor | Mô tả | Được phép |
|---|---|---|
| Người gửi | Khách đã đăng nhập, có ví | Tra cứu người nhận, xem trước, xác nhận lệnh, xem kết quả lệnh của mình |
| Người nhận | Khách có ví | Nhận thông báo và xem khoản tiền nhận trong lịch sử |
| Hệ thống | | Thực hiện lệnh, hoàn tất lệnh dở dang, gửi thông báo |

### 2.3 Phụ thuộc
| Loại | Tên | Vai trò |
|---|---|---|
| Tính năng | F-ACC-02 | Cung cấp cấp định danh của người gửi; xác thực khuôn mặt cho `FACE` |
| Tính năng | F-LMT-01 | Kiểm tra và giữ chỗ hạn mức |
| Tính năng | F-FEE-01 | Báo giá phí |
| Tính năng | F-FIN-01 | Ghi sổ kép, số dư |
| Tính năng | F-NTF-01 | Gửi thông báo |
| Cấu hình | `fee.p2p.*`, `limit.*`, `stepup.p2p.*`, `p2p.*` | Xem 3.3 và 3.5 |

### 2.4 Giả định & ràng buộc
- `[Thiết kế §6.1]` Phí do người gửi chịu: ví người gửi bị trừ `amount + fee`, ví người nhận được cộng đúng `amount`.
- `[Thiết kế §6.3]` Chuyển tiền ví–ví không gọi hệ thống bên ngoài, nên lệnh không có trạng thái `UNKNOWN`.
- `[Giả định]` Mỗi khách có tối đa một ví (FR-ID-03) nên "ví người gửi" xác định được từ phiên đăng nhập.
- Mọi số tiền là số nguyên đơn vị đồng (VND) (NFR-C-05).

## 3. Yêu cầu chức năng

### 3.1 Use case

| ID | Tên | Actor | Mục tiêu |
|---|---|---|---|
| F-PAY-03.UC-01 | Chuyển tiền cho người nhận bằng SĐT | Người gửi | Chuyển một khoản tiền cho ví khác |
| F-PAY-03.UC-02 | Gửi lại / xem lại kết quả lệnh | Người gửi | Biết chắc lệnh đã thành công hay chưa sau khi mất kết nối |

#### F-PAY-03.UC-01 — Chuyển tiền cho người nhận bằng SĐT
- **Tiền điều kiện:** Người gửi đã đăng nhập; ví người gửi `ACTIVE`; cấp định danh ≥ `p2p.min_kyc_tier` `[Cần xác nhận Q2]`.
- **Hậu điều kiện (thành công):** Lệnh `SUCCEEDED`. Số dư người gửi giảm `amount + fee`, số dư người nhận tăng `amount`, doanh thu phí tăng `fee`. Hạn mức người gửi được ghi nhận. Hai bên nhận thông báo và thấy lệnh trong lịch sử.
- **Hậu điều kiện (thất bại sau khi lệnh được tạo):** Lệnh `FAILED` kèm `failureCode`. Số dư hai bên không đổi. Hạn mức đã giữ được hoàn lại.
- **Hậu điều kiện (từ chối trước khi tạo lệnh):** Không có lệnh, không có thay đổi nào.
- **Luồng chính:**
  1. Người gửi nhập SĐT người nhận.
  2. Hệ thống hiển thị tên người nhận đã che một phần (FR-01).
  3. Người gửi nhập số tiền và (tuỳ chọn) lời nhắn.
  4. Hệ thống hiển thị xem trước: tên người nhận đã che, số tiền, phí, tổng bị trừ, và yêu cầu xác thực nếu có (FR-05).
  5. Client sinh `Idempotency-Key` khi người gửi vào bước xác nhận.
  6. Người gửi xác nhận.
  7. Hệ thống thực hiện chuyển tiền (FR-19..FR-25) và trả lệnh `SUCCEEDED`.
  8. Hệ thống hiển thị kết quả và gửi thông báo cho hai bên (FR-29).
- **Luồng thay thế:**
  - `A1` (sau bước 6) Xem trước có `requiredFactor = OTP`/`FACE`: người gửi hoàn tất xác thực rồi client gửi lệnh kèm `challengeId` (và `otp`).
  - `A2` (bước 7) Lệnh chưa xong khi hết thời gian chờ của request: hệ thống trả `PROCESSING` (202); client hiển thị "Đang xử lý" và hỏi lại kết quả (UC-02).
- **Luồng lỗi:**
  - `E1` (bước 2) SĐT không có ví → `RECIPIENT_NOT_FOUND`; ví không `ACTIVE` → `RECIPIENT_UNAVAILABLE`.
  - `E2` (bước 6) Phí đã đổi so với xem trước → `FEE_CHANGED`; không tạo lệnh; quay lại bước 4.
  - `E3` (bước 7) Số dư không đủ → lệnh `FAILED INSUFFICIENT_BALANCE`.
  - `E4` (bước 7) Vượt hạn mức → lệnh `FAILED LIMIT_EXCEEDED`.
  - `E5` (bước 6) Thiếu hoặc sai xác thực tăng cường → `CHALLENGE_REQUIRED`/`CHALLENGE_INVALID`; không tạo lệnh.
  - `E6` (bước 6) Ví người gửi không `ACTIVE` → `WALLET_FROZEN`; chưa đủ cấp định danh → `KYC_REQUIRED`.

#### F-PAY-03.UC-02 — Gửi lại / xem lại kết quả lệnh
- **Tiền điều kiện:** Người gửi đã gửi lệnh với một `Idempotency-Key` nhưng không biết kết quả (mất mạng, timeout, đóng app).
- **Hậu điều kiện:** Người gửi biết trạng thái cuối cùng của lệnh; không có lệnh thứ hai nào được tạo.
- **Luồng chính:** (1) Client gửi lại đúng yêu cầu với cùng `Idempotency-Key`. (2) Hệ thống trả lại lệnh đã tạo với trạng thái hiện tại.
- **Luồng thay thế:** `A1` Client hỏi trực tiếp `GET /orders/{id}` nếu đã biết `id`.
- **Luồng lỗi:** `E1` Cùng key nhưng nội dung khác → `IDEMPOTENCY_KEY_REUSED`. `E2` Lệnh đã `FAILED` và người gửi muốn thử lại → phải dùng `Idempotency-Key` **mới** (gửi lại key cũ chỉ nhận lại kết quả `FAILED`).

### 3.2 Yêu cầu chức năng

**Tra cứu & xem trước**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-PAY-03.FR-01 | Khi người gửi cung cấp số điện thoại của người nhận, hệ thống phải trả tên người nhận đã che một phần theo `p2p.recipient_mask_rule`, và không được trả tên đầy đủ. | M | FR-P2P-01; [Cần xác nhận Q4] |
| F-PAY-03.FR-02 | Hệ thống phải chấp nhận số điện thoại ở dạng nội địa (`0…`) và quốc tế (`+84…`) và chuẩn hoá về dạng quốc tế trước khi tra cứu. | M | [Giả định] |
| F-PAY-03.FR-03 | Nếu không có ví nào gắn với số điện thoại, hệ thống phải trả `RECIPIENT_NOT_FOUND`; nếu có ví nhưng không ở trạng thái `ACTIVE`, hệ thống phải trả `RECIPIENT_UNAVAILABLE`. | M | [Thiết kế api §1]; [Cần xác nhận Q5] |
| F-PAY-03.FR-04 | Hệ thống phải giới hạn số lần tra cứu người nhận của mỗi người dùng theo `ratelimit.recipient_lookup` và trả `RATE_LIMITED` kèm `Retry-After` khi vượt. | M | NFR-S-03; [Thiết kế api §2] |
| F-PAY-03.FR-05 | Khi người gửi yêu cầu xem trước, hệ thống phải trả: tên người nhận đã che, số tiền, phí, tổng bị trừ (`amount + fee`) và `requiredFactor` (`NONE`, `OTP` hoặc `FACE`). | M | FR-P2P-01; FR-FEE-01; [Thiết kế api §2] |
| F-PAY-03.FR-06 | Hệ thống không được tạo lệnh, giữ chỗ hạn mức hay ghi sổ khi xử lý yêu cầu xem trước. | M | [Thiết kế §6.3] |

**Idempotency**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-PAY-03.FR-07 | Khi yêu cầu tạo lệnh thiếu header `Idempotency-Key`, hệ thống phải từ chối với `IDEMPOTENCY_KEY_REQUIRED` và không tạo lệnh. | M | NFR-C-03 |
| F-PAY-03.FR-08 | Khi nhận yêu cầu tạo lệnh, hệ thống phải tra `Idempotency-Key` của người gửi **trước mọi kiểm tra khác** (xác thực tăng cường, phí, trạng thái ví); nếu đã có lệnh cùng khoá và cùng nội dung, hệ thống phải trả lại lệnh đó với trạng thái hiện tại và không được kiểm tra lại hay thực hiện lại bất kỳ bước nào. | M | NFR-C-03; [Thiết kế §6.3] |
| F-PAY-03.FR-09 | Khi cùng một khoá được gửi với nội dung khác (số tiền, số điện thoại người nhận hoặc lời nhắn), hệ thống phải từ chối với `IDEMPOTENCY_KEY_REUSED` và không tạo lệnh. | M | NFR-C-03; [Thiết kế api §1]; "nội dung" gồm ba trường trên `[Giả định]` |
| F-PAY-03.FR-10 | Hệ thống phải ghi nhớ `Idempotency-Key` ít nhất `idempotency.window` (mặc định 24 giờ). | M | [Thiết kế api §1] |
| F-PAY-03.FR-11 | Khi hai yêu cầu cùng người gửi, cùng `Idempotency-Key` đến đồng thời, hệ thống phải tạo đúng một lệnh và trả cùng lệnh đó cho cả hai yêu cầu. | M | NFR-C-03; [Thiết kế §6.3] |

**Điều kiện trước khi tạo lệnh**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-PAY-03.FR-12 | Nếu ví người gửi không ở trạng thái `ACTIVE`, hệ thống phải từ chối với `WALLET_FROZEN` và không tạo lệnh. | M | [Thiết kế §6.9; api §1] |
| F-PAY-03.FR-13 | Nếu cấp định danh của người gửi thấp hơn `p2p.min_kyc_tier`, hệ thống phải từ chối với `KYC_REQUIRED` và không tạo lệnh. | M | FR-ID-04; [Cần xác nhận Q2] |
| F-PAY-03.FR-14 | Hệ thống phải từ chối với `VALIDATION_ERROR` khi số tiền không phải số nguyên dương, hoặc lời nhắn vượt `p2p.note.max_length` ký tự; và với `AMOUNT_OUT_OF_RANGE` khi số tiền là số nguyên nhỏ hơn `p2p.amount.min`. | M | [Thiết kế api §1]; [Giả định] về phân biệt hai mã |
| F-PAY-03.FR-15 | Hệ thống không được cho phép người gửi chuyển tiền cho chính số điện thoại của mình; yêu cầu đó phải bị từ chối với `VALIDATION_ERROR`. | M | [Giả định] |
| F-PAY-03.FR-16 | Hệ thống phải xác định `requiredFactor` theo BR-07; khi `requiredFactor` khác `NONE`, hệ thống phải từ chối lệnh không kèm challenge hợp lệ (`CHALLENGE_REQUIRED` nếu thiếu, `CHALLENGE_INVALID` nếu sai, hết hạn hoặc đã dùng cho lệnh khác). | M | FR-P2P-04; NFR-L-04; [Cần xác nhận Q1] |
| F-PAY-03.FR-17 | Challenge phải gắn với nội dung lệnh (người nhận, số tiền): challenge cấp cho nội dung này không được dùng cho lệnh có nội dung khác. | M | [Thiết kế §6.9] |
| F-PAY-03.FR-18 | Nếu phí hiện hành khác `expectedFee` mà client gửi, hệ thống phải từ chối với `FEE_CHANGED` và không tạo lệnh. | M | FR-FEE-01; [Thiết kế §6.3] |

**Thực hiện lệnh**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-PAY-03.FR-19 | Khi mọi điều kiện ở trên thoả mãn, hệ thống phải tạo đúng một lệnh ở trạng thái `PROCESSING`. | M | [Thiết kế §6.3] |
| F-PAY-03.FR-20 | Hệ thống phải giữ chỗ hạn mức của người gửi cho lệnh trước khi ghi sổ; nếu vượt hạn mức, lệnh phải chuyển `FAILED` với `LIMIT_EXCEEDED` và không có thay đổi số dư. | M | FR-LIM-02; [Thiết kế §6.3] |
| F-PAY-03.FR-21 | Hệ thống phải thực hiện trừ ví người gửi, cộng ví người nhận và ghi phí như một thao tác không thể dở dang: không có trạng thái trong đó chỉ một phần của thao tác được áp dụng. | M | FR-P2P-02 |
| F-PAY-03.FR-22 | Hệ thống không được để số dư ví nhỏ hơn 0 trong mọi trường hợp, kể cả khi nhiều lệnh của cùng một ví được gửi đồng thời. | M | FR-P2P-02; NFR-C-02 |
| F-PAY-03.FR-23 | Nếu số dư khả dụng của người gửi nhỏ hơn `amount + fee`, lệnh phải chuyển `FAILED` với `INSUFFICIENT_BALANCE` và không có thay đổi số dư. | M | FR-P2P-02 |
| F-PAY-03.FR-24 | Khi lệnh chuyển `FAILED`, hệ thống phải hoàn lại hạn mức đã giữ chỗ; khi lệnh chuyển `SUCCEEDED`, hạn mức phải được ghi nhận là đã dùng. | M | FR-LIM-03; [Thiết kế §6.3] |
| F-PAY-03.FR-25 | Nếu ví người gửi hoặc người nhận bị khoá giữa lúc kiểm tra và lúc ghi sổ, lệnh phải chuyển `FAILED` với `ACCOUNT_BLOCKED` và không có thay đổi số dư. | M | [Thiết kế §6.3; api §5]; [Cần xác nhận Q9] |

**Kết quả & khôi phục**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-PAY-03.FR-26 | Hệ thống phải trả `200` kèm lệnh khi lệnh đã `SUCCEEDED` hoặc `FAILED`, và `202` kèm lệnh `PROCESSING` khi lệnh chưa có kết quả cuối khi hết thời gian chờ của request; người gửi phải lấy được kết quả sau đó bằng `GET /orders/{id}`. | M | [Thiết kế api §1, §2] |
| F-PAY-03.FR-27 | Hệ thống phải tự hoàn tất hoặc chuyển `FAILED` mọi lệnh ở `PROCESSING` quá `p2p.processing.resume_after` (mặc định 2 phút), sao cho không lệnh nào kẹt vĩnh viễn và tiền không bị chuyển hai lần. | M | [Thiết kế §6.3, §6.6]; thời hạn tối đa [Cần xác nhận Q7] |
| F-PAY-03.FR-28 | Khi lệnh đã `FAILED`, gửi lại cùng `Idempotency-Key` phải trả đúng lệnh `FAILED` đó; muốn thử lại, client phải dùng `Idempotency-Key` mới. | M | NFR-C-03; [Thiết kế api §1] |

**Thông báo, lịch sử, lời nhắn, audit**

| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-PAY-03.FR-29 | Khi lệnh `SUCCEEDED`, hệ thống phải gửi thông báo biến động số dư cho cả người gửi và người nhận, gồm số tiền, mã lệnh và thời điểm; nội dung hiển thị về đối phương theo Q8. | M | FR-HS-04; [Cần xác nhận Q8] |
| F-PAY-03.FR-30 | Hệ thống phải hiển thị lệnh trong lịch sử của người gửi ở mọi trạng thái (kể cả `FAILED`) với số tiền ảnh hưởng ví là `amount + fee`; và trong lịch sử của người nhận chỉ khi lệnh `SUCCEEDED`, với số tiền là `amount`. | M | FR-HS-02, 03; [Thiết kế data-model `wallet_activity`]; hiển thị `FAILED` cho người gửi [Giả định] |
| F-PAY-03.FR-31 | Hệ thống nên cho phép người gửi kèm lời nhắn tối đa `p2p.note.max_length` ký tự (mặc định 140) và phải từ chối lời nhắn chứa ký tự điều khiển. | S | FR-P2P-03; độ dài [Thiết kế data-model]; ký tự bị cấm [Giả định] |
| F-PAY-03.FR-32 | Hệ thống phải ghi audit cho mỗi lệnh gồm: người gửi, người nhận, số tiền, phí, trạng thái cuối, `failureCode` (nếu có) và thời điểm. | M | [Thiết kế §10, §11]; CC-SEC |

### 3.3 Quy tắc nghiệp vụ

| ID | Quy tắc | Tham số cấu hình | Nguồn |
|---|---|---|---|
| F-PAY-03.BR-01 | Phí tính theo biểu phí P2P hiện hành của F-FEE-01: `phí = kẹp vào [min_fee, max_fee] của (fixed_fee + làm tròn lên đến đồng của amount × percent_bp / 10.000)`. | `fee.p2p.*` | FR-FEE-01, 03 |
| F-PAY-03.BR-02 | Tổng bị trừ của người gửi = `amount + fee`; người nhận được cộng đúng `amount`; `fee` được ghi vào doanh thu phí. | — | [Thiết kế §6.1] |
| F-PAY-03.BR-03 | Người gửi và người nhận phải là hai ví khác nhau. | — | [Giả định] |
| F-PAY-03.BR-04 | Ví người nhận phải ở trạng thái `ACTIVE`; ví `FROZEN`, `CLOSING`, `CLOSED` không nhận được tiền. | — | [Thiết kế §6.9] |
| F-PAY-03.BR-05 | Số tiền là số nguyên đơn vị đồng, `≥ p2p.amount.min`. Giới hạn trên do hạn mức quy định (BR-06). | `p2p.amount.min` | NFR-C-05; [Cần xác nhận Q10] |
| F-PAY-03.BR-06 | Hạn mức loại giao dịch `P2P` được kiểm tra theo ba tầng (pháp lý, cấp định danh, riêng của khách) cho các kỳ giao dịch/ngày/tháng; kỳ ngày và tháng tính theo giờ Việt Nam; giao dịch bị từ chối nếu vượt bất kỳ tầng nào. | `limit.*` (F-LMT-01) | FR-LIM-01; [Cần xác nhận Q3] |
| F-PAY-03.BR-07 | `requiredFactor`: `NONE` nếu `amount ≤ stepup.p2p.otp_threshold` và tổng đã chuyển trong ngày cộng `amount` `≤ stepup.p2p.day_otp_threshold`; `FACE` nếu `amount > stepup.p2p.face_threshold` hoặc tổng ngày cộng `amount > stepup.p2p.day_face_threshold`; còn lại `OTP`. "Vượt" nghĩa là lớn hơn hẳn. | `stepup.p2p.*` | FR-P2P-04; [requirements §7.2]; [Cần xác nhận Q1] |
| F-PAY-03.BR-08 | Khi một yêu cầu vi phạm nhiều điều kiện, hệ thống trả mã lỗi của điều kiện đầu tiên theo thứ tự: (1) định dạng (`VALIDATION_ERROR`, `IDEMPOTENCY_KEY_REQUIRED`) → (2) tra `Idempotency-Key` (FR-08, FR-09) → (3) challenge (FR-16, FR-17) → (4) ví người gửi và cấp định danh (FR-12, FR-13) → (5) người nhận (FR-03, BR-03) → (6) phí (FR-18) → (7) số tiền tối thiểu (FR-14). | — | [Thiết kế §6.3] cho thứ tự (2)–(6); vị trí (1), (7) [Giả định] |

### 3.4 Trạng thái & chuyển trạng thái

| ID | Từ | Điều kiện | Sang | Ghi chú |
|---|---|---|---|---|
| F-PAY-03.ST-01 | (chưa có) | Qua mọi kiểm tra trước khi tạo lệnh | `PROCESSING` | FR-19 |
| F-PAY-03.ST-02 | `PROCESSING` | Giữ chỗ hạn mức và ghi sổ thành công | `SUCCEEDED` | FR-21, FR-24 |
| F-PAY-03.ST-03 | `PROCESSING` | Vượt hạn mức / thiếu số dư / ví bị khoá | `FAILED` | FR-20, FR-23, FR-25 |
| F-PAY-03.ST-04 | `SUCCEEDED`, `FAILED`; hoặc bất kỳ → `UNKNOWN` | bất kỳ | (bị cấm) | Trạng thái cuối không đổi được; P2P không có `UNKNOWN` (2.4) |

### 3.5 Dữ liệu

**Tra cứu người nhận** `GET /transfers/recipient?phone=`
| Trường | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| phone | chuỗi | Có | SĐT hợp lệ, chuẩn hoá về `+84…` | `0900000002` |

**Xem trước** `POST /transfers/preview` và **Tạo lệnh** `POST /transfers`
| Trường | Kiểu | Bắt buộc | Ràng buộc | Ví dụ |
|---|---|---|---|---|
| recipientPhone | chuỗi | Có | như `phone` | `+84900000002` |
| amount | số nguyên (VND) | Có | ≥ `p2p.amount.min`, không thập phân | `100000` |
| note | chuỗi | Không | ≤ `p2p.note.max_length`, không chứa ký tự điều khiển | `Tiền ăn trưa` |
| expectedFee | số nguyên | Có (tạo lệnh) | ≥ 0 | `1000` |
| challengeId | UUID | Khi `requiredFactor ≠ NONE` | | |
| otp | chuỗi 6 chữ số | Khi `requiredFactor = OTP` | | `123456` |
| Header `Idempotency-Key` | UUID | Có (tạo lệnh) | | |

**Lệnh (`order`) trả về:** `id`, `type = P2P`, `status`, `amount`, `fee`, `createdAt`, `failureCode?`.

Dữ liệu nhạy cảm: OTP không được ghi log; tên người nhận chỉ trả dạng đã che.

### 3.6 Đầu ra phụ: thông báo, sự kiện, audit
| ID | Sự kiện | Đích | Nội dung | Điều kiện |
|---|---|---|---|---|
| F-PAY-03.EV-01 | Lệnh `SUCCEEDED` | Thông báo người gửi | Số tiền chuyển, phí, mã lệnh, thời điểm | FR-29 |
| F-PAY-03.EV-02 | Lệnh `SUCCEEDED` | Thông báo người nhận | Số tiền nhận, mã lệnh, thời điểm; thông tin người gửi theo Q8 | FR-29 |
| F-PAY-03.EV-03 | Lệnh `SUCCEEDED` / `FAILED` | Audit | Xem FR-32 | Luôn |
| F-PAY-03.EV-04 | Lệnh `SUCCEEDED` / `FAILED` | Sự kiện nội bộ (`PaymentSucceeded`/`PaymentFailed`), đúng một lần, có `aggregateversion` | Cho hạn mức, thông báo, đối soát, AML | [Thiết kế api §6, §7] |

## 4. Giao diện ngoài

### 4.1 Giao diện người dùng
| Màn hình | Thành phần | Hành vi |
|---|---|---|
| M1 Nhập thông tin | SĐT người nhận, số tiền, lời nhắn | Nhập SĐT xong hiển thị tên đã che hoặc lỗi (FR-01, FR-03). Số tiền định dạng `1.000.000 ₫` (CC-UX) |
| M2 Xác nhận | Người nhận (đã che), số tiền, phí, tổng bị trừ | Nút xác nhận bị vô hiệu hoá ngay khi bấm, không gửi được hai yêu cầu bằng bấm đúp. `Idempotency-Key` sinh khi vào M2 và giữ nguyên khi người dùng bấm lại trong cùng lần xác nhận |
| M3 Xác thực | Ô nhập OTP hoặc luồng khuôn mặt | Chỉ hiện khi `requiredFactor ≠ NONE` |
| M4 Kết quả | Trạng thái, mã lệnh, số tiền | `SUCCEEDED`: thành công. `FAILED`: lý do bằng tiếng Việt (mục 6). `PROCESSING`: "Đang xử lý", **không** hiển thị "Thất bại"; tự hỏi lại kết quả định kỳ |

Quy tắc UI: sau lệnh `FAILED`, bấm "Thử lại" phải sinh `Idempotency-Key` mới (FR-28); khi nhận `FEE_CHANGED`, quay về M2 với phí mới.

### 4.2 API
| Method | Path | Idempotency | Auth | Ghi chú |
|---|---|---|---|---|
| GET | `/transfers/recipient?phone=` | — | Khách | FR-01, 03, 04 |
| POST | `/transfers/preview` | — | Khách | FR-05, 06 |
| POST | `/transfers` | Bắt buộc | Khách | FR-07..FR-26. `200` (cuối) hoặc `202` (`PROCESSING`) |
| GET | `/orders/{id}` | — | Khách, chỉ lệnh của mình | FR-26 |
| POST | `/challenges` | — | Khách | Cấp challenge `{purpose, payload}` (F-ACC-01) |

Chi tiết schema: [api §2](../../design/2026-11-04-ewallet-api.md).

### 4.3 Hệ thống ngoài / provider
Không áp dụng: chuyển tiền ví–ví không gọi ngân hàng hay provider bên thứ 3. (Thông báo qua `NotificationSender`: xem F-NTF-01.)

## 5. Yêu cầu phi chức năng riêng
| ID | Loại | Yêu cầu | Nguồn |
|---|---|---|---|
| F-PAY-03.NFR-01 | Hiệu năng | p99 của `POST /transfers` < 1 giây khi hệ thống chịu 150 TPS ghi `(giả định NFR-P-01: 500 TPS, 70/30)` | NFR-P-02 |
| F-PAY-03.NFR-02 | Đúng đắn | Trong mọi thời điểm, tổng số dư mọi ví cộng doanh thu phí không đổi sau một lệnh `SUCCEEDED` hoặc `FAILED` (bảo toàn tiền) | NFR-C-01, 04 |
| F-PAY-03.NFR-03 | Bảo mật | Một người dùng không đọc được lệnh của người khác qua `GET /orders/{id}` | CC-SEC; [Thiết kế api §2] |
| F-PAY-03.NFR-04 | Bảo mật | OTP và mã xác thực không xuất hiện trong log hay response | NFR-S-04; [Thiết kế §10] |
| F-PAY-03.NFR-05 | Vận hành | Mỗi lệnh có `traceId` tra cứu được trên CMS để truy vết | NFR-O-01; [Thiết kế §11] |
| F-PAY-03.NFR-06 | Khả dụng | Theo CC-AVAIL (≥ 99,9%/tháng) | NFR-A-01 |
| F-PAY-03.NFR-07 | Truy cập | Các màn hình M1–M4 đạt WCAG 2.1 AA | NFR-UX-03 |

## 6. Danh mục lỗi & thông điệp

| Mã | Điều kiện | HTTP / nơi trả | Thông điệp (vi) | Thử lại |
|---|---|---|---|---|
| `VALIDATION_ERROR` | Định dạng sai, số tiền không hợp lệ, lời nhắn sai, chuyển cho chính mình | 400 | "Thông tin không hợp lệ: {chi tiết}." | Có, sau khi sửa |
| `IDEMPOTENCY_KEY_REQUIRED` | Thiếu `Idempotency-Key` | 400 | (lỗi phía client, không hiển thị) | Không |
| `WALLET_FROZEN` | Ví người gửi không `ACTIVE` | 403 | "Ví của bạn đang bị tạm khoá. Vui lòng liên hệ hỗ trợ." | Không |
| `KYC_REQUIRED` | Cấp định danh chưa đủ | 403 | "Vui lòng hoàn tất xác thực danh tính để chuyển tiền." | Sau khi xác thực |
| `RECIPIENT_NOT_FOUND` | Không có ví với SĐT | 404 | "Không tìm thấy ví với số điện thoại này." | Có, nhập lại |
| `RECIPIENT_UNAVAILABLE` | Ví người nhận không `ACTIVE` | 422 | "Không thể chuyển tiền cho người nhận này." | Không |
| `AMOUNT_OUT_OF_RANGE` | Số tiền < `p2p.amount.min` | 422 | "Số tiền tối thiểu là {min} ₫." | Có, sau khi sửa |
| `CHALLENGE_REQUIRED` | Cần xác thực tăng cường nhưng thiếu | 422 | "Vui lòng xác thực để tiếp tục." | Có |
| `CHALLENGE_INVALID` | Mã sai, hết hạn hoặc không khớp nội dung lệnh | 422 | "Mã xác thực không đúng hoặc đã hết hạn." | Có |
| `IDEMPOTENCY_KEY_REUSED` | Cùng khoá, khác nội dung | 409 | (lỗi phía client, không hiển thị) | Không |
| `FEE_CHANGED` | Phí khác `expectedFee` | 409 | "Phí đã thay đổi. Vui lòng xem lại trước khi xác nhận." | Có, sau khi xem lại |
| `RATE_LIMITED` | Vượt giới hạn tra cứu | 429 + `Retry-After` | "Bạn thao tác quá nhanh. Vui lòng thử lại sau {n} giây." | Có, sau `Retry-After` |
| `INSUFFICIENT_BALANCE` | Số dư khả dụng < `amount + fee` | `order.failureCode` (200) | "Số dư không đủ để thực hiện giao dịch." | Có, với khoá mới |
| `LIMIT_EXCEEDED` | Vượt hạn mức | `order.failureCode` (200) | "Giao dịch vượt hạn mức. Hạn mức còn lại: {remaining} ₫." | Chỉ khi hạn mức làm mới hoặc với số tiền nhỏ hơn |
| `ACCOUNT_BLOCKED` | Ví bị khoá giữa chừng | `order.failureCode` (200) | "Giao dịch không thể thực hiện. Vui lòng liên hệ hỗ trợ." | Không |

## 7. Tiêu chí chấp nhận

**Cấu hình giả định cho các AC:** phí P2P cố định 1.000 ₫ (trừ khi nói khác); `limit.p2p.per_txn = 5.000.000`; `limit.p2p.per_day = 10.000.000`; `stepup.p2p.otp_threshold = 2.000.000`, `stepup.p2p.face_threshold = 20.000.000`; `p2p.amount.min = 1.000`; `p2p.note.max_length = 140`. Đây là số dùng để minh hoạ AC, không phải giá trị đã chốt.

| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-PAY-03.AC-01 | FR-19, FR-21, FR-24, FR-26, BR-01, BR-02 | Ví A `ACTIVE` số dư 1.000.000; ví B `ACTIVE` số dư 0 | A chuyển 100.000 cho B (key K1) | `200`, lệnh `SUCCEEDED`; A = 899.000; B = 100.000; doanh thu phí +1.000; hạn mức ngày đã dùng của A = 100.000 |
| F-PAY-03.AC-02 | FR-23 | A số dư 101.000 | A chuyển 100.000 | `SUCCEEDED`; A = 0 (đúng đủ `amount + fee`) |
| F-PAY-03.AC-03 | FR-23, FR-24 | A số dư 100.000 | A chuyển 100.000 | Lệnh `FAILED INSUFFICIENT_BALANCE`; A = 100.000, B không đổi; hạn mức đã dùng của A không đổi |
| F-PAY-03.AC-04 | FR-22, FR-21 | Phí 0; A số dư 100.000 | A gửi đồng thời hai lệnh 100.000 với hai khoá khác nhau | Đúng một lệnh `SUCCEEDED`, một lệnh `FAILED INSUFFICIENT_BALANCE`; A = 0; B +100.000; tổng tiền của A, B bảo toàn |
| F-PAY-03.AC-05 | FR-08, FR-26 | A đã chuyển thành công với K1 | A gửi lại đúng yêu cầu với K1 | Nhận lại cùng lệnh `SUCCEEDED` (cùng `id`); số dư A, B không đổi thêm |
| F-PAY-03.AC-06 | FR-08, FR-16 | Số tiền 3.000.000 cần OTP; A chuyển thành công với K1 và OTP | A gửi lại đúng yêu cầu với K1 và cùng `challengeId` (OTP đã dùng) | Nhận lại lệnh `SUCCEEDED`; **không** nhận `CHALLENGE_INVALID`; không có lệnh thứ hai |
| F-PAY-03.AC-07 | FR-11 | Chưa có lệnh với K2 | A gửi hai yêu cầu giống hệt với K2 đồng thời | Đúng một lệnh được tạo; cả hai yêu cầu nhận cùng `id`; A chỉ bị trừ một lần |
| F-PAY-03.AC-08 | FR-09 | A đã dùng K1 với amount 100.000 | A gửi K1 với amount 200.000 | `409 IDEMPOTENCY_KEY_REUSED`; không có lệnh mới; số dư không đổi |
| F-PAY-03.AC-09 | FR-07 | — | A gửi `POST /transfers` không có `Idempotency-Key` | `400 IDEMPOTENCY_KEY_REQUIRED`; không có lệnh |
| F-PAY-03.AC-10 | FR-28 | A số dư 100.000; lệnh K3 chuyển 100.000 đã `FAILED INSUFFICIENT_BALANCE`; sau đó A nạp thêm 100.000 | A gửi lại K3 | Nhận lại lệnh `FAILED` cũ, không thực hiện lại. Với key K4 cùng nội dung → `SUCCEEDED` |
| F-PAY-03.AC-11 | FR-10 | A đã dùng K1 cách đây 23 giờ | A gửi lại K1 | Nhận lại lệnh cũ |
| F-PAY-03.AC-12 | FR-18, BR-01 | Xem trước báo phí 1.000; sau đó biểu phí P2P đổi thành 1.500 và có hiệu lực | A xác nhận với `expectedFee = 1.000` | `409 FEE_CHANGED`; không có lệnh; số dư không đổi |
| F-PAY-03.AC-13 | FR-20, BR-06 | Hạn mức mỗi giao dịch 5.000.000; A đủ số dư | A chuyển 5.000.000 / 5.000.001 / 4.999.999 (ba lệnh riêng, lần lượt) | Lệnh 1: `SUCCEEDED`. Lệnh 2: `FAILED LIMIT_EXCEEDED` kèm hạn mức còn lại. Lệnh 3: `SUCCEEDED` (khi hạn mức ngày còn đủ) |
| F-PAY-03.AC-14 | FR-24 | Hạn mức ngày còn 10.000.000; A số dư 100.000 | A chuyển 5.000.000 (thiếu số dư) | `FAILED INSUFFICIENT_BALANCE`; hạn mức ngày còn lại vẫn 10.000.000 |
| F-PAY-03.AC-15 | BR-06 | Đồng hồ hệ thống 2026-11-04 16:59:59Z (23:59:59 giờ VN) và 17:00:01Z (00:00:01 ngày 05/11 giờ VN) | A chuyển một lệnh ở mỗi thời điểm | Hai lệnh được tính vào hai ngày khác nhau |
| F-PAY-03.AC-16 | FR-05, FR-16, BR-07 | Ngưỡng OTP 2.000.000 | Xem trước với amount 2.000.000 / 2.000.001 | `requiredFactor` = `NONE` / `OTP` |
| F-PAY-03.AC-17 | FR-16, BR-07 | Ngưỡng khuôn mặt 20.000.000 | Xem trước amount 20.000.000 / 20.000.001 | `OTP` / `FACE` |
| F-PAY-03.AC-18 | FR-16, BR-08 | amount 3.000.000 cần OTP | A tạo lệnh không kèm `challengeId` | `422 CHALLENGE_REQUIRED`; không có lệnh |
| F-PAY-03.AC-19 | FR-17 | Challenge cấp cho amount 3.000.000 | A dùng challenge đó cho lệnh amount 4.000.000 | `422 CHALLENGE_INVALID`; không có lệnh |
| F-PAY-03.AC-20 | FR-12 | Ví A `FROZEN` | A tạo lệnh | `403 WALLET_FROZEN`; không có lệnh |
| F-PAY-03.AC-21 | FR-25 | Ví A vừa qua kiểm tra, rồi bị khoá trước khi ghi sổ | Lệnh đang xử lý | Lệnh `FAILED ACCOUNT_BLOCKED`; A, B không đổi |
| F-PAY-03.AC-22 | FR-13 | Cấp định danh của A thấp hơn `p2p.min_kyc_tier` | A tạo lệnh | `403 KYC_REQUIRED`; không có lệnh |
| F-PAY-03.AC-23 | FR-03, BR-04 | Không có ví với `0900000999`; ví B `FROZEN` | A tra cứu `0900000999`, rồi tra cứu SĐT của B | `404 RECIPIENT_NOT_FOUND`; `422 RECIPIENT_UNAVAILABLE`; không có lệnh |
| F-PAY-03.AC-24 | FR-15, BR-03 | — | A chuyển cho chính SĐT của A | `400 VALIDATION_ERROR`; không có lệnh |
| F-PAY-03.AC-25 | FR-14, BR-05 | `p2p.amount.min = 1.000` | A xem trước/tạo lệnh với amount `0`, `-1`, `1000.5`, `"abc"`, `999`, `1000` | Bốn giá trị đầu: `VALIDATION_ERROR`. `999`: `AMOUNT_OUT_OF_RANGE`. `1000`: hợp lệ |
| F-PAY-03.AC-26 | FR-31, FR-14 | Lời nhắn dài 140 / 141 ký tự; lời nhắn chứa ký tự điều khiển | A tạo lệnh với từng lời nhắn | 140: hợp lệ. 141 và ký tự điều khiển: `VALIDATION_ERROR`; không có lệnh |
| F-PAY-03.AC-27 | FR-01, FR-05 | Ví B của "NGUYEN VAN A" | A tra cứu và xem trước | Response không chứa tên đầy đủ "NGUYEN VAN A"; chứa tên đã che theo quy tắc `p2p.recipient_mask_rule` |
| F-PAY-03.AC-28 | FR-04 | Giới hạn tra cứu N lần trong cửa sổ W | A tra cứu lần thứ N+1 trong W | `429 RATE_LIMITED` kèm `Retry-After` |
| F-PAY-03.AC-29 | FR-06 | A và B có số dư S_A, S_B | A gọi xem trước 3 lần | Không có lệnh nào; số dư, hạn mức không đổi |
| F-PAY-03.AC-30 | FR-26, FR-27 | Tiến trình xử lý bị ngắt giữa chừng ngay sau khi tạo lệnh `PROCESSING` | Chờ quá `p2p.processing.resume_after` | Lệnh về trạng thái cuối `SUCCEEDED` hoặc `FAILED`; nếu `SUCCEEDED` thì tiền chuyển đúng một lần |
| F-PAY-03.AC-31 | FR-26 | Lệnh chưa xong khi hết thời gian chờ request | A nhận `202 PROCESSING`, sau đó gọi `GET /orders/{id}` | Cuối cùng trả trạng thái cuối; UI không hiển thị "Thất bại" khi lệnh còn `PROCESSING` |
| F-PAY-03.AC-32 | FR-21 | Sự cố xảy ra ở bất kỳ điểm nào trong lúc ghi sổ | Sau khi hệ thống phục hồi | Hoặc cả ba thay đổi (trừ A, cộng B, ghi phí) cùng có, hoặc không thay đổi nào |
| F-PAY-03.AC-33 | FR-29 | Lệnh `SUCCEEDED` | Xem thông báo của A và B | Cả hai có đúng một thông báo biến động với số tiền tương ứng (A: 101.000 gồm phí, B: 100.000) |
| F-PAY-03.AC-34 | FR-30 | Lệnh `FAILED` do thiếu số dư | Xem lịch sử của A và B | A thấy lệnh `FAILED`; B không thấy gì |
| F-PAY-03.AC-35 | FR-30 | Lệnh `SUCCEEDED` | Xem lịch sử của A và B | A thấy −101.000; B thấy +100.000, cùng mã lệnh |
| F-PAY-03.AC-36 | FR-32, NFR-05 | Một lệnh bất kỳ | Truy cập audit và CMS theo mã lệnh | Có bản ghi audit đủ trường FR-32 và `traceId` |
| F-PAY-03.AC-37 | NFR-03 | Lệnh O thuộc ví A | Người dùng C gọi `GET /orders/{O}` | Bị từ chối (không lộ nội dung lệnh) |
| F-PAY-03.AC-38 | NFR-02 | Trước và sau 1.000 lệnh ngẫu nhiên (thành công và thất bại) | Tính tổng số dư mọi ví + doanh thu phí | Hai tổng bằng nhau |
| F-PAY-03.AC-39 | FR-02 | Ví B đăng ký với SĐT `+84900000002` | A tra cứu `0900000002`, rồi `+84900000002` | Cả hai cho cùng người nhận B |
| F-PAY-03.AC-40 | BR-08, FR-08, FR-12, FR-03 | Ví A `FROZEN`; SĐT người nhận không có ví | A tạo lệnh | Trả `WALLET_FROZEN` (điều kiện đứng trước theo BR-08), không phải `RECIPIENT_NOT_FOUND`. Gửi lại cùng key của một lệnh đã tạo khi A đã bị khoá: trả lại lệnh cũ, không phải `WALLET_FROZEN` (tra khoá đứng trước) |
| F-PAY-03.AC-41 | ST-04 | Lệnh đã `SUCCEEDED` (hoặc `FAILED`) | Một tiến trình khác (callback, recovery) cố đổi trạng thái lệnh | Trạng thái không đổi; số dư không đổi; không có journal mới |
| F-PAY-03.AC-42 | EV-04 | Một lệnh `SUCCEEDED` và một lệnh `FAILED`; một lệnh bị rollback trước khi tạo | Đọc topic `payment.events` | Đúng 1 `PaymentSucceeded` và 1 `PaymentFailed` (có `failureCode`), mỗi event có `aggregateversion`; lệnh rollback không có event |

## 8. Ma trận truy vết

| Yêu cầu nguồn | FR | BR | AC | Ghi chú |
|---|---|---|---|---|
| FR-P2P-01 | FR-01..06 | BR-04 | AC-16, 23, 27..29, 39 | |
| FR-P2P-02 | FR-21..23 | BR-02 | AC-01..04, 32, 38 | |
| FR-P2P-03 | FR-31 | — | AC-26 | Mức S; ký tự bị cấm là giả định |
| FR-P2P-04 | FR-16, FR-17 | BR-07 | AC-06, 16..19 | Chờ Q1 (ngưỡng) |
| NFR-C-03 (CC-MONEY) | FR-07..11, FR-28 | — | AC-05..11 | Cross-cutting |
| FR-LIM-02, 03 (liên quan) | FR-20, FR-24 | BR-06 | AC-13..15 | Ngữ nghĩa hạn mức ở F-LMT-01 |
| FR-FEE-01 (liên quan) | FR-05, FR-18 | BR-01 | AC-01, 12 | Công thức phí ở F-FEE-01 |
| FR-HS-02..04 (liên quan) | FR-29, FR-30 | — | AC-33..35 | |

Các FR/BR không có yêu cầu gốc trực tiếp (nhãn `[Giả định]`/`[Thiết kế]`) vẫn có AC; xem cột "Phủ" ở mục 7. Mọi FR-01..32 và BR-01..08 có ít nhất một AC. Các yêu cầu phi chức năng riêng NFR-01, NFR-04, NFR-06, NFR-07 không viết thành AC Given/When/Then vì được kiểm chứng ở mức phi chức năng (load test, rà soát log, giám sát khả dụng, kiểm tra truy cập); NFR-02, NFR-03, NFR-05 có AC-38, AC-37, AC-36.

## 9. Câu hỏi mở & giả định cần xác nhận

ID chung và trạng thái trả lời: xem [open-questions.md](../../open-questions.md) (cột Nguồn: "SRS F-PAY-03 Q#").

| # | Câu hỏi / Giả định | Ảnh hưởng | Ai quyết định |
|---|---|---|---|
| Q1 | Ngưỡng xác thực tăng cường cho P2P: ngưỡng OTP, ngưỡng khuôn mặt, và có tính *tổng trong ngày* không? ⚖️ | FR-16, BR-07, AC-16..17 | Pháp chế / Rủi ro |
| Q2 | Cấp định danh tối thiểu để chuyển tiền (requirements §7.2: cấp 0 không được giao dịch, nhưng bảng đó là ví dụ minh hoạ) | FR-13, UC-01 tiền điều kiện | Rủi ro / Pháp chế |
| Q3 | Hạn mức P2P tính trên tiền *ra*, tiền *vào* hay cả hai? Người nhận có bị hạn mức nhận hoặc giới hạn số dư ví tối đa không? ⚖️ | BR-06, FR-20, thêm FR cho người nhận | Pháp chế |
| Q4 | Quy tắc che tên người nhận (`p2p.recipient_mask_rule`); data-model chỉ có ví dụ `NGUYEN V*** A` | FR-01, AC-27 | Product |
| Q5 | Có nên gộp `RECIPIENT_NOT_FOUND` và `RECIPIENT_UNAVAILABLE` thành một lỗi để tránh dò tài khoản? (rate limit chỉ giảm nhẹ rủi ro) | FR-03, AC-23 | Product / Bảo mật |
| Q6 | Người nhận cần đạt cấp định danh tối thiểu để nhận tiền không? | Thêm BR cho người nhận | Rủi ro / Pháp chế |
| Q7 | Thời hạn tối đa một lệnh được ở `PROCESSING` trước khi cảnh báo vận hành, và có cần báo cho khách? (thiết kế chỉ nêu job quét sau 2 phút) | FR-27 | Vận hành |
| Q8 | Thông báo cho người nhận được hiển thị gì về người gửi (tên che, SĐT che, hay không gì)? | FR-29, 3.6 | Product / Pháp chế (dữ liệu cá nhân) |
| ~~Q9~~ | ✅ Đã chốt 2026-11-04: dùng `ACCOUNT_BLOCKED` (khớp api §5 và data model `debit_blocked`/`credit_blocked`); đã sửa sơ đồ design §6.3 | FR-25, mục 6 | — |
| Q10 | Giá trị `p2p.amount.min` | BR-05, FR-14 | Product |

**Mâu thuẫn đã phát hiện và xử lý khi viết spec:** [api §1] bảng mã lỗi từng liệt kê `INSUFFICIENT_BALANCE`/`LIMIT_EXCEEDED` ở HTTP 422, trái với quy ước "lệnh đã tạo thì trả 200 kèm `failureCode`" của chính tài liệu đó và của design §6.3. Đã sửa api.md theo quy ước 200; spec này dùng `order.failureCode`.

## 10. Lịch sử thay đổi
| Phiên bản | Ngày | Thay đổi | Lý do |
|---|---|---|---|
| 0.1 | 2026-11-04 | Tạo mới | Spec đầu tiên, chạy thử quy trình `srs-feature-spec` |
| 0.2 | 2026-11-04 | Gán ID `ST-01..04` (§3.4) và `EV-01..04` (§3.6); thêm `ST-04` cho chuyển bị cấm; thêm AC-41 (ST-04), AC-42 (EV-04) vì hai mục này chưa có AC. Nội dung hành vi không đổi | Template mới yêu cầu ID cho bảng trạng thái và đầu ra phụ để test case truy vết được |
