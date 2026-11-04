---
name: ui-ux-spec
description: Thiết kế UI/UX cho một tính năng và viết thành tài liệu đặc tả giao diện, gồm user flow (Mermaid), danh sách màn hình, wireframe ASCII cho mobile/desktop, component theo Tailwind + shadcn/ui, ma trận trạng thái (đang tải, rỗng, lỗi, đang xử lý, không có quyền), nội dung chữ tiếng Việt, quy tắc tương tác và validation, accessibility WCAG 2.1 AA, truy vết về SRS. Áp dụng cho web khách hàng (mobile-first, giao dịch tiền) và CMS back-office (bảng dữ liệu, maker–checker, che dữ liệu nhạy cảm). Kết quả nằm trong docs/ui/. Dùng skill này khi người dùng muốn "thiết kế UI", "thiết kế màn hình", "UX cho tính năng X", "wireframe", "user flow", "luồng màn hình", "đặc tả giao diện", "màn hình CMS cho ...", "thiết kế form chuyển tiền", "review UX", hoặc trước khi code frontend cho một tính năng, kể cả khi không nói chữ "UI/UX". Nên có đặc tả SRS của tính năng (srs-feature-spec) trước.
---

# Thiết kế UI/UX → đặc tả giao diện

Đầu ra của skill là một tài liệu mà **frontend dev code được ngay, QA kiểm được, và người duyệt sản phẩm hình dung được**, không cần file Figma. Vì vậy mỗi màn hình phải trả lời đủ: hiển thị gì, lấy dữ liệu ở đâu, người dùng làm được gì, và **mọi trạng thái** của nó trông ra sao. Lỗi UX trong ví điện tử thường không nằm ở màn hình thành công mà ở các trạng thái còn lại: đang xử lý, timeout, bấm hai lần, phí đổi, phiên hết hạn.

Viết bằng **tiếng Việt**. Tên component, token, thuộc tính kỹ thuật giữ nguyên tiếng Anh. Nội dung chữ trên giao diện viết đúng như người dùng sẽ đọc.

## Bước 1 — Đọc nguồn

1. **Đặc tả SRS của tính năng** (`docs/srs/features/<ID>-*.md`): use case, FR, quy tắc nghiệp vụ, dữ liệu (mục 3.5), giao diện UI sơ bộ (mục 4.1), danh mục lỗi (mục 6), tiêu chí chấp nhận (mục 7), câu hỏi mở. Đây là nguồn chính: UI **không được tự thêm hay bớt hành vi nghiệp vụ**. Nếu thiết kế cần một hành vi mà SRS chưa có (ví dụ "lưu người nhận gần đây"), ghi vào câu hỏi mở, không lặng lẽ thêm vào.
2. Chưa có SRS → dùng requirements và design, nhưng nói rõ với người dùng rằng thiết kế dựa trên nguồn chưa chi tiết, và đề nghị chạy `srs-feature-spec` trước.
3. **API** (`docs/design/*-api.md`): endpoint, field, mã lỗi, quy ước response (200/202, `failureCode`).
4. **Design system dự án** `docs/ui/design-system.md`. Chưa có → tạo từ `references/design-system.md` (một lần cho cả dự án) và báo người dùng.
5. Các đặc tả UI đã có trong `docs/ui/features/` để dùng lại pattern và câu chữ cho nhất quán.

## Bước 2 — Xác định bề mặt và đọc pattern tương ứng

| Bề mặt | Người dùng | Đọc |
|---|---|---|
| **Web khách hàng** | Khách hàng, đa số dùng trình duyệt điện thoại | `references/patterns-web.md` |
| **CMS back-office** | Nhân viên CSKH, vận hành, rủi ro, tài chính; màn hình desktop | `references/patterns-cms.md` |

Luôn đọc thêm `references/a11y-content.md` (accessibility, nội dung chữ, định dạng). Tính năng có cả hai bề mặt (ví dụ tra soát: khách gửi, nhân viên xử lý) thì thiết kế cả hai, trong cùng một tài liệu, mỗi bề mặt một phần.

## Bước 3 — Thiết kế, theo thứ tự này

1. **User flow** trước màn hình: Mermaid flowchart gồm luồng chính, nhánh thay thế và **mọi nhánh lỗi** trong SRS. Làm flow trước để không sót trạng thái: mỗi nhánh của flow phải có màn hình hoặc trạng thái tương ứng.
2. **Danh sách màn hình** (ID `<FeatureID>.S-NN`), kể cả dialog và sheet quan trọng.
3. **Từng màn hình**, theo `references/template.md`:
   - Wireframe ASCII: mobile (khoảng 360 px) cho web khách hàng; desktop (khoảng 1280 px) cho CMS; thêm khung còn lại nếu bố cục thay đổi đáng kể.
   - Component shadcn/ui dùng cho từng vùng.
   - Dữ liệu: field nào, lấy từ API nào, định dạng ra sao.
   - Tương tác và validation: khi nào kiểm, lỗi hiện ở đâu, nút nào bị khoá khi nào.
   - **Ma trận trạng thái**: loading, rỗng, lỗi, đang xử lý, thành công, không có quyền, offline/timeout. Trạng thái nào không áp dụng thì ghi "—" kèm lý do.
   - Nội dung chữ đầy đủ: tiêu đề, nhãn, placeholder, nút, thông báo lỗi lấy từ mục 6 của SRS.
   - Ghi chú accessibility riêng của màn hình.
   - Truy vết: FR/BR/AC nào được thể hiện ở đây.
4. **Bảng ánh xạ lỗi**: mỗi mã lỗi và `failureCode` trong SRS → nơi hiển thị → câu chữ → hành động gợi ý cho người dùng.

Ưu tiên **dùng lại** component và pattern có sẵn trong design system. Thêm component mới chỉ khi không có cách nào khác, và ghi lý do.

## Bước 4 — Tự kiểm

Duyệt checklist cuối `references/template.md`. Tối thiểu:
- Mỗi nhánh trong user flow và mỗi mã lỗi trong SRS mục 6 đều có nơi hiển thị.
- Mỗi màn hình có đủ ma trận trạng thái.
- Các quy tắc an toàn giao dịch tiền (patterns-web §1) đã được áp dụng cho màn hình có thao tác tiền.
- Không có hành vi nghiệp vụ nào mới mà không có trong SRS (nếu có thì đã nằm trong câu hỏi mở).
- Không dùng màu làm tín hiệu duy nhất; nội dung chữ không lộ thuật ngữ kỹ thuật (`UNKNOWN`, `idempotency`...).

## Bước 5 — Ghi file và báo cáo

1. Ghi `docs/ui/features/<FeatureID>-<slug>.md` (cùng slug với SRS).
2. Thêm câu hỏi mở mới vào `docs/open-questions.md` (ID `OQ-` tiếp theo, nguồn "UI <ID> UQ#"). Chỗ API thiếu dữ liệu cho UI xếp vào nhóm "Thiết kế API". Câu hỏi đã có thì chỉ bổ sung nguồn.
2. Báo cáo: số màn hình, các quyết định UX không hiển nhiên (kèm lý do), câu hỏi mở (đặc biệt chỗ SRS thiếu cho UI), và gợi ý bước tiếp theo: duyệt thiết kế → code bằng Next.js theo `docs/ui/design-system.md` → viết test UI theo skill `unit-test-java-nextjs`.

## Chế độ cập nhật

Tài liệu UI đã có: giữ nguyên ID màn hình (`.S-NN`), không dùng lại ID đã bỏ. Chỉ sửa phần bị ảnh hưởng, tăng phiên bản và ghi lịch sử thay đổi. Nếu SRS thay đổi, đối chiếu lại bảng truy vết để tìm màn hình bị ảnh hưởng.
