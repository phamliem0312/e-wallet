# ADR-0002: Luồng phát triển tự động bằng agent, git trunk-based với nhánh tính năng

- Trạng thái: Accepted
- Ngày: 2026-11-10
- Thay thế một phần: quy tắc "tự commit và push lên `origin/main`" trong CLAUDE.md (2026-11-10)

## Bối cảnh
Chủ dự án muốn bộ agent tự làm toàn bộ dự án theo mốc M1 → M7 (tài liệu, code, test, review, quản lý git), chỉ dừng khi bị chặn. Trước đó mọi việc chạy trong một phiên và push thẳng `main`, nên:
- Không có bước review độc lập: người viết code cũng là người tự kiểm.
- Phần chưa qua review vẫn lên thẳng `main`.
- Nhiều phiên Claude cùng làm trên một repo đã commit chen nhau trên `main`.

Ràng buộc: máy chưa có `gh` CLI và GitHub MCP không kết nối được, nên chưa tự mở Pull Request được. Đội thực tế là một người và các agent.

## Các phương án
| Tiêu chí | Push thẳng `main` | Trunk + nhánh tính năng, merge local | Git Flow (develop/release) | Nhánh + Pull Request |
|---|---|---|---|---|
| Phần chưa review lên `main` | Có | Không | Không | Không |
| Nghi thức | Ít nhất | Ít | Nhiều (2 nhánh dài, release) | Trung bình |
| Cần công cụ ngoài | Không | Không | Không | `gh` + đăng nhập |
| Hợp với 1 người + agent, chưa có môi trường release | Có | Có | Thừa | Có, khi có `gh` |

## Quyết định
**Trunk-based với nhánh tính năng ngắn**, merge `--no-ff` vào `main` sau khi qua ba cổng: tài liệu (doc-reviewer), test (test-engineer), review (code-reviewer). Tag `m<N>` ở cuối mỗi mốc.

Điều phối bằng skill `/deliver` trong phiên chính (subagent không gọi được subagent). Mỗi lần gọi làm một hạng mục trong `docs/backlog.md`; `scripts/run-pipeline.ps1` gọi lặp lại ở chế độ headless. Sáu subagent trong `.claude/agents/` tách vai trò: người viết (spec-author, implementer, test-engineer) khác người soát (doc-reviewer, code-reviewer); git-manager giữ quy ước nhánh.

Hàng rào: allowlist/deny trong `.claude/settings.json` và hook `.claude/hooks/guard.mjs` chặn force push, ghi đè/xoá `main`, `reset --hard`, `clean -f`, `--no-verify`, đổi remote, commit trực tiếp trên `main`.

## Hệ quả
- **Tích cực:** `main` chỉ nhận phần đã qua cổng; review độc lập; lịch sử có merge commit theo từng hạng mục; chạy được không người trông.
- **Tiêu cực / chấp nhận:**
  - Tốn token hơn (mỗi hạng mục gọi 4–8 subagent).
  - Reviewer cũng là mô hình: không thay được review của người cho phần tiền. Chủ dự án nên đọc lại diff của `ledger`, `payment`, `bank-gateway`.
  - Luồng headless chạy trên thư mục gốc của repo: không được cho phiên khác ghi vào repo cùng lúc (bước 0 của `/deliver` dừng khi cây làm việc bẩn).
  - Hook soát nhánh theo thư mục làm việc của tool; `git -C <khác> commit` không bị soát.
- **Khi nào xem lại:**
  - Có `gh` hoặc GitHub MCP → chuyển sang Pull Request (git-manager mở PR, code-reviewer comment lên PR), có CI chạy trên PR.
  - Có môi trường staging/release thật → xem lại nhu cầu nhánh release.
  - Đội có thêm người → bắt buộc người duyệt cho thay đổi tiền.
