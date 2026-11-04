---
name: srs-test-cases
description: Sinh bộ test case từ tài liệu đặc tả yêu cầu (SRS, requirements, user story, PRD, design doc) để phục vụ unit test, API test và auto test E2E. Mỗi test case truy vết về mã yêu cầu, ghi rõ mức test và kịch bản tự động hoá. Dùng skill này khi người dùng muốn "viết test case", "tạo test case từ SRS/requirements", "lập test plan/test scenario", "ma trận truy vết yêu cầu (RTM)", "chuẩn bị test cho QA", "chuyển yêu cầu thành Gherkin/BDD", "xuất test case ra CSV/Excel/TestRail/Xray", hoặc hỏi "cần test những gì cho tính năng X", kể cả khi không nói chữ SRS mà chỉ đưa file yêu cầu hay mô tả tính năng. Không dùng cho việc viết code test cụ thể (khi đó dùng unit-test-java-nextjs), nhưng có thể nối tiếp sang skill đó.
---

# Sinh test case từ SRS

Mục tiêu: từ tài liệu yêu cầu, tạo ra bộ test case **đủ để chứng minh từng yêu cầu được đáp ứng**, có truy vết hai chiều (yêu cầu → test, test → yêu cầu), và đủ cụ thể để dev viết unit test hoặc QA viết auto test **mà không phải đoán**.

Viết bằng **tiếng Việt**; ID, tên field, mã lỗi và thuật ngữ kỹ thuật giữ nguyên như trong tài liệu.

## Bước 1 — Đọc nguồn và xác định phạm vi

1. Đọc SRS/requirements mà người dùng chỉ định (trong repo này mặc định là `docs/requirements.md`). Đọc thêm design doc và API spec nếu có (`docs/design/`): chúng cho biết mã lỗi, trạng thái và API cụ thể, nhờ đó test case có kết quả mong đợi chính xác.
   **Nếu có đặc tả từng tính năng** (`docs/srs/features/F-xxx-*.md`, tạo bởi `srs-feature-spec`): ưu tiên dùng nó làm nguồn chính. Mã truy vết của test case là ID `F-xxx.FR-nn` / `.BR-nn` / `.ST-nn` / `.EV-nn` / `.NFR-nn`, và mỗi tiêu chí chấp nhận (`.AC-nn`) là điểm xuất phát cho ít nhất một test case, vì dữ liệu và kết quả mong đợi đã cụ thể sẵn. Câu hỏi mở của spec trở thành test case `BLOCKED`.
2. Xác định phạm vi: toàn bộ SRS hay một nhóm yêu cầu (ví dụ "FR-P2P-*"). Tài liệu lớn thì làm **theo từng nhóm chức năng**, mỗi nhóm một file, để người đọc review được.
3. Lập danh sách yêu cầu kèm ID. Yêu cầu chưa có ID thì gán ID tạm (`REQ-<nhóm>-NN`) và ghi chú lại.

## Bước 2 — Phân tích từng yêu cầu

Với mỗi yêu cầu, trả lời:
- **Hành vi cần chứng minh** là gì? Một yêu cầu có thể chứa nhiều hành vi; tách chúng ra.
- **Đầu vào và các miền giá trị** → dùng kỹ thuật thiết kế test phù hợp (xem `references/techniques.md`): phân vùng tương đương, giá trị biên, bảng quyết định, chuyển trạng thái, ca lỗi.
- **Kết quả quan sát được**: response, trạng thái, dữ liệu lưu, thông báo, event, log/audit.
- **Điều chưa rõ**: con số chưa chốt, mâu thuẫn giữa các mục, hành vi lỗi không được mô tả. **Không tự bịa kết quả mong đợi.** Ghi vào danh sách "Câu hỏi mở", và đánh dấu test case liên quan là `BLOCKED (Q#)` hoặc tham số hoá theo cấu hình (ví dụ "hạn mức = giá trị cấu hình `LIMIT_DAY_TIER1`").

Yêu cầu phi chức năng (hiệu năng, bảo mật, sẵn sàng) cũng cần test case, nhưng mức test thường là load test, security test hay chaos test chứ không phải unit test. Ghi đúng mức test cho chúng.

## Bước 3 — Thiết kế test case

Áp dụng `references/techniques.md`. Với mỗi yêu cầu, tối thiểu có:
- 1 ca **positive** đại diện.
- Các ca **biên** cho mọi ngưỡng số.
- Các ca **negative** cho từng điều kiện bị vi phạm.
- Ca **chuyển trạng thái không hợp lệ** nếu yêu cầu có trạng thái.
- Ca **bảo mật/phân quyền** nếu yêu cầu liên quan dữ liệu của người dùng.

Với domain tài chính, rà thêm checklist `../unit-test-java-nextjs/references/fintech-test-cases.md` (idempotency, timeout ngân hàng, giữ tiền, sổ cái cân...) và gắn từng ca vào yêu cầu tương ứng.

**Gộp và lọc:** hai ca cùng phân vùng tương đương và cùng kết quả thì giữ một. Mục tiêu là **ít ca mà bắt được nhiều lỗi**, không phải nhiều ca.

## Bước 4 — Gán mức test & hướng tự động hoá

Mỗi test case ghi rõ **mức test** thấp nhất mà vẫn chứng minh được hành vi (xem `references/automation-mapping.md`):

| Mức | Khi nào |
|---|---|
| `UNIT` | Rule tính toán, validate, state machine: kiểm được không cần DB hay mạng |
| `INTEGRATION` | Cần DB thật hoặc broker: lock, unique, transaction, consumer |
| `API` | Hợp đồng HTTP: mã lỗi, phân quyền, idempotency qua header |
| `E2E` | Luồng người dùng xuyên UI: chỉ cho các luồng chính |
| `MANUAL` | Cần con người: UX, eKYC thật, pentest; ghi lý do không tự động hoá |
| `NFR` | Load test, chaos test, security scan |

Nguyên tắc kim tự tháp: phần lớn ở `UNIT`/`API`, ít ở `E2E`. Một rule đã được kiểm ở `UNIT` thì `E2E` chỉ cần một ca đại diện cho luồng, không lặp lại mọi biên.

## Bước 5 — Xuất kết quả

Theo format ở `references/output-format.md`:
1. **File test case** cho từng nhóm: `docs/test-cases/<nhóm>.md` (tạo thư mục nếu chưa có).
2. **Ma trận truy vết (RTM)**: `docs/test-cases/traceability.md`: mỗi yêu cầu → danh sách TC; đánh dấu yêu cầu **chưa có TC** và lý do.
3. **Câu hỏi mở**: cuối mỗi file, kèm TC bị ảnh hưởng. Câu hỏi mới thêm vào `docs/open-questions.md` (ID `OQ-` tiếp theo, nguồn "Test case <ID> TQ#"). Câu hỏi đã có thì chỉ ghi TC bị chặn vào cột "Chặn". Khi một `OQ` được trả lời, gỡ `BLOCKED` của các TC liên quan và điền kết quả mong đợi.
4. **Tuỳ chọn khi người dùng yêu cầu:**
   - CSV để import vào công cụ quản lý test (TestRail, Xray, Zephyr...).
   - Gherkin `.feature` cho BDD/auto test.
   - Code test: chuyển sang skill `unit-test-java-nextjs` cho `UNIT`/`INTEGRATION`; với `API`/`E2E` thì viết theo framework repo đang dùng (mặc định Playwright).

Nếu file đã tồn tại thì cập nhật, giữ nguyên ID test case cũ (ID không bao giờ đổi hay dùng lại), và đánh dấu ca bị bỏ là `DEPRECATED` thay vì xoá. Lý do: auto test và báo cáo test đang tham chiếu tới các ID này.

## Bước 6 — Tự kiểm trước khi giao

- Mọi yêu cầu trong phạm vi đều có ít nhất một TC, hoặc có lý do ghi trong RTM?
- Mọi TC có **kết quả mong đợi cụ thể, kiểm chứng được** (con số, mã lỗi, trạng thái), không phải "hệ thống hoạt động đúng"?
- Có TC nào tự bịa kết quả khi SRS không nói rõ? Nếu có, chuyển sang câu hỏi mở.
- Ngưỡng nào cũng có ca biên: đúng ngưỡng, ngưỡng ± 1?
- Mức test đã là mức thấp nhất có thể chưa?

Khi giao, tóm tắt: số yêu cầu đã phủ, số TC theo mức test, số câu hỏi mở và yêu cầu chưa phủ.
