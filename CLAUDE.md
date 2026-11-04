# EWallet

Ví điện tử của một tổ chức trung gian thanh toán, liên kết với tài khoản ngân hàng của khách. Giai đoạn 1 gồm: khách hàng cá nhân (B2C) trên web và back-office (CMS); tích hợp bên thứ 3 (ngân hàng, eKYC, thông báo, AML) **chỉ có interface + fake**.

Trạng thái: đang ở giai đoạn tài liệu; `source-code/` còn trống. Mốc tiếp theo là M1 (nền tảng) theo design §12.4.

Ngôn ngữ làm việc: **tiếng Việt**, giữ thuật ngữ kỹ thuật tiếng Anh. Code, tên bảng, tên cột, ID bằng tiếng Anh.

## Bản đồ tài liệu

| Tài liệu | Nội dung | Nguồn sự thật cho |
|---|---|---|
| `docs/requirements.md` | Yêu cầu FR/IR/NFR/PR, quyết định đã chốt, câu hỏi mở | *Cái gì* cần làm |
| `docs/adr/` | Quyết định kiến trúc (ADR-0001: microservices) | Kiến trúc; ADR mới hơn thắng |
| `docs/design/*-design.md` | Kiến trúc, 14 service, luồng, nhất quán, bảo mật, triển khai | Service nào làm gì, giao tiếp ra sao |
| `docs/design/*-api.md` | API khách/admin/nội bộ, danh mục event, provider interface | Hợp đồng HTTP và event |
| `docs/design/*-data-model.md` | DDL theo từng service | Schema |
| `docs/srs/index.md` | 24 tính năng + 8 cross-cutting, phủ 110 yêu cầu | Ranh giới tính năng |
| `docs/srs/features/` | Đặc tả từng tính năng (FR/BR/AC) | Hành vi chi tiết |
| `docs/ui/` | Design system + đặc tả UI từng tính năng | Màn hình, trạng thái, câu chữ |
| `docs/test-cases/` | Test case từng tính năng + ma trận truy vết | Cần kiểm chứng những gì |
| `docs/open-questions.md` | **Mọi câu hỏi mở** của mọi tài liệu (ID `OQ-NNN`), người quyết định, việc bị chặn | Còn gì chưa quyết |

Tài liệu mâu thuẫn nhau: không chọn một bên ngầm. Nêu mâu thuẫn ra, hoặc sửa tài liệu sai khi đã rõ bên nào đúng, và ghi vào lịch sử thay đổi của tài liệu đó.

## Quy trình & skill

```
requirements → srs-feature-map → srs-feature-spec ─┬→ ui-ux-spec ────────┐
                                                    ├→ data-model-design ─┴→ api-contract (contracts/)
                                                    └→ srs-test-cases → unit-test-java-nextjs
system-design-java-nextjs: kiến trúc, giao tiếp giữa service, review, ADR, scaffold
```

- Dùng skill của dự án (`.claude/skills/`) thay cho skill chung tương ứng (ví dụ ưu tiên `system-design-java-nextjs` hơn `engineering:system-design`).
- Một tính năng đi theo thứ tự: SRS → (UI, data model) → hợp đồng API → test case → code. Không code tính năng chưa có SRS được duyệt.
- `source-code/contracts/` (OpenAPI, JSON Schema event) là nguồn sự thật của hợp đồng khi code; `docs/design/*-api.md` là bản tổng quan, cập nhật cùng lúc.

## Quy tắc không được vi phạm

**Tiền**
- Tiền là số nguyên đơn vị đồng (`long`/`bigint`); không bao giờ dùng `float`/`double`.
- **Chỉ service `ledger` thay đổi số dư**, theo sổ cái kép; bút toán chỉ được thêm, sửa sai bằng bút toán đảo.
- Mọi API ghi tiền bắt buộc có `Idempotency-Key`. **Tra key trước mọi kiểm tra khác** (OTP, phí, trạng thái ví).
- Gọi ngân hàng/đối tác: **ghi bước "đã gọi" trước khi gọi**; timeout thì trạng thái là `UNKNOWN`, giữ nguyên tiền đang tạm giữ. **`UNKNOWN` và `NOT_FOUND` không bao giờ tự thành "thất bại"**; chỉ câu trả lời chắc chắn của đối tác hoặc đối soát mới chốt.
- Kiểm chủ sở hữu (ví, lệnh, liên kết ngân hàng) ở service giữ dữ liệu đó.

**Kiến trúc**
- Database-per-service: không đọc/ghi DB của service khác, không khoá ngoại xuyên service. Không quyết định về tiền dựa trên bản sao dữ liệu nhận qua event.
- Event qua outbox; consumer idempotent (inbox); event có `aggregateversion`. Không đưa OTP, CCCD, số tài khoản dạng rõ lên Kafka hay vào log.
- Lời gọi nội bộ có tác dụng phụ phải idempotent; không gọi vòng giữa service.
- Fake provider bị cấm ở profile `prod`.

**Nghiệp vụ & pháp lý**
- **Không tự đặt ra quy tắc nghiệp vụ.** Chỗ yêu cầu chưa nói thì: lấy từ design (có trích dẫn), hoặc ghi `[Giả định]`, hoặc đưa vào câu hỏi mở.
- Con số pháp lý (⚖️: hạn mức, ngưỡng xác thực, thời hạn lưu trữ...) chưa được Pháp chế xác nhận → luôn là **tham số cấu hình**, không viết như sự thật.

## Quy ước ID

| Loại | Dạng | Ví dụ |
|---|---|---|
| Yêu cầu | `FR-<NHÓM>-NN`, `NFR-<X>-NN`, `IR-NN`, `PR-NN` | `FR-P2P-02` |
| Tính năng | `F-<NHÓM>-NN` | `F-PAY-03` |
| Mục trong spec | `<Tính năng>.UC/FR/BR/NFR/AC-NN` | `F-PAY-03.AC-05` |
| Màn hình | `<Tính năng>.S-NN` | `F-PAY-03.S-02` |
| Test case | `TC-<NHÓM>-NNN` | `TC-P2P-001` |
| Cross-cutting | `CC-<CHỦ ĐỀ>` | `CC-MONEY` |
| ADR | `ADR-NNNN` | `ADR-0001` |

ID không bao giờ đổi hay dùng lại; mục bị bỏ thì đánh dấu `DEPRECATED`/`REMOVED` thay vì xoá.

## Stack (đã chốt)

- Backend: Java 21 LTS, Spring Boot, Gradle (Kotlin DSL), monorepo có `platform` starter dùng chung.
- Data: PostgreSQL (mỗi service một DB), Kafka + Schema Registry, Redis.
- Frontend: Next.js (App Router, TypeScript), Tailwind + shadcn/ui; `web` và `cms` là BFF gọi qua gateway.
- Hạ tầng: Kubernetes + service mesh (mTLS), cloud. Câu hỏi còn mở: dữ liệu có bắt buộc đặt tại Việt Nam không.

## Lệnh

Chưa có code. Cập nhật mục này khi scaffold M1 (build, test, chạy local).

## Cách làm việc

- Báo kết quả trung thực: chưa chạy được build/test/kiểm cú pháp thì nói rõ là chưa kiểm.
- Sửa tài liệu: tăng phiên bản và ghi lịch sử thay đổi trong tài liệu đó.
- Câu hỏi mở mới: ghi trong tài liệu đang viết **và** thêm vào `docs/open-questions.md` (ID `OQ-` tiếp theo). Câu hỏi được trả lời: cập nhật ở đó, rồi cập nhật tài liệu nguồn và gỡ trạng thái `BLOCKED` của test case liên quan.
- Thay đổi kiến trúc hoặc lật lại quyết định đã chốt → viết ADR mới.
