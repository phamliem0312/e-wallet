---
name: deliver
description: Điều phối luồng phát triển tự động của EWallet cho MỘT hạng mục backlog - chọn việc tiếp theo theo mốc M1→M7, tạo nhánh, viết tài liệu còn thiếu, code, test, review, merge vào main và push - bằng các subagent spec-author, doc-reviewer, implementer, test-engineer, code-reviewer, git-manager. Dùng khi người dùng gõ /deliver, nói "chạy pipeline", "làm hạng mục tiếp theo", "tiếp tục mốc", "deliver F-XXX-NN", hoặc khi script scripts/run-pipeline.ps1 gọi ở chế độ headless.
---

# Deliver: một hạng mục, từ tài liệu tới main

Phiên chính là **người điều phối**. Subagent không gọi được subagent khác, nên mọi lần chuyển giữa các bước đều đi qua phiên chính. Người điều phối **không tự viết tài liệu hay code nghiệp vụ**; nó chọn việc, giao việc, đọc báo cáo, quyết định qua cổng, ghi backlog và worklog, commit checkpoint.

Mỗi lần gọi chỉ làm **một** hạng mục rồi dừng, để mỗi hạng mục có ngữ cảnh sạch. Script headless gọi lặp lại.

Tham số (`$ARGUMENTS`): rỗng thì chọn hạng mục tiếp theo; `<ID hạng mục>` thì làm đúng hạng mục đó.

## Bước 0: Kiểm tra trước khi chạy

1. `git status --porcelain` phải rỗng và nhánh hiện tại là `main`. Nếu không:
   - Có nhánh `feat|fix|docs|chore|test/*` đang dở mà backlog ghi `InProgress` → chuyển sang nhánh đó và **tiếp tục** từ bước còn dở (đọc mục "Nhật ký" của hạng mục trong backlog).
   - Có thay đổi chưa commit không rõ nguồn gốc → **dừng** với `PIPELINE_STATUS: BLOCKED` (có thể đang có phiên khác làm việc trên repo). Không stash, không xoá.
2. `git pull --ff-only origin main`. Lỗi → BLOCKED.
3. `docker info` chạy được (build và test cần Docker). Lỗi → BLOCKED.
4. Chưa có `docs/backlog.md` → tạo theo [Backlog](#backlog), commit trên nhánh `docs/backlog`, merge như một hạng mục docs, rồi mới sang bước 1.

## Bước 1: Chọn hạng mục

Hạng mục đầu tiên trong `docs/backlog.md` có trạng thái `Todo`, theo thứ tự mốc rồi thứ tự dòng, với **mọi phụ thuộc đã `Done`**. Hạng mục `Blocked` mà OQ chặn nó đã có câu trả lời trong `docs/open-questions.md` → đổi về `Todo` và được chọn lại.

Không còn hạng mục chạy được:
- Mọi hạng mục của mọi mốc đều `Done` → `PIPELINE_STATUS: COMPLETE`.
- Còn hạng mục nhưng đều `Blocked` → `PIPELINE_STATUS: BLOCKED`, liệt kê OQ và người cần quyết định.

## Bước 2: Mở nhánh

Gọi **git-manager**: `start <loại>/<ID>-<slug>` (ví dụ `feat/M2-ledger-core`, `docs/F-ACC-01-ui`). Ghi backlog: `InProgress`, tên nhánh.

## Bước 3: Tài liệu (cổng tài liệu)

Theo thứ tự trong CLAUDE.md: SRS → UI (nếu hạng mục có màn hình) → data model (nếu có bảng) → hợp đồng API/event → test case. Với từng tài liệu **còn thiếu hoặc ghi `Cần cập nhật`**, gọi **spec-author** một lần cho một tài liệu: đưa ID hạng mục, loại tài liệu, đường dẫn các tài liệu đầu vào.

Sau đó gọi **doc-reviewer** cho phạm vi của hạng mục.
- `PASS` → commit checkpoint `docs(<phạm vi>): ...`.
- `FAIL` → đưa danh sách lỗi cho spec-author sửa, rồi review lại. Tối đa **2 vòng**; vẫn FAIL → hạng mục `Blocked` (lý do: doc-review), sang [Kết thúc khi bị chặn](#kết-thúc-khi-bị-chặn).
- spec-author báo một câu hỏi mở **P1 chặn hành vi cốt lõi** (không thể làm thành tham số cấu hình) → `Blocked (OQ-NNN)`.

Hạng mục chỉ gồm tài liệu (loại `docs`) thì nhảy tới bước 7.

## Bước 4: Code

Gọi **implementer** với: ID hạng mục, danh sách tài liệu (SRS, data model, contracts, test case), phạm vi service. Implementer viết code kèm unit test và phải để `bash source-code/scripts/gradle.sh build` xanh.

Báo cáo không có dòng `BUILD: PASS` → gọi implementer thêm một lần với log lỗi. Vẫn đỏ → `Blocked (build)`.

Commit checkpoint `feat(<service>): ...`.

## Bước 5: Test (cổng test)

Gọi **test-engineer**: viết test API/integration từ `docs/test-cases/` của hạng mục, chạy toàn bộ build, trả về bảng truy vết TC → test.

- Test đỏ do code sai → implementer sửa (đưa nguyên tên test và assertion lỗi), test-engineer chạy lại. Tối đa **3 vòng**.
- test-engineer cho rằng test case hoặc spec sai → **không sửa test cho khớp code**. Chuyển cho spec-author xử lý như một mâu thuẫn tài liệu (sửa tài liệu và ghi lịch sử, hoặc mở OQ).
- TC bị `BLOCKED` vì OQ thì giữ nguyên `BLOCKED`, không tính là đỏ.

Commit checkpoint `test(<service>): ...`.

## Bước 6: Review (cổng review)

Gọi **code-reviewer** với `git diff main...HEAD` và danh sách tài liệu của hạng mục.
- `APPROVE` (không còn P0/P1) → sang bước 7.
- `CHANGES_REQUESTED` → implementer sửa theo từng finding → test-engineer chạy lại build → code-reviewer review **phần thay đổi mới và các finding cũ**. Tối đa **3 vòng**; còn P0/P1 → `Blocked (review)`.
- P2 chưa sửa → ghi vào worklog mục "Còn dở". P3 tuỳ chọn.

## Bước 7: Hoàn tất

1. Cập nhật trên nhánh: backlog (`Done`), `docs/worklog.md` (mục mới ở đầu: làm gì, kết quả kiểm chứng **thật** gồm số test và kết quả review, quyết định, còn dở), trạng thái spec trong `docs/srs/index.md`, bảng tiến độ trong `README.md`/`source-code/README.md` nếu đổi, dòng "Trạng thái" trong `CLAUDE.md` khi xong một mốc. Commit `chore: hoàn tất <ID>`.
2. Gọi **git-manager**: `finish <nhánh>` (rebase lên `origin/main`, build lại nếu rebase có thay đổi từ người khác, merge `--no-ff`, push, xoá nhánh).
3. Hạng mục cuối của một mốc → báo cáo mốc (mục riêng trong worklog: đã giao gì, test bắt buộc của §12.4 đã chạy chưa, OQ còn mở ảnh hưởng mốc sau), rồi gọi git-manager `tag m<N>`.
4. In kết quả và `PIPELINE_STATUS: CONTINUE`.

## Kết thúc khi bị chặn

1. Backlog: `Blocked (<lý do>)`, kèm một dòng giải thích và OQ liên quan. OQ mới thì thêm vào `docs/open-questions.md`.
2. Worklog: mục ngắn ghi hạng mục, bước bị chặn, bằng chứng (log, finding).
3. Commit trên nhánh, gọi git-manager `park <nhánh>` (push nhánh để người xem lại, quay về `main`). **Không merge** phần chưa qua cổng.
4. Ghi trạng thái Blocked vào `main` qua một nhánh `docs/backlog-<ID>` nhỏ (chỉ backlog và worklog), để lần chạy sau không chọn lại hạng mục này.
5. In `PIPELINE_STATUS: CONTINUE` nếu backlog còn hạng mục chạy được, ngược lại `PIPELINE_STATUS: BLOCKED`.

Lỗi môi trường (Docker chết, push bị từ chối hai lần, mạng chặn) **không** đánh dấu hạng mục Blocked: dừng ngay với `PIPELINE_STATUS: BLOCKED` và mô tả lỗi.

## Commit checkpoint

Người điều phối tự commit checkpoint trên nhánh tính năng (không cần git-manager):
`git add <các đường dẫn của bước vừa xong>` rồi `git commit -m "<loại>(<phạm vi>): <tóm tắt tiếng Việt>"`. Không `git add -A` khi có file lạ ngoài phạm vi hạng mục. Không bao giờ commit trên `main` (hook chặn).

## Backlog

`docs/backlog.md` là nguồn sự thật về **việc gì làm tiếp**. Tạo một lần từ design §12.4, `docs/srs/index.md` và bảng tiến độ trong `README.md`. Mỗi mốc là một bảng:

```
## M2 — ledger, wallet, fee, limit

| ID | Hạng mục | Loại | Tính năng / service | Phụ thuộc | Trạng thái | Nhánh | Ghi chú |
|---|---|---|---|---|---|---|---|
| M2-01 | ledger: sổ cái kép, posting, đảo bút toán | feat | ledger; F-... | M1-03 | Todo | | |
```

- Một hạng mục vừa một lần review: một service hoặc một lát dọc của một tính năng. Lớn hơn khoảng 1500 dòng thay đổi thì chia nhỏ.
- `Loại`: `feat` (tài liệu thiếu + code + test), `docs` (chỉ tài liệu), `chore` (hạ tầng, CI, scaffold).
- Trạng thái: `Todo`, `InProgress`, `Done`, `Blocked (<lý do>)`. Không xoá dòng; bỏ hạng mục thì ghi `REMOVED`.
- Phần việc đã xong trước khi có backlog (theo worklog) ghi `Done` ngay.
- Hạng mục M7 (load test, chaos, pentest) cần môi trường thật: ghi rõ phần nào làm được ở local, phần nào `Blocked (môi trường)`.

## Giao việc cho subagent

Mỗi lời gọi gồm: ID hạng mục, mục tiêu một câu, **đường dẫn** tài liệu đầu vào (không dán nội dung), tiêu chí xong, và phản hồi của vòng trước nếu có. Subagent trả báo cáo theo khuôn của nó; người điều phối chỉ đọc báo cáo, không đọc lại toàn bộ diff.

Không tin báo cáo một cách mù quáng: dòng `BUILD: PASS` phải đi kèm số test chạy được. Thiếu thì chạy lại `bash source-code/scripts/gradle.sh build` một lần để tự xác nhận.
