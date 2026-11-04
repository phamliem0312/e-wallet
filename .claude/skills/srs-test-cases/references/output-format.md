# Format output

## 1. File test case (`docs/test-cases/<nhóm>.md`)

```markdown
# Test case — <Nhóm chức năng>

- Nguồn: docs/requirements.md v<x> (mục ...), docs/design/... (mục ...)
- Ngày: <yyyy-mm-dd>
- Phạm vi: FR-P2P-01..04, NFR-C-02, ...

## Tóm tắt
| Mức | Số TC |
|---|---|
| UNIT | 12 |
| API | 8 |
| E2E | 2 |
| MANUAL | 1 |

## Test case

### TC-P2P-001 — Chuyển tiền thành công trong hạn mức
- **Yêu cầu:** FR-P2P-01, FR-P2P-02
- **Mức:** API · **Ưu tiên:** P1 · **Kỹ thuật:** EP
- **Tiền điều kiện:** Ví A ACTIVE, tier 1, số dư 1.000.000; ví B ACTIVE; phí P2P = 0
- **Dữ liệu:** amount = 100.000; recipientPhone = SĐT của B; Idempotency-Key = K1
- **Bước:**
  1. POST /api/v1/transfers/preview
  2. POST /api/v1/transfers với dữ liệu trên
- **Kết quả mong đợi:**
  1. Preview trả fee = 0, requiredFactor = NONE, recipientDisplay đã che
  2. 200, order.status = SUCCEEDED; số dư A = 900.000; số dư B tăng 100.000; có event PaymentSucceeded
- **Tự động hoá:** `api/transfers.spec.ts` › "p2p success"
- **Ghi chú:** —
```

**Quy tắc:**
- **ID:** `TC-<NHÓM>-<NNN>`, không bao giờ đổi hay dùng lại. Ca bị bỏ → thêm `[DEPRECATED]` vào tiêu đề và ghi lý do.
- **Tiêu đề:** điều kiện + kết quả, đọc lên là biết test gì.
- **Ưu tiên:**
  - P1: tiền, bảo mật, luồng chính.
  - P2: lỗi nghiệp vụ thường gặp.
  - P3: biên hiếm gặp, UX.
- **Kết quả mong đợi:** con số, mã lỗi, trạng thái cụ thể. Với tiền, ghi số dư trước và sau của mọi tài khoản liên quan, kể cả phí.
- Ca bị chặn vì câu hỏi mở: thêm `**Trạng thái:** BLOCKED (Q3)`.
- Nhiều ca chỉ khác dữ liệu → gộp thành một TC dạng bảng dữ liệu:

```markdown
### TC-FEE-010 — Phí được kẹp trong [min, max] và làm tròn lên
- **Yêu cầu:** FR-FEE-01, FR-FEE-03 · **Mức:** UNIT · **Ưu tiên:** P1 · **Kỹ thuật:** BVA
- **Tiền điều kiện:** rule P2P: percent 0,1%, min 1.000, max 20.000

| # | amount | Phí mong đợi | Lý do |
|---|---|---|---|
| a | 1 | 1.000 | dưới min |
| b | 1.000.000 | 1.000 | đúng min |
| c | 1.000.001 | 1.001 | làm tròn lên |
| d | 20.000.000 | 20.000 | đúng max |
| e | 20.000.001 | 20.000 | trên max |
```

**Cuối file:**

```markdown
## Câu hỏi mở
| # | Câu hỏi | Nguồn | TC bị ảnh hưởng |
|---|---|---|---|
| Q1 | Ngưỡng sinh trắc học cụ thể? | FR-P2P-04 ⚖️ | TC-P2P-020..022 |
```

## 2. Ma trận truy vết (`docs/test-cases/traceability.md`)

```markdown
| Yêu cầu | Mô tả ngắn | Ưu tiên YC | Test case | Mức | Trạng thái phủ |
|---|---|---|---|---|---|
| FR-P2P-01 | Chuyển theo SĐT, hiện tên đã che | M | TC-P2P-001, 002, 003 | API, E2E | ✅ |
| FR-P2P-04 | Xác thực tăng cường theo ngưỡng | M | TC-P2P-020..022 | UNIT, API | ⏸️ BLOCKED Q1 |
| FR-HS-06 | Sao kê PDF | S | — | — | ❌ Ngoài phạm vi GĐ1 |
```
Trạng thái phủ: ✅ đã phủ · ⚠️ phủ một phần (ghi rõ thiếu gì) · ⏸️ bị chặn · ❌ chưa phủ (ghi lý do).

## 3. CSV (khi người dùng yêu cầu)

Một dòng cho mỗi TC; TC dạng bảng dữ liệu thì mỗi dòng dữ liệu một dòng CSV (ID gắn hậu tố `-a`, `-b`...). Encoding UTF-8 có BOM để Excel đọc đúng tiếng Việt.

```
ID,Title,Requirement,Level,Priority,Technique,Preconditions,Test Data,Steps,Expected Result,Automation,Status
```
- Nhiều bước hoặc nhiều kết quả: đánh số trong cùng một ô, xuống dòng bằng `\n` trong ô có dấu ngoặc kép.
- Tên cột trùng với field của công cụ đích nếu người dùng nói rõ công cụ. Ví dụ TestRail dùng `Section`, `Title`, `Preconditions`, `Steps`, `Expected Result`, `Priority`, `References`.

## 4. Gherkin (khi người dùng yêu cầu BDD)

```gherkin
# language: vi  (hoặc giữ tiếng Anh theo quy ước repo)
@FR-P2P-01 @TC-P2P-001 @api @P1
Feature: Chuyển tiền ví – ví

  Background:
    Given ví "A" ACTIVE, tier 1, số dư 1.000.000
    And ví "B" ACTIVE

  Scenario: Chuyển tiền thành công trong hạn mức
    When "A" chuyển 100.000 cho SĐT của "B" với Idempotency-Key "K1"
    Then lệnh có trạng thái "SUCCEEDED"
    And số dư "A" là 900.000
    And số dư "B" tăng 100.000

  @TC-FEE-010
  Scenario Outline: Phí được kẹp trong [min, max]
    When tính phí P2P cho <amount>
    Then phí là <fee>
    Examples:
      | amount     | fee    |
      | 1          | 1000   |
      | 1000001    | 1001   |
      | 20000001   | 20000  |
```
- Tag bằng ID yêu cầu và ID TC để báo cáo truy vết được.
- Step viết theo ngôn ngữ nghiệp vụ, không chứa selector UI hay URL.
