---
name: api-contract
description: Thiết kế và đối chiếu hợp đồng API cho dự án EWallet - viết OpenAPI 3.1 (API khách, admin, nội bộ) và JSON Schema cho event Kafka trong source-code/contracts/, từ SRS, đặc tả UI và data model; đồng thời đối chiếu xem API có đủ dữ liệu cho từng màn hình, đủ mã lỗi theo SRS, đủ event theo SRS, và có phá tương thích không. Dùng skill này khi người dùng muốn "thiết kế API", "viết OpenAPI/Swagger", "định nghĩa endpoint", "hợp đồng API cho tính năng X", "schema event", "API có đủ cho màn hình không", "đối chiếu API với SRS/UI", "thêm field vào response", "đổi API có phá tương thích không", hoặc trước khi frontend và backend code song song, kể cả khi chỉ đưa tên tính năng. Ưu tiên skill này cho mọi thay đổi hợp đồng HTTP/event trong repo EWallet; kiến trúc giao tiếp tổng thể dùng system-design-java-nextjs.
---

# Hợp đồng API & event

Với 14 service và hai frontend, **hợp đồng lệch là lỗi hay gặp nhất**. Màn hình cần một field mà API không trả; SRS định nghĩa một mã lỗi mà API không bao giờ sinh ra; consumer đọc một field mà producer vừa đổi tên. Skill này làm hai việc:
1. **Đối chiếu:** chứng minh, bằng bảng, rằng API đáp ứng đủ nhu cầu của SRS, UI và các service tiêu thụ.
2. **Thiết kế:** viết hợp đồng (OpenAPI, JSON Schema) đủ chặt để sinh client, validate request và chạy contract test.

`contracts/` là **nguồn sự thật khi code**. `docs/design/*-api.md` là bản tổng quan cho người đọc và phải được cập nhật cùng.

Viết bằng **tiếng Việt** trong tài liệu và `description`; tên path, field, mã lỗi bằng tiếng Anh.

## Bước 1 — Đọc nguồn
1. **SRS tính năng** (`docs/srs/features/`): FR, dữ liệu (§3.5), giao diện (§4.2), **danh mục lỗi (§6)**, đầu ra phụ và event (§3.6, `EV-nn`).
2. **Đặc tả UI** (`docs/ui/features/`): bảng "Dữ liệu" của từng màn hình, bảng ánh xạ lỗi, câu hỏi mở về API.
3. **Data model** (`docs/design/*-data-model.md`): field nào thật sự có để trả.
4. **API doc hiện có** và **`contracts/`** hiện có: quy ước, endpoint đã có, phiên bản.
5. **`docs/open-questions.md`**, nhóm "Thiết kế API".

## Bước 2 — Đối chiếu trước khi viết

Lập ba bảng theo `references/reconciliation.md`:

| Bảng | Câu hỏi | Thường bắt được |
|---|---|---|
| **Nhu cầu dữ liệu** | Mỗi field mà màn hình hoặc service tiêu thụ cần → endpoint/field nào trả? | Thiếu field (ví dụ màn hình kết quả cần "người nhận" mà `order` không có) |
| **Mã lỗi** | Mỗi mã trong SRS §6 → endpoint nào trả, HTTP nào, body có gì (ví dụ `remaining`)? | Mã lỗi không có nơi phát sinh; thông điệp cần dữ liệu mà body không có |
| **Event** | Mỗi `EV-nn` và mỗi consumer → topic, event, field? | Consumer cần field mà event không mang; event chứa dữ liệu nhạy cảm |

Mỗi dòng thiếu sẽ thành một thay đổi hợp đồng ở bước 3, hoặc thành câu hỏi mở nếu cần người khác quyết định (ví dụ có lộ dữ liệu cho FE không là quyết định của Product/Bảo mật, không phải của API).

## Bước 3 — Thiết kế theo quy ước

Áp dụng `references/conventions.md`:
- path, method;
- `Idempotency-Key` cho lệnh tiền;
- quy ước response 200/202/4xx của lệnh tiền;
- ProblemDetail + `code`;
- tiền là số nguyên;
- phân trang cursor;
- bảo mật;
- **quy tắc tương thích**.

Dùng mẫu trong `references/openapi-patterns.md` (component dùng chung, endpoint lệnh tiền, schema event theo CloudEvents).

Nguyên tắc:
- **Hợp đồng phản ánh SRS, không thêm nghiệp vụ.** Field mới phải truy vết được về một dòng của bảng đối chiếu.
- **Không lộ dữ liệu theo mặc định:** chỉ trả field mà một consumer cụ thể cần; dữ liệu cá nhân trả dạng đã che; không có field "để sau này".
- **Mọi schema đều đóng:** `additionalProperties: false` cho request (từ chối field lạ); response và event thì cho phép thêm field mới về sau (client phải bỏ qua field lạ).
- **Mỗi schema có `example`** khớp với AC của SRS (dùng chính số liệu trong AC).

## Bước 4 — Ghi file

```
source-code/contracts/
├── openapi/
│   ├── common.yaml                       # Problem, ErrorCode, Money, Cursor, header dùng chung
│   └── <service>/
│       ├── public.yaml                   # /api/v1/** (qua api-gateway)
│       ├── admin.yaml                    # /admin/v1/** (qua admin-gateway)
│       └── internal.yaml                 # /internal/** (chỉ trong mesh)
└── events/<service>/<EventName>/v<N>.json   # JSON Schema 2020-12, phần `data` của CloudEvents
```
Chỉ tạo file mà tính năng cần; không sinh khung rỗng cho các service khác.

## Bước 5 — Kiểm tra
1. **Lint** bằng Redocly CLI nếu có Docker:
   ```
   docker run --rm -v "<đường dẫn contracts>:/spec" redocly/cli:latest lint /spec/openapi/<service>/public.yaml
   ```
   Lint bắt được: `$ref` gãy, schema sai, example không khớp schema, thiếu `operationId`. Không có Docker → nói rõ là **chưa lint**.
   - Dùng `redocly.yaml` ở gốc `contracts/` (extends `recommended`, bật `no-invalid-schema-examples`, `no-invalid-media-type-examples`, `operation-4xx-response`); chạy với `-w /spec` để Redocly đọc được file cấu hình.
   - **Xác nhận lint thật sự bắt lỗi:** lần đầu chạy, cố ý sửa sai một example trong bản sao (ví dụ đổi số tiền thành chuỗi) và kiểm lint báo lỗi. Một lần lint "sạch" mà không bắt được lỗi cố ý thì không chứng minh được gì.
   - **Schema event** (Redocly không kiểm): dùng Ajv chế độ strict, draft 2020-12, kèm `ajv-formats`, qua `node:20-alpine`. Kiểm: schema compile được, mọi `examples` hợp lệ, và example thiếu trường bắt buộc bị từ chối.
2. **Đối chiếu lại** ba bảng ở bước 2: mọi dòng đã ✅ hoặc đã có câu hỏi mở.
3. **Tương thích:** hợp đồng đã có bản trước thì so sánh (ví dụ `docker run --rm -v ...:/specs tufin/oasdiff breaking /specs/old.yaml /specs/new.yaml`) và liệt kê thay đổi phá tương thích. Có thay đổi phá tương thích thì phải tăng version (`/api/v2`, `v2.json`) hoặc thiết kế lại cho tương thích.
4. Chạy checklist ở cuối `references/conventions.md`.

## Bước 6 — Cập nhật tài liệu và báo cáo
- Cập nhật `docs/design/*-api.md`: bảng endpoint, mã lỗi, danh mục event, liên kết sang file trong `contracts/`.
- `docs/open-questions.md`:
  - Câu hỏi mà hợp đồng đã giải quyết bằng một **đề xuất** → ghi đề xuất vào cột trả lời, trạng thái "Đề xuất, chờ duyệt". Không tự đóng câu hỏi thuộc quyền người khác.
  - Câu hỏi mới → thêm `OQ-` tiếp theo.
- Báo cáo:
  - endpoint/event mới hoặc đã đổi;
  - kết quả ba bảng đối chiếu (số dòng thiếu đã sửa, số dòng thành câu hỏi);
  - kết quả lint;
  - thay đổi phá tương thích (nếu có);
  - việc frontend/backend cần làm tiếp (sinh client, contract test).
