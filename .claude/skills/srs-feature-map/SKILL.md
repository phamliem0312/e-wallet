---
name: srs-feature-map
description: Phân rã tài liệu requirements thành danh mục tính năng (feature map) để làm đầu vào cho việc viết đặc tả SRS từng tính năng. Gom các yêu cầu có đánh mã (FR/NFR/IR...) thành tính năng, gán ID tính năng ổn định, xác định phụ thuộc, thứ tự viết, và kiểm tra mọi yêu cầu đều được phủ (không sót, không trùng). Ghi kết quả vào docs/srs/index.md. Dùng skill này khi người dùng muốn "lập danh sách tính năng từ requirements", "phân rã yêu cầu", "feature breakdown", "mục lục SRS", "index SRS", "cần đặc tả những tính năng nào", "chia SRS thành các phần", hoặc trước khi viết SRS cho nhiều tính năng, kể cả khi không nói chữ "feature map". Sau skill này, dùng srs-feature-spec để viết đặc tả từng tính năng.
---

# Feature map: từ requirements đến danh mục tính năng

Requirements thường được viết theo **nhóm yêu cầu** (FR-ID, FR-LK, NFR-C...), còn SRS cần được viết theo **tính năng** mà một người có thể gọi tên, giao cho một người viết, review và kiểm thử độc lập. Skill này làm cầu nối: nó quyết định "SRS gồm những tính năng nào, mỗi tính năng sở hữu yêu cầu nào", rồi chứng minh không yêu cầu nào bị bỏ sót.

Khâu này quan trọng vì mọi lỗi ở đây lan xuống: yêu cầu bị sót thì không bao giờ được đặc tả và không bao giờ được test; yêu cầu bị hai tính năng cùng sở hữu thì hai bản đặc tả sẽ mâu thuẫn nhau.

Viết bằng **tiếng Việt**; ID, tên field và thuật ngữ kỹ thuật giữ nguyên.

## Bước 1 — Đọc nguồn

1. Đọc tài liệu requirements người dùng chỉ định (trong repo này: `docs/requirements.md`). Đọc **toàn bộ**, kể cả các phần "đề xuất chi tiết", "câu hỏi mở", phụ lục: yêu cầu bổ sung thường nằm ở đó.
2. Đọc design doc (`docs/design/`) để biết ranh giới service/module và API: chúng giúp xác định phụ thuộc giữa các tính năng và cột "Dịch vụ" trong index. Design **không** thay đổi danh sách yêu cầu.
3. Nếu `docs/srs/index.md` đã tồn tại → chế độ **cập nhật** (xem cuối file). Không tạo lại từ đầu.

## Bước 2 — Lập kho yêu cầu (inventory)

Trích **mọi** ID yêu cầu. ID có thể xuất hiện ở nhiều dạng trong cùng một file, nên tìm cả hai:
- Dòng bảng: `| FR-P2P-01 | ... |`
- Gạch đầu dòng: `- FR-LIM-01 (M): ...`

Tìm bằng công cụ (grep), không dựa vào trí nhớ. Với mỗi ID ghi: nhóm (tiền tố), ưu tiên (M/S/C hoặc tương đương), cờ pháp lý (⚖️), cờ ngoài phạm vi giai đoạn hiện tại, nội dung tóm tắt. **Đếm theo tiền tố** và giữ con số này: nó là cơ sở để kiểm tra phủ ở Bước 5.

Yêu cầu chưa có ID: gán ID tạm `REQ-<nhóm>-NN`, ghi vào mục câu hỏi mở để chủ tài liệu bổ sung ID thật.

## Bước 3 — Gom yêu cầu thành tính năng

Một **tính năng** là một năng lực mà người dùng hoặc người vận hành có thể gọi tên ("Chuyển tiền ví–ví", "Liên kết ngân hàng"), có thể giao hàng và kiểm thử độc lập. Gom theo các nguyên tắc sau:

1. **Theo mục tiêu của actor, không theo lớp kỹ thuật.** "Nạp tiền" gồm cả luồng chính, hạn mức, phí, xử lý timeout: tất cả cùng một tính năng. Không tạo tính năng "Hạn mức" chỉ vì có module hạn mức, trừ khi hạn mức có actor và vòng đời riêng (cấu hình bởi back-office).
2. **Cỡ vừa đủ viết một bản đặc tả.** Khoảng 3–15 yêu cầu sở hữu. Nhiều hơn 15 → xem có tách được theo actor hoặc vòng đời không. Chỉ 1–2 yêu cầu và gắn chặt với tính năng khác → gộp, trừ khi actor khác nhau.
3. **Khác actor *và* khác vòng đời thì tách; cùng một quy trình thì giữ chung.** Nhân viên cấu hình hạn mức tách khỏi khách xem hạn mức còn lại, vì là hai việc độc lập. Nhưng khách gửi khiếu nại và nhân viên xử lý khiếu nại là hai đầu của *cùng một quy trình*, tách ra thì đặc tả mỗi nửa đều thiếu: giữ trong một tính năng (ghi cả hai actor).
4. **Đừng tin tiền tố.** Yêu cầu cùng tiền tố không nhất thiết cùng tính năng (FR-ID chứa đăng nhập, eKYC và đóng ví: ba tính năng). Đọc nội dung từng yêu cầu rồi mới gom.
5. **Yêu cầu phi chức năng (NFR):**
   - NFR gắn với đúng một tính năng → thuộc tính năng đó.
   - NFR áp dụng cho nhiều tính năng (bảo mật, tính đúng của tiền, quan sát...) → **cross-cutting**: gom thành một vài chủ đề (`CC-xx`), mỗi tính năng ghi "áp dụng CC-xx" thay vì sao chép lại.
6. **Yêu cầu tích hợp (IR) và interface (PR)** thuộc tính năng sử dụng chúng; nếu tích hợp là một năng lực riêng (ví dụ "Tích hợp ngân hàng") thì lập tính năng riêng.
7. **Yêu cầu ngoài phạm vi giai đoạn hiện tại** (mức Could/để sau): vẫn gán cho tính năng nhưng đánh dấu `ngoài GĐ`, hoặc đưa vào mục "Để sau" nếu cả tính năng nằm ngoài phạm vi.

Mỗi yêu cầu có **đúng một tính năng sở hữu** (owner). Tính năng khác chỉ *tham chiếu* ("liên quan") khi cần.

Ví dụ minh hoạ cách gom cho dự án ví điện tử ở `references/index-format.md`.

## Bước 4 — Đặt ID, phụ thuộc, thứ tự

- **ID tính năng:** `F-<NHÓM>-<NN>` (ví dụ `F-PAY-02`). `NHÓM` 2–4 chữ in hoa theo miền (ACC, KYC, BNK, PAY, LMT, BO...). ID **không bao giờ đổi hay dùng lại**, kể cả khi tính năng bị bỏ (đánh dấu `DEPRECATED`). Lý do: spec, test case và code sẽ trích ID này.
- **Slug file:** `docs/srs/features/<ID>-<slug-tiếng-việt-không-dấu>.md`.
- **Phụ thuộc:** tính năng A phụ thuộc B nếu A không thể hoạt động hoặc kiểm thử nếu thiếu B (ví dụ chuyển tiền cần ví đã mở và đã KYC). Không có vòng phụ thuộc; nếu có thì gộp hoặc tách lại.
- **Thứ tự viết đề xuất:** theo "làn sóng" (wave): tính năng nền (danh tính, ví, sổ cái) → luồng tiền → vận hành/back-office. Trong mỗi làn ưu tiên Must. Lý do: spec của tính năng sau cần dùng thuật ngữ và quy tắc đã chốt ở tính năng trước.
- **Ưu tiên tính năng** = ưu tiên cao nhất trong các yêu cầu Must nó sở hữu. Yêu cầu phi chức năng thường không có cột ưu tiên: coi là `M` theo mặc định và ghi câu hỏi mở để chủ tài liệu xác nhận.

## Bước 5 — Kiểm tra phủ (không bỏ qua)

Chứng minh bằng số liệu, không bằng cảm giác:
1. Tổng số yêu cầu trong inventory = (đã gán tính năng) + (cross-cutting) + (ngoài phạm vi, có lý do) + (chưa gán). Mục tiêu: **chưa gán = 0**. Đối chiếu theo từng tiền tố.
2. Mỗi ID xuất hiện đúng một lần ở cột "sở hữu".
3. Không có phụ thuộc vòng.
4. Mỗi tính năng có ít nhất một actor và ít nhất một yêu cầu Must hoặc Should.

Chỗ nào không đạt thì sửa rồi kiểm lại; không để "tạm" chưa gán mà không ghi lý do.

## Bước 6 — Ghi kết quả và báo cáo

Ghi `docs/srs/index.md` theo `references/index-format.md` (tạo thư mục nếu chưa có). Không viết đặc tả chi tiết ở bước này; đó là việc của `srs-feature-spec`.

Câu hỏi mở và quyết định gom nhóm cần xác nhận: thêm vào `docs/open-questions.md` (ID `OQ-` tiếp theo, nguồn "SRS index Q#/G#"). Câu hỏi đã có thì chỉ bổ sung nguồn.

Khi báo cáo, nêu: số yêu cầu và số tính năng, số cross-cutting, số yêu cầu ngoài phạm vi, kết quả kiểm tra phủ, các quyết định gom nhóm không hiển nhiên (kèm lý do) để người dùng điều chỉnh, và thứ tự viết đề xuất.

## Chế độ cập nhật

Khi requirements đổi và `docs/srs/index.md` đã có:
- **Giữ nguyên ID tính năng.** Yêu cầu mới → thêm vào tính năng phù hợp, hoặc lập tính năng mới với ID kế tiếp.
- Yêu cầu bị xoá → ghi vào mục lịch sử, đánh dấu `REMOVED` ở tính năng sở hữu.
- Tính năng nào có yêu cầu đổi nội dung → đặt trạng thái spec `Cần cập nhật` và liệt kê ID yêu cầu đã đổi, để `srs-feature-spec` biết sửa chỗ nào.
- Ghi dòng mới vào "Lịch sử thay đổi" của index.
