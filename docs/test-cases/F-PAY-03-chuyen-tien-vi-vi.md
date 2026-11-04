# Test case — F-PAY-03 Chuyển tiền ví–ví

- Nguồn: [SRS F-PAY-03](../srs/features/F-PAY-03-chuyen-tien-vi-vi.md) v0.2 (FR, BR, ST, EV, NFR, AC) · [UI F-PAY-03](../ui/features/F-PAY-03-chuyen-tien-vi-vi.md) v0.1 · [data model §payment](../design/2026-11-04-ewallet-data-model.md) · [api](../design/2026-11-04-ewallet-api.md) §1–§2
- Ngày: 2026-11-04
- Phạm vi: F-PAY-03.FR-01..32, BR-01..08, ST-01..04, EV-01..04, NFR-01..07, AC-01..42; quy tắc UI ở UI §4.5; ràng buộc DB của `payment`.

## Tóm tắt
| Mức | Số TC |
|---|---|
| UNIT (backend) | 7 |
| UNIT (frontend) | 3 |
| INTEGRATION | 10 |
| API | 26 |
| E2E | 6 (1 BLOCKED) |
| NFR | 3 |
| **Cộng** | **55** |

## Dữ liệu & môi trường test

**Cấu hình mặc định** (lấy từ SRS §7; là giá trị để test, không phải giá trị đã chốt):

| Tham số | Giá trị test |
|---|---|
| `fee.p2p` | phí cố định 1.000 ₫ (trừ khi TC nói khác) |
| `limit.p2p.per_txn` / `limit.p2p.per_day` | 5.000.000 / 10.000.000 |
| `stepup.p2p.otp_threshold` / `face_threshold` | 2.000.000 / 20.000.000 |
| `stepup.p2p.day_otp_threshold` / `day_face_threshold` | chưa có giá trị trong SRS → ký hiệu `D_OTP` / `D_FACE` |
| `p2p.amount.min` / `p2p.note.max_length` | 1.000 / 140 |
| `p2p.min_kyc_tier` | ký hiệu `T_MIN` (SRS Q2) |
| `ratelimit.recipient_lookup` | ký hiệu `N` lần trong cửa sổ `W` |

**Môi trường:**
- Ví được tạo bằng helper qua API (nạp tiền bằng `FakeBankGateway` ở profile test). Mỗi TC dùng khách và ví riêng để chạy song song được.
- Đồng hồ hệ thống điều khiển được (inject `Clock`) cho TC kỳ ngày.
- OTP lấy bằng `FakeNotificationSender.lastMessageTo(...)`.
- **Ép lỗi giữa saga** (TC-013, 039, 054): cần cơ chế fault injection ở profile test, xem TQ3.

## Test case

### UNIT (backend)

### TC-P2P-001 — Phí P2P được kẹp trong [min, max] và làm tròn lên
- **Yêu cầu:** F-PAY-03.BR-01 · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Tiền điều kiện:** rule P2P: `percent_bp = 10` (0,1%), `fixed_fee = 0`, `min_fee = 1.000`, `max_fee = 20.000`

| # | amount | Phí mong đợi | Lý do |
|---|---|---|---|
| a | 1 | 1.000 | dưới min |
| b | 1.000.000 | 1.000 | đúng min |
| c | 1.000.001 | 1.001 | ⌈1.000,001⌉, làm tròn lên |
| d | 20.000.000 | 20.000 | đúng max |
| e | 20.000.001 | 20.000 | trên max |

- **Tự động hoá:** (chưa viết) `payment` › `P2pFeeQuoteTest`

### TC-P2P-002 — Xác định `requiredFactor` theo ngưỡng
- **Yêu cầu:** F-PAY-03.BR-07, FR-16 · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** BVA, bảng quyết định
- **Tiền điều kiện:** tổng đã chuyển trong ngày = 0 (dòng a–d)

| # | amount | Tổng ngày trước đó | Mong đợi |
|---|---|---|---|
| a | 2.000.000 | 0 | `NONE` (không vượt) |
| b | 2.000.001 | 0 | `OTP` |
| c | 20.000.000 | 0 | `OTP` |
| d | 20.000.001 | 0 | `FACE` |
| e | 1.000 | `D_OTP − 1.000` | `NONE` (tổng = `D_OTP`) |
| f | 1.001 | `D_OTP − 1.000` | `OTP` |
| g | 1.001 | `D_FACE − 1.000` | `FACE` |

- **Trạng thái:** dòng a–d sẵn sàng; dòng e–g **BLOCKED (Q1)**, chờ SRS chốt có tính tổng ngày không và giá trị ngưỡng.
- **Tự động hoá:** (chưa viết) `payment` › `StepUpPolicyTest`

### TC-P2P-003 — Thứ tự ưu tiên mã lỗi khi vi phạm nhiều điều kiện
- **Yêu cầu:** F-PAY-03.BR-08 · **Mức:** UNIT · **Ưu tiên:** P2 · **Kỹ thuật:** bảng quyết định

| # | Vi phạm đồng thời | Mã mong đợi |
|---|---|---|
| a | amount = "abc" + ví người gửi FROZEN | `VALIDATION_ERROR` |
| b | Khoá đã có lệnh + challenge sai + ví FROZEN | Trả lại lệnh cũ (không lỗi) |
| c | Challenge thiếu + ví FROZEN | `CHALLENGE_REQUIRED` |
| d | Ví người gửi FROZEN + người nhận không có ví | `WALLET_FROZEN` |
| e | Người nhận FROZEN + `expectedFee` sai | `RECIPIENT_UNAVAILABLE` |
| f | `expectedFee` sai + amount = 999 | `FEE_CHANGED` |

- **Tự động hoá:** (chưa viết) `payment` › `TransferPreconditionsOrderTest`

### TC-P2P-004 — Validation số tiền
- **Yêu cầu:** F-PAY-03.FR-14, BR-05 · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** EP, BVA · **AC:** AC-25

| # | amount | Mong đợi |
|---|---|---|
| a | 0 | `VALIDATION_ERROR` |
| b | -1 | `VALIDATION_ERROR` |
| c | 1000.5 | `VALIDATION_ERROR` |
| d | "abc" | `VALIDATION_ERROR` |
| e | 999 | `AMOUNT_OUT_OF_RANGE` |
| f | 1000 | hợp lệ |

- **Tự động hoá:** (chưa viết) `payment` › `TransferRequestValidationTest`

### TC-P2P-005 — Validation lời nhắn
- **Yêu cầu:** F-PAY-03.FR-31, FR-14 · **Mức:** UNIT · **Ưu tiên:** P3 · **Kỹ thuật:** BVA · **AC:** AC-26

| # | note | Mong đợi |
|---|---|---|
| a | (không có) | hợp lệ |
| b | 140 ký tự | hợp lệ |
| c | 141 ký tự | `VALIDATION_ERROR` |
| d | "abc\u0007def" (ký tự điều khiển) | `VALIDATION_ERROR` |
| e | 140 ký tự có dấu tiếng Việt ("ạ" × 140) | hợp lệ (đếm theo ký tự, không theo byte) |

- **Ghi chú:** dòng e không có trong SRS mà là error guessing; SRS nói "ký tự" nên kết quả mong đợi suy ra trực tiếp.
- **Tự động hoá:** (chưa viết) `payment` › `TransferRequestValidationTest`

### TC-P2P-006 — Chuẩn hoá số điện thoại người nhận
- **Yêu cầu:** F-PAY-03.FR-02 · **Mức:** UNIT · **Ưu tiên:** P2 · **Kỹ thuật:** EP · **AC:** AC-39

| # | Đầu vào | Mong đợi |
|---|---|---|
| a | `0900000002` | `+84900000002` |
| b | `+84900000002` | `+84900000002` |
| c | `0900 000 002` | **BLOCKED (TQ2)**: SRS không nói về khoảng trắng |
| d | `090000000` (9 số) | `VALIDATION_ERROR` |

- **Tự động hoá:** (chưa viết) `payment` › `PhoneNormalizerTest`

### TC-P2P-008 — State machine của lệnh P2P
- **Yêu cầu:** F-PAY-03.ST-01..04 · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** chuyển trạng thái · **AC:** AC-41

| # | Từ | Sự kiện | Mong đợi |
|---|---|---|---|
| a | `PROCESSING` | ghi sổ thành công | `SUCCEEDED` |
| b | `PROCESSING` | vượt hạn mức / thiếu số dư / ví bị khoá | `FAILED` + `failureCode` tương ứng |
| c | `SUCCEEDED` | bất kỳ | bị từ chối, giữ `SUCCEEDED` |
| d | `FAILED` | bất kỳ | bị từ chối, giữ `FAILED` |
| e | `PROCESSING` | → `UNKNOWN` | bị từ chối (P2P không có `UNKNOWN`) |

- **Tự động hoá:** (chưa viết) `payment` › `PaymentOrderStateTest`

*(TC-P2P-007 không dùng: ca tự chuyển được kiểm ở TC-P2P-044 (API) và TC-P2P-017 (DB).)*

### UNIT (frontend)

### TC-P2P-055 — Thử lại sau lệnh FAILED sinh `Idempotency-Key` mới; gửi lại vì mất mạng giữ khoá cũ
- **Yêu cầu:** F-PAY-03.FR-28; UI §4.5 · **Mức:** UNIT (Vitest + MSW) · **Ưu tiên:** P1
- **Bước:** render luồng S-02 → (1) MSW trả lỗi mạng → client tự gửi lại; (2) MSW trả `200 FAILED INSUFFICIENT_BALANCE`; (3) bấm "Thử lại" → xác nhận lại → MSW trả `200 SUCCEEDED`
- **Kết quả mong đợi:** khoá của lần gửi lại ở (1) = khoá ban đầu; khoá ở (3) khác khoá ở (1)–(2)
- **Tự động hoá:** (chưa viết) `apps/web` › `transfer/confirm.test.tsx`

### TC-P2P-056 — "Sửa thông tin" huỷ khoá; vào lại S-02 dùng khoá mới
- **Yêu cầu:** UI §4.5 (hệ quả của FR-09) · **Mức:** UNIT (frontend) · **Ưu tiên:** P1
- **Bước:** vào S-02 (khoá K1) → "Sửa thông tin" → đổi số tiền → vào lại S-02 → xác nhận
- **Kết quả mong đợi:** request mang khoá K2 ≠ K1; không bao giờ có `409 IDEMPOTENCY_KEY_REUSED`
- **Tự động hoá:** (chưa viết) `apps/web` › `transfer/flow.test.tsx`

### TC-P2P-057 — Bấm đúp nút chuyển chỉ gửi một request
- **Yêu cầu:** UI S-02 (`SubmitButton`); patterns an toàn giao dịch · **Mức:** UNIT (frontend) · **Ưu tiên:** P1
- **Bước:** `userEvent.dblClick` nút "Chuyển 101.000 ₫"
- **Kết quả mong đợi:** MSW ghi nhận đúng 1 request `POST /transfers`; nút ở trạng thái khoá "Đang xử lý…"
- **Tự động hoá:** (chưa viết) `apps/web` › `transfer/confirm.test.tsx`

### INTEGRATION

### TC-P2P-010 — Hai lệnh đồng thời không làm số dư âm
- **Yêu cầu:** F-PAY-03.FR-22, FR-21 · **Mức:** INTEGRATION (Testcontainers: payment + ledger + limit) · **Ưu tiên:** P1 · **AC:** AC-04
- **Tiền điều kiện:** phí 0; ví A số dư 100.000
- **Bước:** gửi đồng thời 2 lệnh chuyển 100.000 từ A (hai khoá khác nhau); lặp 50 vòng với ví mới
- **Kết quả mong đợi:** mỗi vòng: đúng 1 `SUCCEEDED`, 1 `FAILED INSUFFICIENT_BALANCE`; A = 0; B = +100.000; Σ số dư A + B không đổi; sổ cái cân
- **Tự động hoá:** (chưa viết) `payment` › `P2pConcurrencyIT`

### TC-P2P-011 — Hai request cùng khoá đồng thời tạo đúng một lệnh
- **Yêu cầu:** F-PAY-03.FR-11 · **Mức:** INTEGRATION · **Ưu tiên:** P1 · **AC:** AC-07
- **Bước:** 10 thread gửi cùng lúc request giống hệt nhau với khoá K2
- **Kết quả mong đợi:** đúng 1 dòng `payment_order` với K2; cả 10 response cùng `order.id`; A chỉ bị trừ một lần; ledger có đúng 1 journal tham chiếu lệnh
- **Tự động hoá:** (chưa viết) `payment` › `P2pIdempotencyIT`

### TC-P2P-012 — Ghi sổ không bao giờ dở dang
- **Yêu cầu:** F-PAY-03.FR-21 · **Mức:** INTEGRATION (fault injection trong `ledger`) · **Ưu tiên:** P1 · **AC:** AC-32
- **Bước:** ép lỗi lần lượt sau: trừ A / cộng B / ghi phí, trước khi commit
- **Kết quả mong đợi:** mỗi lần: hoặc cả ba thay đổi cùng có, hoặc không thay đổi nào; không có journal lệch (Σ D ≠ Σ C)
- **Tự động hoá:** (chưa viết) `ledger` › `JournalAtomicityIT`

### TC-P2P-013 — Khôi phục lệnh bị gián đoạn ở từng bước saga
- **Yêu cầu:** F-PAY-03.FR-27, FR-26 · **Mức:** INTEGRATION (chaos) · **Ưu tiên:** P1 · **AC:** AC-30

| # | Kill tiến trình `payment` sau khi | Mong đợi sau `p2p.processing.resume_after` |
|---|---|---|
| a | tạo lệnh (`STARTED`), chưa giữ hạn mức | Lệnh về trạng thái cuối; hạn mức đúng |
| b | giữ hạn mức (`LIMIT_RESERVED`), chưa ghi sổ | Lệnh `SUCCEEDED` (đủ tiền) hoặc `FAILED`; tiền chuyển đúng một lần |
| c | ledger đã commit, chưa cập nhật lệnh | Lệnh `SUCCEEDED`; **không** có journal thứ hai |

- **Trạng thái:** cần cơ chế kill/fault injection ở môi trường test (TQ3)
- **Tự động hoá:** (chưa viết) `payment` › `P2pRecoveryIT`

### TC-P2P-014 — Ví bị khoá giữa lúc kiểm tra và lúc ghi sổ
- **Yêu cầu:** F-PAY-03.FR-25 · **Mức:** INTEGRATION · **Ưu tiên:** P1 · **AC:** AC-21

| # | Ví bị khoá | Mong đợi |
|---|---|---|
| a | Ví người gửi (`debit_blocked`) | `FAILED ACCOUNT_BLOCKED`; A, B không đổi; hạn mức được hoàn |
| b | Ví người nhận đóng (`credit_blocked`) | `FAILED ACCOUNT_BLOCKED`; A, B không đổi |

- **Tự động hoá:** (chưa viết) `payment` › `P2pBlockedMidwayIT`

### TC-P2P-015 — Hạn mức: hoàn khi FAILED, ghi nhận khi SUCCEEDED, event trùng không hoàn hai lần
- **Yêu cầu:** F-PAY-03.FR-24 · **Mức:** INTEGRATION (payment + limit + Kafka) · **Ưu tiên:** P1 · **AC:** AC-14

| # | Tình huống | Hạn mức ngày đã dùng mong đợi |
|---|---|---|
| a | A còn hạn mức 10.000.000, số dư 100.000, chuyển 5.000.000 → `FAILED INSUFFICIENT_BALANCE` | 0 |
| b | Chuyển 100.000 → `SUCCEEDED` | 100.000 |
| c | Như (a), nhưng `PaymentFailed` được giao 2 lần | 0 (không âm) |

- **Tự động hoá:** (chưa viết) `limit` › `ReservationLifecycleIT`

### TC-P2P-016 — Kỳ ngày của hạn mức tính theo giờ Việt Nam
- **Yêu cầu:** F-PAY-03.BR-06 · **Mức:** INTEGRATION · **Ưu tiên:** P2 · **Kỹ thuật:** BVA (thời gian) · **AC:** AC-15
- **Bước:** đặt đồng hồ `2026-11-04T16:59:59Z` (23:59:59 VN), A chuyển 6.000.000; đặt `2026-11-04T17:00:01Z` (00:00:01 ngày 05/11 VN), A chuyển 6.000.000
- **Kết quả mong đợi:** cả hai `SUCCEEDED` (hạn mức ngày 10.000.000 được tính cho hai ngày khác nhau)
- **Ghi chú:** 6.000.000 vượt hạn mức mỗi giao dịch 5.000.000 trong cấu hình mặc định → TC này đặt `limit.p2p.per_txn = 10.000.000`
- **Tự động hoá:** (chưa viết) `limit` › `PeriodBoundaryIT`

### TC-P2P-017 — Ràng buộc DB chặn dữ liệu lệnh sai
- **Yêu cầu:** data model §payment; F-PAY-03.BR-03, §2.4, FR-31 · **Mức:** INTEGRATION (Flyway migration + PostgreSQL thật) · **Ưu tiên:** P1
- **Bước:** chạy migration của `payment`, rồi thử 14 thao tác vi phạm (danh sách ở data model, mục "Đã kiểm")
- **Kết quả mong đợi:** cả 14 bị từ chối với đúng tên ràng buộc; 4 thao tác hợp lệ (P2P, 2 dòng activity, TOPUP) thành công
- **Ghi chú:** bộ kiểm tra đã chạy tay ngày 2026-11-04 (14/14 bị chặn); TC này chuyển nó thành test tự động chạy trên migration thật.
- **Tự động hoá:** (chưa viết) `payment` › `PaymentSchemaConstraintsIT`

### TC-P2P-018 — Event `PaymentSucceeded`/`PaymentFailed` phát đúng một lần, có version
- **Yêu cầu:** F-PAY-03.EV-04; design §7 · **Mức:** INTEGRATION (Kafka) · **Ưu tiên:** P1 · **AC:** AC-42
- **Kết quả mong đợi:**
  - Lệnh `SUCCEEDED` → đúng 1 `PaymentSucceeded`, key = `orderId`, `aggregateversion` = `version` của lệnh.
  - Lệnh `FAILED` → đúng 1 `PaymentFailed` có `failureCode`.
  - Nghiệp vụ rollback → 0 event.
  - Payload không chứa OTP.
- **Tự động hoá:** (chưa viết) `payment` › `PaymentEventsIT`

### TC-P2P-019 — Bảo toàn tiền qua nhiều lệnh ngẫu nhiên
- **Yêu cầu:** F-PAY-03.NFR-02 · **Mức:** INTEGRATION (property-based) · **Ưu tiên:** P1 · **AC:** AC-38
- **Bước:** sinh ngẫu nhiên 1.000 lệnh (đủ tiền, thiếu tiền, vượt hạn mức, đồng thời) giữa 20 ví
- **Kết quả mong đợi:** Σ số dư 20 ví + doanh thu phí trước = sau; sổ cái cân; không ví nào âm
- **Tự động hoá:** (chưa viết) `payment` › `P2pConservationPropertyIT`

### API

### TC-P2P-020 — Chuyển tiền thành công có phí
- **Yêu cầu:** F-PAY-03.FR-19, FR-21, FR-24, FR-26, BR-01, BR-02 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-01
- **Tiền điều kiện:** A `ACTIVE` số dư 1.000.000; B `ACTIVE` số dư 0; phí 1.000
- **Bước:** `POST /transfers/preview` → `POST /transfers` (khoá K1, `expectedFee = 1000`)
- **Kết quả mong đợi:** `200`, `order.status = SUCCEEDED`, `fee = 1000`; A = 899.000; B = 100.000; doanh thu phí +1.000; hạn mức ngày đã dùng = 100.000
- **Tự động hoá:** (chưa viết) `e2e/api/transfers.spec.ts` › "TC-P2P-020"

### TC-P2P-021 — Biên số dư đủ/thiếu
- **Yêu cầu:** F-PAY-03.FR-23 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA · **AC:** AC-02, AC-03

| # | Số dư A | Chuyển (phí 1.000) | Mong đợi |
|---|---|---|---|
| a | 101.000 | 100.000 | `SUCCEEDED`; A = 0 |
| b | 100.999 | 100.000 | `FAILED INSUFFICIENT_BALANCE`; A = 100.999 |
| c | 100.000 | 100.000 | `FAILED INSUFFICIENT_BALANCE`; A = 100.000; hạn mức không đổi |

### TC-P2P-022 — Gửi lại cùng khoá trả lại lệnh cũ
- **Yêu cầu:** F-PAY-03.FR-08, FR-26 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-05
- **Bước:** sau TC-P2P-020, gửi lại đúng request với K1
- **Kết quả mong đợi:** `200`, cùng `order.id`, `SUCCEEDED`; số dư A, B không đổi thêm

### TC-P2P-023 — Gửi lại sau khi OTP đã dùng vẫn nhận lệnh cũ
- **Yêu cầu:** F-PAY-03.FR-08, FR-16 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-06
- **Tiền điều kiện:** chuyển 3.000.000 (cần OTP) thành công với K1, `challengeId` C1
- **Bước:** gửi lại đúng request với K1, C1 và OTP cũ
- **Kết quả mong đợi:** `200`, cùng `order.id`; **không** phải `422 CHALLENGE_INVALID`; không có lệnh thứ hai

### TC-P2P-024 — Cùng khoá, khác nội dung bị từ chối
- **Yêu cầu:** F-PAY-03.FR-09 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-08
- **Tiền điều kiện:** đã dùng K1 với (B, 100.000, note "x")

| # | Gửi lại K1 với | Mong đợi |
|---|---|---|
| a | amount 200.000 | `409 IDEMPOTENCY_KEY_REUSED` |
| b | người nhận C | `409 IDEMPOTENCY_KEY_REUSED` |
| c | note "y" | `409 IDEMPOTENCY_KEY_REUSED` |
| d | `expectedFee` khác, còn lại giống hệt | Trả lại lệnh cũ (`expectedFee` không thuộc nội dung lệnh, theo FR-09) |

- **Ghi chú:** dòng d dựa trên định nghĩa "nội dung" của FR-09 (ba trường), vốn là `[Giả định]` trong SRS.

### TC-P2P-025 — Thiếu `Idempotency-Key`
- **Yêu cầu:** F-PAY-03.FR-07 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-09
- **Kết quả mong đợi:** `400 IDEMPOTENCY_KEY_REQUIRED`; không có dòng `payment_order` mới

### TC-P2P-026 — Sau lệnh FAILED: khoá cũ trả FAILED, khoá mới thực hiện được
- **Yêu cầu:** F-PAY-03.FR-28 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-10
- **Bước:** K3 chuyển 100.000 khi số dư 100.000 → `FAILED`; nạp thêm 100.000; gửi lại K3; gửi K4 cùng nội dung
- **Kết quả mong đợi:** K3 → lệnh `FAILED` cũ (cùng `id`); K4 → `SUCCEEDED`

### TC-P2P-027 — Khoá còn hiệu lực trong 24 giờ
- **Yêu cầu:** F-PAY-03.FR-10 · **Mức:** API (đồng hồ điều khiển được) · **Ưu tiên:** P2 · **AC:** AC-11
- **Bước:** dùng K1; tua đồng hồ +23 giờ 59 phút; gửi lại K1
- **Kết quả mong đợi:** nhận lại lệnh cũ
- **Ghi chú:** SRS chỉ nói "ít nhất 24 giờ", nên không có ca > 24 giờ (data model giữ unique vĩnh viễn).

### TC-P2P-028 — Phí đổi giữa xem trước và xác nhận
- **Yêu cầu:** F-PAY-03.FR-18, BR-01 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-12
- **Bước:** xem trước (phí 1.000) → đổi rule phí thành 1.500 có hiệu lực → `POST /transfers` với `expectedFee = 1000`
- **Kết quả mong đợi:** `409 FEE_CHANGED`; không có lệnh; số dư không đổi. Gửi lại cùng khoá với `expectedFee = 1500` → `SUCCEEDED`, `fee = 1500`

### TC-P2P-029 — Biên hạn mức mỗi giao dịch
- **Yêu cầu:** F-PAY-03.FR-20, BR-06 · **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** BVA · **AC:** AC-13
- **Tiền điều kiện:** A số dư 20.000.000; dùng OTP khi được yêu cầu; `limit.p2p.per_day = 20.000.000` cho TC này

| # | amount | Mong đợi |
|---|---|---|
| a | 4.999.999 | `SUCCEEDED` |
| b | 5.000.000 | `SUCCEEDED` |
| c | 5.000.001 | `FAILED LIMIT_EXCEEDED` |

- **Trạng thái:** phần "kèm hạn mức còn lại" của dòng c **BLOCKED (UQ5)**: API chưa có trường `remaining` trong `order` (data model đã thêm `failure_detail`)

### TC-P2P-030 — Xem trước trả đúng `requiredFactor`
- **Yêu cầu:** F-PAY-03.FR-05, FR-16, BR-07 · **Mức:** API · **Ưu tiên:** P2 · **AC:** AC-16, AC-17
- **Bước:** xem trước với 2.000.000 / 2.000.001 / 20.000.001
- **Kết quả mong đợi:** `NONE` / `OTP` / `FACE`; response có `fee`, `total = amount + fee`, `recipientDisplay`
- **Ghi chú:** biên chi tiết ở TC-P2P-002; đây là ca đại diện cho hợp đồng API.

### TC-P2P-031 — Thiếu challenge khi cần xác thực
- **Yêu cầu:** F-PAY-03.FR-16 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-18
- **Kết quả mong đợi:** amount 3.000.000 không kèm `challengeId` → `422 CHALLENGE_REQUIRED`; không có lệnh

### TC-P2P-032 — Challenge cấp cho nội dung khác bị từ chối
- **Yêu cầu:** F-PAY-03.FR-17 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-19

| # | Challenge cấp cho | Dùng cho lệnh | Mong đợi |
|---|---|---|---|
| a | B, 3.000.000 | B, 4.000.000 | `422 CHALLENGE_INVALID` |
| b | B, 3.000.000 | C, 3.000.000 | `422 CHALLENGE_INVALID` |
| c | B, 3.000.000, đã dùng cho K1 | B, 3.000.000, khoá K5 | `422 CHALLENGE_INVALID` |

### TC-P2P-033 — Ví người gửi không ACTIVE
- **Yêu cầu:** F-PAY-03.FR-12 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-20
- **Kết quả mong đợi:** ví A ở `FROZEN` / `CLOSING` / `CLOSED` → `403 WALLET_FROZEN`; không có lệnh

### TC-P2P-034 — Cấp định danh chưa đủ
- **Yêu cầu:** F-PAY-03.FR-13 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-22
- **Kết quả mong đợi:** tier = `T_MIN − 1` → `403 KYC_REQUIRED`; tier = `T_MIN` → hợp lệ
- **Trạng thái:** **BLOCKED (Q2)** về giá trị `T_MIN`; chạy được với giá trị cấu hình test

### TC-P2P-035 — Người nhận không có ví hoặc không ACTIVE
- **Yêu cầu:** F-PAY-03.FR-03, BR-04 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-23

| # | Người nhận | Tra cứu | Tạo lệnh |
|---|---|---|---|
| a | SĐT không có ví | `404 RECIPIENT_NOT_FOUND` | — |
| b | Ví `FROZEN` | `422 RECIPIENT_UNAVAILABLE` | `422 RECIPIENT_UNAVAILABLE`, không có lệnh |
| c | Ví `CLOSING` | `422 RECIPIENT_UNAVAILABLE` | như trên |
| d | Ví `CLOSED` | `422 RECIPIENT_UNAVAILABLE` | như trên |

- **Ghi chú:** nếu SRS Q5 chọn gộp hai lỗi thì dòng a đổi kết quả.

### TC-P2P-036 — Tên người nhận luôn được che
- **Yêu cầu:** F-PAY-03.FR-01, FR-05 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-27
- **Kết quả mong đợi:** response tra cứu và xem trước của B ("NGUYEN VAN A") **không** chứa chuỗi "NGUYEN VAN A"; `displayName` khác tên đầy đủ
- **Trạng thái:** định dạng che chính xác **BLOCKED (Q4)**

### TC-P2P-037 — Giới hạn tần suất tra cứu người nhận
- **Yêu cầu:** F-PAY-03.FR-04 · **Mức:** API · **Ưu tiên:** P2 · **AC:** AC-28
- **Bước:** tra cứu `N` lần trong `W`, rồi lần `N+1`
- **Kết quả mong đợi:** `N` lần đầu thành công; lần `N+1` → `429 RATE_LIMITED` có header `Retry-After`; sau `Retry-After` giây tra cứu được lại

### TC-P2P-038 — Xem trước không có tác dụng phụ
- **Yêu cầu:** F-PAY-03.FR-06 · **Mức:** API · **Ưu tiên:** P2 · **AC:** AC-29
- **Kết quả mong đợi:** gọi xem trước 3 lần → không có dòng `payment_order` mới; số dư và hạn mức đã dùng không đổi

### TC-P2P-039 — Lệnh chưa xong trả 202, hỏi lại có kết quả cuối
- **Yêu cầu:** F-PAY-03.FR-26 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-31
- **Bước:** ép `ledger` chậm hơn thời gian chờ của request → `POST /transfers` → `GET /orders/{id}` định kỳ
- **Kết quả mong đợi:** lần đầu `202`, `status = PROCESSING`; sau đó `GET` trả `SUCCEEDED`; tiền chuyển đúng một lần
- **Trạng thái:** cần fault injection (TQ3)

### TC-P2P-040 — Người khác không xem được lệnh
- **Yêu cầu:** F-PAY-03.NFR-03 · **Mức:** API · **Ưu tiên:** P1 · **AC:** AC-37
- **Bước:** người dùng C gọi `GET /orders/{id}` của lệnh thuộc A
- **Kết quả mong đợi:** mã 4xx; body không chứa `amount`, tên, hay bất kỳ trường nào của lệnh
- **Trạng thái:** mã cụ thể (403 hay 404) **BLOCKED (TQ1)**

### TC-P2P-041 — Audit và traceId cho mỗi lệnh
- **Yêu cầu:** F-PAY-03.FR-32, EV-03, NFR-05 · **Mức:** API (đọc audit qua `/admin/v1/audit`) · **Ưu tiên:** P2 · **AC:** AC-36
- **Kết quả mong đợi:**
  - Mỗi lệnh `SUCCEEDED` và `FAILED` có bản ghi audit đủ trường: người gửi, người nhận, số tiền, phí, trạng thái cuối, `failureCode`, thời điểm.
  - `payment_order.trace_id` khớp `traceparent` của request.

### TC-P2P-042 — Thông báo cho hai bên
- **Yêu cầu:** F-PAY-03.FR-29, EV-01, EV-02 · **Mức:** API (`FakeNotificationSender`) · **Ưu tiên:** P2 · **AC:** AC-33
- **Kết quả mong đợi:** sau lệnh `SUCCEEDED` (100.000, phí 1.000): A có đúng 1 thông báo chứa 101.000 và mã lệnh; B có đúng 1 thông báo chứa 100.000 và mã lệnh. Lệnh `FAILED` → không thông báo biến động cho B
- **Trạng thái:** nội dung về đối phương **BLOCKED (Q8)**

### TC-P2P-043 — Lịch sử của hai bên
- **Yêu cầu:** F-PAY-03.FR-30 · **Mức:** API · **Ưu tiên:** P2 · **AC:** AC-34, AC-35

| # | Lệnh | Lịch sử A | Lịch sử B |
|---|---|---|---|
| a | `SUCCEEDED` 100.000, phí 1.000 | 1 dòng `O`, 101.000, `SUCCEEDED` | 1 dòng `I`, 100.000, `SUCCEEDED`, cùng mã lệnh |
| b | `FAILED INSUFFICIENT_BALANCE` | 1 dòng `O`, `FAILED` | không có dòng nào |

### TC-P2P-044 — Không chuyển cho chính mình
- **Yêu cầu:** F-PAY-03.FR-15, BR-03 · **Mức:** API · **Ưu tiên:** P2 · **AC:** AC-24
- **Kết quả mong đợi:** A chuyển cho SĐT của A (cả dạng `0…` và `+84…`) → `400 VALIDATION_ERROR`; không có lệnh

### TC-P2P-045 — Thứ tự ưu tiên lỗi ở mức API
- **Yêu cầu:** F-PAY-03.BR-08, FR-08, FR-12, FR-03 · **Mức:** API · **Ưu tiên:** P2 · **AC:** AC-40

| # | Tình huống | Mong đợi |
|---|---|---|
| a | A `FROZEN` + người nhận không có ví | `403 WALLET_FROZEN` |
| b | A tạo lệnh K6 thành công, sau đó A bị khoá, gửi lại K6 | Trả lại lệnh K6 cũ, không phải `WALLET_FROZEN` |

### E2E

### TC-P2P-050 — Chuyển tiền thành công qua giao diện, không cần xác thực tăng cường
- **Yêu cầu:** UC-01; FR-01, FR-05, FR-26 · **Mức:** E2E (Playwright, mobile viewport 360×740) · **Ưu tiên:** P1
- **Bước:** đăng nhập A → Chuyển tiền → nhập SĐT B → thấy tên đã che → nhập 100.000 → Tiếp tục → S-02 thấy phí 1.000, tổng 101.000, nút "Chuyển 101.000 ₫" → bấm
- **Kết quả mong đợi:** S-04 "Chuyển tiền thành công", −101.000 ₫, có mã giao dịch sao chép được; số dư trang chủ = 899.000 ₫
- **Trạng thái:** dòng "Người nhận" ở S-04 khi tải lại trang **BLOCKED (DQ1)**

### TC-P2P-051 — Luồng có OTP
- **Yêu cầu:** UC-01 A1; FR-16 · **Mức:** E2E · **Ưu tiên:** P1
- **Bước:** chuyển 3.000.000 → S-02 báo cần OTP → bấm chuyển → S-03 → nhập sai OTP → thấy lỗi, ô được xoá → nhập OTP đúng (lấy từ fake)
- **Kết quả mong đợi:** lỗi "Mã xác thực không đúng hoặc đã hết hạn." ở lần sai; lần đúng sang S-04 thành công; chỉ có 1 lệnh

### TC-P2P-052 — Mất mạng khi gửi lệnh không tạo lệnh trùng
- **Yêu cầu:** UI §4.5; FR-08 · **Mức:** E2E (Playwright `route` chặn response đầu tiên) · **Ưu tiên:** P1

| # | Tình huống | Mong đợi |
|---|---|---|
| a | Response đầu tiên bị huỷ, server đã tạo lệnh | UI tự gửi lại cùng khoá → S-04 thành công; DB có đúng 1 lệnh |
| b | Mọi lần gửi lại đều lỗi mạng | S-04 "Chưa xác định được kết quả"; **không** có chữ "Thất bại"; "Kiểm tra lại" sau khi mạng có lại → kết quả thật; vẫn 1 lệnh |
| c | Như (b), tải lại trang rồi "Kiểm tra lại" | Gửi lại cùng khoá từ `sessionStorage`; vẫn 1 lệnh |

### TC-P2P-053 — Phí đổi khi đang ở màn xác nhận
- **Yêu cầu:** FR-18; UI S-02 · **Mức:** E2E · **Ưu tiên:** P2
- **Kết quả mong đợi:** đổi phí trong lúc ở S-02 → bấm chuyển → dòng phí được làm nổi bật "Phí đã thay đổi từ 1.000 ₫ thành 1.500 ₫", nút đổi thành "Chuyển 101.500 ₫", không tự gửi; bấm lại → thành công với phí 1.500

### TC-P2P-054 — Lệnh đang xử lý không bao giờ hiển thị "Thất bại"
- **Yêu cầu:** FR-26, FR-27; AC-31; UI S-04 · **Mức:** E2E · **Ưu tiên:** P1
- **Bước:** ép `ledger` chậm 10 giây → chuyển tiền
- **Kết quả mong đợi:** S-04 "Giao dịch đang được xử lý" (icon đồng hồ), không có chữ "Thất bại"; tự chuyển sang "Chuyển tiền thành công" khi có kết quả, và được đọc bởi `aria-live`
- **Trạng thái:** cần fault injection (TQ3)

### TC-P2P-059 — Luồng xác thực khuôn mặt
- **Yêu cầu:** FR-16 (`FACE`) · **Mức:** E2E · **Ưu tiên:** P2
- **Trạng thái:** **BLOCKED (UQ1)**: luồng UI khuôn mặt thuộc F-ACC-02, chưa thiết kế

### NFR

### TC-P2P-058 — Accessibility các màn hình chuyển tiền
- **Yêu cầu:** F-PAY-03.NFR-07 · **Mức:** NFR (`@axe-core/playwright` trong E2E + kiểm tay bàn phím) · **Ưu tiên:** P2
- **Kết quả mong đợi:**
  - Không có vi phạm axe mức serious/critical ở S-01..S-04.
  - Hoàn tất luồng chỉ bằng bàn phím.
  - Focus vào `h1` mỗi lần chuyển bước.
  - Dùng được ở 320 px và khi phóng to 200%.

### TC-P2P-060 — Hiệu năng tạo lệnh
- **Yêu cầu:** F-PAY-03.NFR-01 · **Mức:** NFR (k6, staging) · **Ưu tiên:** P1
- **Bước:** 150 lệnh P2P/giây trong 15 phút, cộng 350 req/giây đọc (số dư, lịch sử), dữ liệu 500k ví
- **Kết quả mong đợi:** p99 `POST /transfers` < 1 giây; tỷ lệ lỗi 5xx < 0,1%; sau test sổ cái cân và không có lệnh `PROCESSING` tồn quá 2 phút

### TC-P2P-061 — OTP không xuất hiện trong log và response
- **Yêu cầu:** F-PAY-03.NFR-04 · **Mức:** NFR (security, chạy kèm TC-P2P-051) · **Ưu tiên:** P1
- **Kết quả mong đợi:**
  - Tìm mã OTP đã dùng trong log của `identity`, `payment`, `notification`, gateway và trong mọi response: 0 kết quả.
  - Event Kafka của luồng không chứa OTP.

## Câu hỏi mở

ID chung: TQ1 = OQ-023, TQ2 = OQ-016, TQ3 = OQ-024 ([open-questions.md](../open-questions.md)).

| # | Câu hỏi | Nguồn | TC bị ảnh hưởng |
|---|---|---|---|
| TQ1 | Người khác gọi `GET /orders/{id}`: trả 403 hay 404? SRS AC-37 ghi "bị từ chối"; UI S-04 hiển thị "Không tìm thấy giao dịch" | SRS NFR-03, UI S-04 | TC-P2P-040 |
| TQ2 | Server có chấp nhận SĐT có khoảng trắng hoặc dấu chấm không, hay client phải bỏ trước khi gửi? | SRS FR-02 | TC-P2P-006c |
| TQ3 | **Khả năng kiểm thử:** chưa có tài liệu nào mô tả cách ép lỗi giữa saga (kill tiến trình, làm chậm `ledger`) ở môi trường test. Cần một cơ chế fault injection chỉ có ở profile test (tương tự cách `FakeBankGateway` chọn kết quả theo số tiền) | design §12.3 | TC-P2P-013, 039, 054 |
| — | Kế thừa: SRS Q1 (ngưỡng ngày), Q2 (`T_MIN`), Q4 (quy tắc che), Q5 (gộp lỗi người nhận), Q8 (nội dung thông báo); UI UQ1 (FACE), UQ5 (`remaining`); data model DQ1 (`counterpartyDisplay`) | | TC-P2P-002, 029, 034, 035, 036, 042, 050, 059 |
