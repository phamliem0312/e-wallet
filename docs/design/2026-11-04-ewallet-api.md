# EWallet — API, Event & Provider interface

Đi kèm [thiết kế chính](2026-11-04-ewallet-design.md). Đây là hợp đồng ở mức thiết kế. Khi code, nguồn sự thật là thư mục [`source-code/contracts/`](../../source-code/contracts/): OpenAPI cho từng service và JSON Schema cho event. Frontend và các service sinh client từ đó.

**Đã có hợp đồng (v0.1, phần F-PAY-03, lint qua 2026-11-04):** `openapi/common.yaml`, `openapi/payment/public.yaml` (`/transfers/preview`, `/transfers`, `/orders/{orderId}`), `openapi/wallet/public.yaml` (`/wallet`, `/transfers/recipient`), `openapi/identity/public.yaml` (`/challenges`), `events/payment/PaymentSucceeded/v1.json`, `events/payment/PaymentFailed/v1.json`.

## 1. Quy ước chung

- Base path:
  - `/api/v1` (khách hàng, qua `api-gateway`).
  - `/admin/v1` (nhân viên, qua `admin-gateway`).
  - `/callbacks/{bankCode}` (bên thứ 3, ingress riêng vào thẳng `bank-gateway`).
  - `/internal/**` (giữa các service, **không bao giờ** đi qua gateway).
- Gateway định tuyến theo prefix tới service sở hữu. Cột "Service" trong các bảng dưới cho biết service nào xử lý.
- JSON camelCase. Tiền là **số nguyên đồng** (`"amount": 100000`), không dùng số thập phân.
- Thời gian ISO-8601 có offset.
- Header:
  - `Authorization: Bearer <jwt>`: do BFF gắn vào, trình duyệt không tự gửi.
  - `Idempotency-Key: <uuid>`: **bắt buộc** cho mọi `POST` tạo lệnh tiền; dùng được trong 24 giờ.
  - `traceparent`: W3C trace context.
- Phân trang: `?limit=20&cursor=<opaque>` → `{ items: [...], nextCursor: "..." | null }`.
- Lỗi: RFC 9457. Trường `params` mang dữ liệu để frontend dựng thông điệp (ví dụ `AMOUNT_OUT_OF_RANGE` → `minAmount`; `CHALLENGE_INVALID` → `attemptsRemaining`).
  ```json
  { "type": "https://ewallet/errors/challenge-invalid", "title": "Mã xác thực không hợp lệ",
    "status": 422, "code": "CHALLENGE_INVALID", "traceId": "4bf9...", "detail": "..." }
  ```

### Quy ước response của lệnh tiền
- **Bị từ chối trước khi order được tạo** (validation, `CHALLENGE_INVALID`, `FEE_CHANGED`, ví không `ACTIVE`, `BANK_UNAVAILABLE`, rate limit): trả 4xx/503 và không lưu gì. Khách sửa rồi gửi lại với **cùng** key được.
- **Order đã được tạo:** luôn trả `200` (`SUCCEEDED`/`FAILED`) hoặc `202` (`PROCESSING`/`UNKNOWN`) kèm `order`. Lý do thất bại nằm ở `order.failureCode` (`INSUFFICIENT_BALANCE`, `LIMIT_EXCEEDED`, `BANK_REJECTED`...). Gửi lại cùng key → nhận đúng order đó.
- **Frontend:** sau khi order `FAILED`, phải **sinh `Idempotency-Key` mới** cho lần thử tiếp theo. Nếu giữ key cũ, khách sẽ chỉ nhận lại kết quả `FAILED` cũ.

### Mã lỗi nghiệp vụ

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR`, `IDEMPOTENCY_KEY_REQUIRED` | Input sai hoặc thiếu |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED` | |
| 403 | `FORBIDDEN`, `KYC_REQUIRED`, `WALLET_FROZEN` | |
| 404 | `NOT_FOUND`, `RECIPIENT_NOT_FOUND` | |
| 409 | `IDEMPOTENCY_KEY_REUSED`, `FEE_CHANGED`, `STATE_CONFLICT` | |
| 422 | `AMOUNT_OUT_OF_RANGE`, `RECIPIENT_UNAVAILABLE`, `CHALLENGE_REQUIRED`, `CHALLENGE_INVALID`, `BANK_LINK_INVALID`, `HOLDER_NAME_MISMATCH` | Từ chối trước khi tạo order |
| (200, trong `order.failureCode`) | `INSUFFICIENT_BALANCE`, `LIMIT_EXCEEDED`, `BANK_REJECTED`... | Order đã tạo và `FAILED`; xem "Quy ước response của lệnh tiền". Chỉ các API *nội bộ* (`/internal/**`) trả 422 cho hai mã đầu |
| 429 | `RATE_LIMITED` | Kèm header `Retry-After` |
| 503 | `BANK_UNAVAILABLE` | Ngân hàng bảo trì hoặc circuit đang mở |

---

## 2. API khách hàng (`/api/v1`)

Routing của `api-gateway`:

| Prefix | Service |
|---|---|
| `/auth/**`, `/me/**`, `/challenges` | `identity` |
| `/kyc/**` | `kyc` |
| `/wallet`, `/wallet/close`, `/transfers/recipient` | `wallet` |
| `/wallet/limits` | `limit` |
| `/banks`, `/bank-links/**` | `bank-gateway` |
| `/topups/**`, `/withdrawals/**`, `/transfers/**` (trừ `/transfers/recipient`), `/orders/**`, `/activities` | `payment` |
| `/disputes/**` | `dispute` |
| `/notifications/**` | `notification` |

`GET /wallet` trả số dư: `wallet` gọi `ledger` để lấy số dư (số dư chỉ có ở `ledger`).

### Auth & identity
| Method | Path | Idempotent | Mô tả |
|---|---|---|---|
| POST | `/auth/register` | — | `{phone}` → gửi OTP, trả `challengeId` |
| POST | `/auth/register/verify` | — | `{challengeId, otp, password, fullName}` → tạo khách, trả token |
| POST | `/auth/login` | — | `{phone, password, deviceFingerprint}` → token, hoặc `CHALLENGE_REQUIRED` + `challengeId` nếu thiết bị lạ |
| POST | `/auth/login/verify` | — | `{challengeId, otp}` → token |
| POST | `/auth/refresh` | — | Xoay refresh token |
| POST | `/auth/logout` | — | Thu hồi token family hiện tại |
| GET | `/me` | — | Hồ sơ, KYC tier, trạng thái |
| GET | `/me/devices` · DELETE `/me/devices/{id}` | — | Quản lý thiết bị |
| GET | `/me/consents` · POST `/me/consents` | — | Xem/cập nhật đồng ý theo từng mục đích (`{purpose, termsVersion, granted}`) |
| POST | `/challenges` | — | `{purpose, payload}` → `201 {challengeId, factor: OTP\|FACE, expiresAt, destinationMasked?, resendAfter?, attemptsRemaining?}` (3 trường cuối là đề xuất cho OQ-019). `payload` là nội dung lệnh, server tự hash |

### KYC
| Method | Path | Mô tả |
|---|---|---|
| POST | `/kyc/applications` | multipart: `idFront`, `idBack`, `selfie` (hoặc video liveness) → `{id, status}` |
| GET | `/kyc/applications/current` | Trạng thái hồ sơ, tier đã cấp, lý do nếu bị từ chối |

### Ví & lịch sử
| Method | Path | Mô tả |
|---|---|---|
| GET | `/wallet` | `{walletId, status, balance, held, available, kycTier}` — đọc từ primary |
| GET | `/wallet/limits` | Hạn mức còn lại theo loại và kỳ |
| GET | `/activities?type=&status=&from=&to=&cursor=` | Lịch sử từ `wallet_activity` |
| GET | `/orders/{id}` | Chi tiết lệnh (FE dùng để poll khi lệnh đang `PROCESSING`/`UNKNOWN`) |

### Liên kết ngân hàng
| Method | Path | Mô tả |
|---|---|---|
| GET | `/banks` | Danh sách ngân hàng đang hoạt động |
| GET | `/bank-links` | Các liên kết của khách |
| POST | `/bank-links` | `{bankCode, sourceType, accountOrCardNumber}` → `{sessionId, next: OTP\|REDIRECT, redirectUrl?}` |
| POST | `/bank-links/{sessionId}/confirm` | `{otp}` → liên kết `ACTIVE` hoặc lỗi `HOLDER_NAME_MISMATCH` |
| DELETE | `/bank-links/{id}` | Huỷ liên kết |

### Lệnh tiền (đều theo mẫu preview → tạo lệnh)
| Method | Path | Idempotency-Key | Request chính | Response |
|---|---|---|---|---|
| POST | `/topups/preview` | — | `{bankLinkId, amount}` | `{fee, total, requiredFactor}` |
| POST | `/topups` | ✅ | `{bankLinkId, amount, expectedFee, challengeId?, otp?}` | `200 {order}` (SUCCEEDED/FAILED) hoặc `202 {order}` (PROCESSING) |
| POST | `/withdrawals/preview` | — | `{bankLinkId, amount}` | như trên |
| POST | `/withdrawals` | ✅ | như `/topups` | như trên |
| GET | `/transfers/recipient?phone=` | — | | `{displayName: "NGUYEN V*** A"}`; có rate limit để chống dò tìm người dùng. Không trả id ví người nhận (không màn hình nào cần) |
| POST | `/transfers/preview` | — | `{recipientPhone, amount}` | `{fee, total, recipientDisplay, requiredFactor}` |
| POST | `/transfers` | ✅ | `{recipientPhone, amount, note?, expectedFee, challengeId?, otp?}` | `200 {order}` |

`order` = `{id, type, status, amount, fee, currency, createdAt, counterpartyDisplay?, failureCode?, failureDetail?, bankRef?}`. `counterpartyDisplay` (OQ-021) và `failureDetail` (ví dụ `{scope, remaining}` cho `LIMIT_EXCEEDED`, OQ-020) là đề xuất. `type`, `status`, `failureCode` là enum có thể mở rộng.

### Khác
| Method | Path | Mô tả |
|---|---|---|
| POST | `/disputes` · GET `/disputes` · GET `/disputes/{id}` | Tra soát gắn với `orderId` |
| GET | `/notifications` · POST `/notifications/{id}/read` | Inbox trong web |
| POST | `/wallet/close` | Đóng ví; chỉ khi `balance = 0` và không còn hold hay lệnh treo |

---

## 3. API back-office (`/admin/v1`)

Mọi endpoint đều yêu cầu role tương ứng và ghi audit. ✍️ = tạo approval request ở `approval` (maker), không thực thi ngay. Khi được duyệt, service chủ mới thực thi qua event `ApprovalApproved`.

`cms` BFF ghép dữ liệu từ nhiều service cho các màn hình tổng hợp (ví dụ trang "Khách hàng 360" gọi `identity`, `kyc`, `wallet`, `payment`, `bank-gateway` song song).

| Nhóm | Endpoint | Service | Role |
|---|---|---|---|
| Khách hàng | `GET /customers?phone=`, `GET /customers/{id}`, `POST /customers/{id}/reveal` | `identity` | CS_AGENT, RISK |
| | `GET /kyc/applications?idNumber=` (server tự hash số CCCD) | `kyc` | |
| Ví | `GET /wallets/{id}`, `POST /wallets/{id}/freeze` ✍️, `/unfreeze` ✍️, `/partial-freeze` ✍️, `/close` | `wallet` | OPS, RISK |
| | `POST /wallets/{id}/adjustments` ✍️ (thực thi ở `ledger`) | `wallet` → `approval` | OPS |
| Lệnh | `GET /orders?status=&bank=&from=&to=`, `GET /orders/{id}`, `POST /orders/{id}/manual-resolution` ✍️ | `payment` | OPS |
| | `GET /bank-calls?externalRef=` | `bank-gateway` | OPS |
| KYC | `GET /kyc/reviews?status=PENDING_REVIEW`, `POST /kyc/applications/{id}/approve` / `reject` | `kyc` | RISK |
| AML | `GET /aml/hits`, `POST /aml/blacklist` ✍️, `GET /aml/alerts?status=`, `POST /aml/alerts/{id}/assign` / `dismiss` / `report`, `GET/POST /aml/rules` ✍️ | `aml` | RISK |
| Tra soát | `GET /disputes?status=&assignee=`, `POST /disputes/{id}/assign`, `/events`, `/resolve` | `dispute` | CS_AGENT |
| Đối soát | `GET /recon/runs`, `GET /recon/discrepancies?status=OPEN`, `POST /recon/discrepancies/{id}/resolve` ✍️, `POST /recon/runs` | `recon` | RECON |
| TK đảm bảo | `GET /safeguard`, `POST /safeguard/rebalances` ✍️ (thực thi ở `ledger`) | `recon` | FINANCE |
| Phí | `GET/POST /fee-rules` ✍️ | `fee` | FINANCE |
| Hạn mức | `GET/POST /limit-rules` ✍️, `GET/POST /stepup-thresholds` ✍️ | `limit` | RISK |
| Cấu hình khác | `GET/POST /sla-config` ✍️ | `dispute` | ADMIN |
| | `GET/POST /notification-templates` ✍️ | `notification` | ADMIN |
| Phê duyệt | `GET /approvals?status=PENDING`, `POST /approvals/{id}/approve` / `reject` | `approval` | Checker có cùng nhóm quyền, khác maker |
| Báo cáo | `GET /reports/daily?date=`, `GET /reports/{type}/export.csv` | `recon` | OPS, FINANCE |
| Audit | `GET /audit?actor=&target=&from=&to=` | `audit` | ADMIN |
| Vận hành | `GET /ops/dlt`, `POST /ops/dlt/{id}/replay` | từng service (qua `platform` starter) | ADMIN |

---

## 4. Callback (`/callbacks/{bankCode}`)

- `POST /callbacks/{bankCode}`: body nguyên văn được đưa cho `BankGateway.parseCallback`.
  - Chữ ký sai → `401` và ghi log bảo mật.
  - Chữ ký đúng → insert `callback_inbox`. Trùng `dedup_key` → `200` luôn. Sau đó xử lý bất đồng bộ.
- Định dạng response theo hợp đồng của từng ngân hàng; adapter quyết định nội dung trả về.

---

## 5. API nội bộ (`/internal/**`)

Chỉ các service được liệt kê ở cột "Được gọi bởi" mới gọi được (AuthorizationPolicy của mesh). Mọi lệnh có tác dụng phụ đều idempotent theo id trong request; gọi lại với cùng id và cùng nội dung sẽ trả kết quả cũ.

| Service | Endpoint | Idempotent theo | Được gọi bởi | Kết quả / lỗi |
|---|---|---|---|---|
| `ledger` | `POST /internal/accounts` `{code, category, ...}` | `code` | `wallet`, `bank-gateway` | Tài khoản |
| | `POST /internal/accounts/{code}/blocks` `{debitBlocked, creditBlocked}` | trạng thái đích | `wallet` | |
| | `GET /internal/accounts/{code}/balance` | — | `wallet`, `payment`, `recon` | `{balance, held, available}` |
| | `POST /internal/journals` `{referenceType, referenceId, purpose, entries[]}` | `(referenceType, referenceId, purpose)` | `payment`; `ledger` tự gọi khi thực thi approval | `200 journal` / `422 INSUFFICIENT_BALANCE`, `ACCOUNT_BLOCKED`, `UNBALANCED` / `409` nếu nội dung khác |
| | `GET /internal/journals?referenceId=` | — | `payment`, `recon` | Danh sách journal |
| | `POST /internal/holds` `{referenceId, accountCode, amount, reason}` | `referenceId` | `payment`, `wallet` | `200 hold` / `422 INSUFFICIENT_BALANCE` |
| | `POST /internal/holds/{referenceId}/capture` `{journal}` | `referenceId` | `payment` | |
| | `POST /internal/holds/{referenceId}/release` | `referenceId` | `payment`, `wallet` | |
| | `GET /internal/reports/trial-balance?asOf=` | — | `recon` | Σ D, Σ C, Σ số dư ví |
| `limit` | `POST /internal/reservations` `{id=orderId, customerId, walletId, txType, amount, at}` | `id` | `payment` | `200 RESERVED` / `422 LIMIT_EXCEEDED {scope, remaining}` |
| | `GET /internal/limits/{walletId}/remaining` | — | `payment` (preview) | |
| `fee` | `POST /internal/quotes` `{txType, amount, bankCode?, at}` | — | `payment` | `{fee, feeRuleId}` |
| `identity` | `POST /internal/challenges/{id}/verify` `{otp, payloadHash, consumerRef}` | `(id, consumerRef)`: challenge chỉ phục vụ **một** `consumerRef` (idempotency key của lệnh); verify lại với cùng ref → `200` | `payment`, `bank-gateway` | `200` / `422 CHALLENGE_INVALID` |
| `wallet` | `GET /internal/recipients?phone=` | — | `payment` | `{walletId, ledgerAccountCode, status, displayMasked}` |
| | `GET /internal/wallets/{id}` | — | `payment`, `bank-gateway` | |
| `payment` | `GET /internal/orders/open?walletId=` | — | `wallet` (đóng ví) | Số lệnh `PROCESSING`/`UNKNOWN` |
| | `GET /internal/orders/{id}` | — | `dispute` | |
| `bank-gateway` | `POST /internal/transfers/debit` · `/credit` `{externalRef, customerId, bankLinkId, amount}` | `externalRef` (bảng `bank_transfer`, ghi trước khi gọi ngân hàng) | `payment` | `Success / Failed / Pending / Unknown` (HTTP 200 cho cả 4 trường hợp). Link không thuộc `customerId` hoặc không `ACTIVE` → `Failed(BANK_LINK_INVALID)`; ref đã `VOIDED` → `Failed(VOIDED)` |
| | `GET /internal/transfers/{externalRef}` | — | `payment` (resolver) | `Success / Failed / Pending / Unavailable`, hoặc `NotFoundLocal` (bank-gateway chưa từng nhận lệnh) / `NotFoundAtBank` |
| | `POST /internal/transfers/{externalRef}/void` | `externalRef` | `payment` (resolver) | `VOIDED` nếu chưa từng nhận lệnh; nếu đã nhận thì trả trạng thái hiện tại và không thay đổi gì |
| | `GET /internal/recon-records?bankCode=&date=` | — | `recon` | |
| | `GET /internal/safeguard-balances` | — | `recon` | Số dư thực của từng ngân hàng |
| `kyc` | `POST /internal/face-verifications` `{verificationId, customerId, selfie}` | `verificationId` | `identity` (step-up `FACE`) | `{passed, livenessScore, matchScore}` |
| `aml` | `POST /internal/screenings` `{subjectRef, fullName, dob, idNumber, nationality}` | `subjectRef` | `kyc` | `Clear / PotentialMatch / Error` |
| `notification` | `POST /internal/otp-messages` `{idempotencyKey, channel, recipient, code}` | `idempotencyKey` | `identity` | `202`. Chỉ dành cho OTP; mã không được ghi log hay lưu dạng rõ |

---

## 6. Danh mục event (Kafka)

Mỗi service publish vào topic `<service>.events`, key = id của aggregate. Envelope theo CloudEvents:

```json
{ "specversion": "1.0", "id": "0193f...", "source": "payment", "type": "PaymentSucceeded",
  "time": "2026-11-04T08:00:00Z", "subject": "<orderId>", "aggregateversion": 3, "traceparent": "00-...",
  "dataschema": "contracts/events/payment/PaymentSucceeded/v1.json", "data": { ... } }
```

| Topic | Event | Dữ liệu chính | Consumer |
|---|---|---|---|
| `identity.events` | `CustomerRegistered`, `CustomerUpdated`, `CustomerLocked` | `customerId`, `phone`, `displayMasked` | `wallet` (customer_directory + mở ví), `audit` |
| `kyc.events` 🔒 | `KycTierChanged` | `customerId`, `walletId?`, `tier`, `fullNameNorm`, `idNumberHash` | `identity`, `limit`, `bank-gateway`, `wallet`, `notification`, `audit` |
| | `KycRejected` | `customerId`, `reasonCode` | `notification`, `audit` |
| `aml.events` | `AmlHitDetected` | `subjectRef`, `listNames` | `kyc`, `audit` |
| `wallet.events` | `WalletOpened`, `WalletFrozen`, `WalletUnfrozen`, `WalletClosing`, `WalletClosed` | `walletId`, `customerId`, `reason` | `kyc` (`WalletClosed` → hồ sơ `CLOSED`), `notification`, `audit`, `recon` |
| `ledger.events` | `JournalPosted` | `journalId`, `referenceId`, `purpose`, `entries[]` | `recon`, `audit` |
| | `HoldPlaced`, `HoldCaptured`, `HoldReleased` | `referenceId`, `accountCode`, `amount` | `recon` |
| `payment.events` | `PaymentSucceeded`, `PaymentFailed`, `PaymentUnknown` | `orderId`, `type`, `customerId`, `walletId`, `counterpartyWalletId`, `amount`, `fee`, `currency`, `bankCode`, `externalRef`, `failureCode`, `createdAt` ([schema](../../source-code/contracts/events/payment/)) | `limit` (commit/release), `aml` (giám sát giao dịch), `notification`, `recon`, `audit` |
| `bank.events` | `BankTransferOutcome` | `externalRef`, `result`, `bankRef` | `payment` |
| | `BankLinkActivated`, `BankLinkRevoked` | `linkId`, `customerId`, `bankCode` | `notification`, `audit` |
| `limit.events`, `fee.events` | `LimitRuleChanged`, `FeeRuleChanged` | rule mới | `payment` (xoá cache), `audit` |
| `approval.events` | `ApprovalApproved`, `ApprovalRejected` | `approvalId`, `type`, `payload` | service chủ của `type`, `audit` |
| `<service>.events` | `ApprovalExecuted`, `ApprovalExecutionFailed` | `approvalId`, `result` | `approval` |
| `dispute.events` | `DisputeOpened`, `DisputeResolved` | `disputeId`, `orderId` | `notification`, `audit` |
| `audit.records` | `AuditRecorded` | `actor`, `action`, `target`, `before`, `after`, `ip`, `traceId` | `audit` (topic 1 partition để chuỗi hash có thứ tự) |

🔒 Topic chứa dữ liệu cá nhân, có ACL chỉ cho các consumer được liệt kê.

**Quy tắc tiến hoá schema:** chỉ được thêm trường optional. Đổi nghĩa hoặc xoá trường → tạo `v2` và publish song song cả hai phiên bản trong thời gian chuyển đổi. CI kiểm tra tương thích ngược trên Schema Registry.

---

## 7. Provider interface (Java)

Đặt trong package `provider` của service sở hữu: `bank-gateway` (BankGateway), `kyc` (EkycProvider), `notification` (NotificationSender), `aml` (AmlScreeningProvider). Kiểu dùng chung (`Money`, `FakeProvider`) nằm trong `platform`.

### 7.1 Quy ước chung

```java
// shared
public record Money(long amount, String currency) {
    public Money { if (amount < 0) throw new IllegalArgumentException("amount < 0"); Objects.requireNonNull(currency); }
    public static Money vnd(long amount) { return new Money(amount, "VND"); }
}

/** Marker: bean implement interface này bị cấm ở profile prod (kiểm lúc khởi động). */
public interface FakeProvider {}
```

Quy tắc cho mọi adapter, lấy từ PR-01 đến PR-07 trong tài liệu yêu cầu:
- **Lệnh có tác dụng phụ không được throw khi gặp lỗi kỹ thuật.** Adapter tự bắt timeout và lỗi IO rồi trả về `Unknown`. Lý do: nếu throw, tầng nghiệp vụ dễ coi đó là lỗi và hoàn tiền sai.
- Exception chỉ dùng cho lỗi lập trình hoặc cấu hình (ví dụ thiếu credential), không dùng cho kết quả nghiệp vụ.
- Adapter ghi `bank_call_log` (hoặc log tương đương) qua `ProviderCallRecorder` dùng chung.

### 7.2 `BankGateway` (service `bank-gateway`)

```java
public interface BankGateway {
    String bankCode();
    BankCapabilities capabilities();

    LinkInitResult initiateLink(LinkInitRequest req);
    LinkConfirmResult confirmLink(String linkSessionRef, String otp);
    UnlinkResult unlink(String linkToken);

    /** Nạp ví: trích nợ TK khách → TK đảm bảo. Không throw khi gặp lỗi kỹ thuật. */
    TransferResult debit(BankTransferRequest req);
    /** Rút ví: TK đảm bảo → ghi có TK khách. Không throw khi gặp lỗi kỹ thuật. */
    TransferResult credit(BankTransferRequest req);

    StatusResult queryStatus(String externalRef);
    BankCallbackEvent parseCallback(Map<String, List<String>> headers, byte[] body) throws InvalidSignatureException;
    List<ReconRecord> fetchReconciliation(LocalDate businessDate);
    Money getSafeguardBalance();
}

public record BankCapabilities(boolean queryStatus, boolean callback, boolean reconFile, boolean safeguardBalanceApi) {}

public record LinkInitRequest(UUID customerId, String holderName, String idNumber, SourceType sourceType, String accountOrCard) {}
public enum SourceType { ACCOUNT, DEBIT_CARD }

public sealed interface LinkInitResult {
    record PendingOtp(String linkSessionRef) implements LinkInitResult {}
    record Redirect(String linkSessionRef, URI url) implements LinkInitResult {}
    record Failed(String reasonCode, String message) implements LinkInitResult {}
}
public sealed interface LinkConfirmResult {
    record Success(String linkToken, String holderName, String accountMasked, String holderIdNumber /* nullable */) implements LinkConfirmResult {}
    record Failed(String reasonCode, String message) implements LinkConfirmResult {}
}
public sealed interface UnlinkResult {
    record Success() implements UnlinkResult {}
    record Failed(String reasonCode) implements UnlinkResult {}
}

public record BankTransferRequest(String externalRef, String linkToken, Money amount, String description) {}

public sealed interface TransferResult {
    record Success(String bankRef) implements TransferResult {}
    /** Ngân hàng từ chối rõ ràng: chắc chắn chưa có tiền dịch chuyển. */
    record Failed(String reasonCode, String message) implements TransferResult {}
    record Pending(String bankRef) implements TransferResult {}
    /** Không biết kết quả (timeout, mất kết nối, 5xx). KHÔNG được coi là thất bại. */
    record Unknown(String cause) implements TransferResult {}
}

public sealed interface StatusResult {
    record Success(String bankRef) implements StatusResult {}
    record Failed(String reasonCode) implements StatusResult {}
    record Pending() implements StatusResult {}
    record NotFound() implements StatusResult {}
    record Unavailable(String cause) implements StatusResult {}
}

public sealed interface BankCallbackEvent {
    String dedupKey();
    record TransferOutcome(String dedupKey, String externalRef, StatusResult result) implements BankCallbackEvent {}
    record LinkRevoked(String dedupKey, String linkToken) implements BankCallbackEvent {}
}

public record ReconRecord(String externalRef, String bankRef, Money amount, Direction direction, String status, Instant bankTime) {}
public enum Direction { DEBIT_CUSTOMER, CREDIT_CUSTOMER }
```

Registry: `BankGatewayRegistry` nhận `List<BankGateway>` qua Spring và tra cứu theo `bankCode()`; thiếu ngân hàng → lỗi cấu hình lúc khởi động.

**`FakeBankGateway`** (`implements BankGateway, FakeProvider`):
- Mặc định mọi lệnh thành công.
- Test đặt kịch bản cho từng lời gọi qua `FakeBankGateway.script(externalRef or amount → Outcome)`, ví dụ: số tiền kết thúc bằng `...13` thì trả `Unknown`, `...99` thì trả `Failed`. Như vậy QA test được trên giao diện dev mà không cần sửa code.
- Lưu lệnh trong bộ nhớ để `queryStatus` và `fetchReconciliation` trả lời nhất quán. Có hàm `emitCallback(...)` cho test và `corruptReconRecord(...)` để giả lập lệch đối soát.

### 7.3 `EkycProvider` (service `kyc`)

```java
public interface EkycProvider {
    String name();
    Set<EkycCapability> capabilities();   // OCR, LIVENESS, FACE_MATCH, NATIONAL_DB

    OcrResult ocrIdCard(Image front, Image back);
    LivenessResult checkLiveness(Media selfie);
    FaceMatchResult matchFace(Image idPortrait, Image selfie);
    /** Chỉ gọi khi capabilities() có NATIONAL_DB. */
    NationalDbResult verifyWithNationalDb(String idNumber, String fullName, LocalDate dob);
}

public record Image(byte[] bytes, String contentType) {}
public record Media(byte[] bytes, String contentType) {}

public sealed interface OcrResult {
    record Success(IdCardData data, Map<String, Double> fieldConfidence, boolean tamperSuspected) implements OcrResult {}
    record Failed(String reasonCode) implements OcrResult {}
    record Unavailable(String cause) implements OcrResult {}
}
public record IdCardData(String idNumber, String fullName, LocalDate dob, String gender, String address, LocalDate issueDate, LocalDate expiryDate) {}

public sealed interface LivenessResult {
    record Live(double score) implements LivenessResult {}
    record Spoof(double score, String kind) implements LivenessResult {}
    record Unavailable(String cause) implements LivenessResult {}
}
public sealed interface FaceMatchResult {
    record Scored(double score) implements FaceMatchResult {}
    record Unavailable(String cause) implements FaceMatchResult {}
}
public enum NationalDbResult { MATCH, MISMATCH, UNAVAILABLE }
```
Kết quả `Unavailable` ở bất kỳ bước nào → hồ sơ chuyển `PENDING_REVIEW` (hoặc cho khách thử lại); không tự động từ chối.

### 7.4 `NotificationSender` (service `notification`)

```java
public interface NotificationSender {
    Set<Channel> channels();
    SendResult send(OutboundMessage msg);
}
public enum Channel { EMAIL, SMS, WEB_PUSH }
public record OutboundMessage(String idempotencyKey, Channel channel, String recipient, String subject, String body) {}
public sealed interface SendResult {
    record Accepted(String providerMessageId) implements SendResult {}
    record Rejected(String reasonCode) implements SendResult {}      // không retry (số điện thoại sai...)
    record Failed(String cause) implements SendResult {}             // retry có backoff
}
```
- `notification` render template thành `subject`/`body` trước khi gọi, nên provider không cần biết template.
- Mỗi kênh chọn một provider qua cấu hình `ewallet.notification.channels.SMS=fake`.
- **`FakeNotificationSender`:** lưu tin vào bộ nhớ, có `lastMessageTo(recipient)` để test E2E đọc OTP. Profile `dev` có thêm endpoint `/dev/outbox` cho QA xem tin đã gửi (endpoint này chỉ có ở profile dev).

### 7.5 `AmlScreeningProvider` (service `aml`)

```java
public interface AmlScreeningProvider {
    ScreeningResult screen(ScreeningSubject subject);
}
public record ScreeningSubject(String fullName, LocalDate dob, String idNumber, String nationality) {}
public sealed interface ScreeningResult {
    record Clear(String screeningRef) implements ScreeningResult {}
    record PotentialMatch(String screeningRef, List<WatchlistHit> hits) implements ScreeningResult {}
    record Error(String cause) implements ScreeningResult {}
}
public record WatchlistHit(String listName, String matchedName, double score) {}
```
`PotentialMatch` hoặc `Error` → duyệt thủ công. Danh sách đen nội bộ (`aml.internal_blacklist`) luôn được kiểm, độc lập với provider.

### 7.6 Contract test

Mỗi interface có một abstract test class (ví dụ `BankGatewayContractTest`) gồm các kiểm tra:
- `debit` gọi hai lần cùng `externalRef` chỉ dịch chuyển tiền một lần.
- `queryStatus` khớp với kết quả `debit`.
- Callback trùng có cùng `dedupKey`.
- Lỗi kỹ thuật trả về `Unknown`, không throw.

`FakeBankGateway` và mọi adapter thật sau này đều extend class này và phải pass (PR-07).
