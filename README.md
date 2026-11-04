# EWallet

Ví điện tử của một tổ chức trung gian thanh toán, liên kết với tài khoản ngân hàng của khách.

**Giai đoạn 1:** khách hàng cá nhân (B2C) trên web và back-office (CMS). Tích hợp bên thứ 3 (ngân hàng, eKYC, thông báo, AML) chỉ có interface và bản fake.

> **Trạng thái: tài liệu đang đi trước, code mới ở mốc M1 (nền tảng).** `source-code/` có starter `platform`, hạ tầng chạy local và hợp đồng API/event; chưa có service nghiệp vụ nào. Tiến độ chi tiết ở [docs/worklog.md](docs/worklog.md); mốc theo [design §12.4](docs/design/2026-11-04-ewallet-design.md).

### Tiến độ (cập nhật 2026-11-04)
| Hạng mục | Tình trạng |
|---|---|
| Yêu cầu, kiến trúc (ADR-0001, 14 service), data model, API doc | Xong bản đầu (requirements v0.2, design v0.3) |
| M1 nền tảng: `platform` core/web/messaging, docker compose | Xong, 20/20 test pass. Còn: security, observability, `api-gateway`, `identity`, frontend, Helm, CI |
| F-PAY-03 Chuyển tiền ví–ví | SRS Draft v0.2, UI, 55 test case, hợp đồng API. Chưa duyệt |
| F-ACC-01 Đăng ký & đăng nhập | SRS Draft v0.3, data model `identity`, hợp đồng API/event. Chưa có UI, test case. Chưa duyệt |
| 22 tính năng còn lại | Chưa có SRS |
| Câu hỏi mở | 50, trong đó 6 câu P1 chặn luồng tiền |

## Kiến trúc tóm tắt

- 14 microservice (identity, kyc, aml, wallet, ledger, limit, fee, payment, bank-gateway, recon, dispute, approval, audit, notification); chi tiết ở [ADR-0001](docs/adr/0001-microservices.md).
- Mỗi service một PostgreSQL; giao tiếp qua REST nội bộ (`/internal/**`) và Kafka (outbox/inbox); luồng thanh toán là saga do `payment` điều phối.
- Chỉ `ledger` thay đổi số dư, theo sổ cái kép. Tiền là số nguyên đơn vị đồng.
- Backend: Java 21, Spring Boot, Gradle (Kotlin DSL). Frontend: Next.js (App Router, TypeScript), Tailwind + shadcn/ui, gồm `web` (khách) và `cms` (back-office). Hạ tầng: Kubernetes + service mesh.

## Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [docs/requirements.md](docs/requirements.md) | Yêu cầu (FR/NFR/IR/PR) và quyết định đã chốt |
| [docs/adr/](docs/adr/) | Quyết định kiến trúc |
| [docs/design/](docs/design/) | Thiết kế ([kiến trúc](docs/design/2026-11-04-ewallet-design.md), [data model](docs/design/2026-11-04-ewallet-data-model.md), [API](docs/design/2026-11-04-ewallet-api.md)) |
| [docs/srs/index.md](docs/srs/index.md) | Danh mục 24 tính năng; đặc tả từng tính năng ở `docs/srs/features/` |
| [docs/ui/](docs/ui/) | Design system và đặc tả UI từng tính năng |
| [docs/test-cases/](docs/test-cases/) | Test case và [ma trận truy vết](docs/test-cases/traceability.md) |
| [docs/open-questions.md](docs/open-questions.md) | **Mọi câu hỏi mở** (`OQ-NNN`), người quyết định, việc bị chặn |
| [source-code/contracts/](source-code/contracts/) | OpenAPI và JSON Schema event (nguồn sự thật khi code) |

Tính năng đã đi hết quy trình tài liệu: **F-PAY-03 Chuyển tiền ví–ví** (SRS, UI, test case, hợp đồng API). **F-ACC-01 Đăng ký & đăng nhập** đã có SRS, data model và hợp đồng; còn UI và test case.

## Quy trình làm việc

```
requirements → srs-feature-map → srs-feature-spec ─┬→ ui-ux-spec ────────┐
                                                    ├→ data-model-design ─┴→ api-contract
                                                    └→ srs-test-cases → unit-test-java-nextjs
system-design-java-nextjs: kiến trúc, giao tiếp giữa service, review, ADR, scaffold
```

Mỗi bước là một skill của Claude Code trong [.claude/skills/](.claude/skills/). Một tính năng đi theo thứ tự SRS → (UI, data model) → hợp đồng API → test case → code; không code tính năng chưa có SRS được duyệt.

## Kiểm tra hợp đồng API

Cần Docker. Chạy từ thư mục gốc repo:

```powershell
docker run --rm -v "${PWD}/source-code/contracts:/spec" -w /spec redocly/cli:latest lint openapi/payment/public.yaml
```

Thay đường dẫn bằng `openapi/wallet/public.yaml` hoặc `openapi/identity/public.yaml` cho các file còn lại. Schema event ở `events/` kiểm bằng Ajv (draft 2020-12), xem bước 5 của skill [api-contract](.claude/skills/api-contract/SKILL.md).

## Quy tắc cần nhớ

Đầy đủ trong [CLAUDE.md](CLAUDE.md). Những điều không được vi phạm:

- Không dùng `float`/`double` cho tiền; chỉ `ledger` đổi số dư.
- API ghi tiền bắt buộc có `Idempotency-Key`, tra key trước mọi kiểm tra khác.
- Gọi ngân hàng timeout thì trạng thái là `UNKNOWN`; `UNKNOWN` không bao giờ tự thành "thất bại".
- Con số pháp lý (hạn mức, ngưỡng xác thực...) chưa được Pháp chế xác nhận thì là tham số cấu hình.
- Không tự đặt quy tắc nghiệp vụ: lấy từ tài liệu, ghi `[Giả định]`, hoặc đưa vào câu hỏi mở.

## Việc đang chờ

50 câu hỏi mở, trong đó 6 câu P1 chặn thiết kế, code hoặc test của luồng tiền: OQ-002, 003, 004, 006 (Pháp chế), OQ-024 (cơ chế ép lỗi khi test), OQ-027 (chuẩn hạ tầng công ty). Các câu P2 chặn từng phần của F-ACC-01 (đăng nhập 2FA, nhận diện thiết bị, SĐT tài khoản đã đóng...) và F-PAY-03 (hạ cấp yếu tố xác thực, OQ-049). Xem [docs/open-questions.md](docs/open-questions.md).
