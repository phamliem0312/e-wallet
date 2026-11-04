---
name: system-design-java-nextjs
description: Thiết kế kiến trúc cho dự án EWallet - microservices Java/Spring Boot + Next.js, Kafka, PostgreSQL database-per-service, domain ví điện tử (ledger, nạp/rút/chuyển tiền, tích hợp ngân hàng). Dùng khi người dùng muốn viết hoặc cập nhật design doc, chia ranh giới service, quyết định service nào sở hữu chức năng/dữ liệu nào, thiết kế giao tiếp đồng bộ/bất đồng bộ giữa service (REST nội bộ, event Kafka, saga, outbox), xử lý lỗi và nhất quán xuyên service, scaffold monorepo hoặc thêm service mới, review thiết kế hay PR kiến trúc, so sánh trade-off công nghệ và viết ADR. Kích hoạt cả khi người dùng chỉ hỏi "nên đặt logic này ở service nào", "luồng này cần saga không", "thêm service X", "event này ai consume", "review thiết kế", "ADR cho ...", hoặc nhắc Spring Boot, Kafka, microservice, saga, outbox, idempotency trong repo này. Ưu tiên skill này hơn engineering:system-design khi làm việc trong repo EWallet. Không dùng cho data model chi tiết (data-model-design), giao diện (ui-ux-spec), đặc tả SRS (srs-feature-spec) hay viết test (unit-test-java-nextjs).
---

# System Design — EWallet (microservices Java/Spring Boot + Next.js)

Skill này giúp thiết kế, scaffold và review kiến trúc của EWallet: một ví điện tử, nơi **tiền phải đúng tuyệt đối và mọi thao tác phải truy vết được**, chạy trên 14 microservice.

Viết bằng **tiếng Việt**, giữ thuật ngữ kỹ thuật tiếng Anh (saga, outbox, idempotency key, consumer lag...). Người đọc là kỹ sư; viết gọn, có lý do cho mỗi quyết định.

## Bước 0 — Kiến trúc hiện hành lấy từ ADR, không lấy từ trí nhớ

Trước mọi việc, đọc `docs/adr/` và design doc hiện hành (`docs/design/*-design.md`). Hiện tại [ADR-0001](../../../docs/adr/0001-microservices.md) chốt **microservices mịn (14 service)**, và các mặc định dưới đây dựa trên ADR đó. Nếu có ADR mới hơn thay thế, **theo ADR mới** và báo cho người dùng biết skill này cần cập nhật.

Danh sách service, chủ sở hữu dữ liệu và chiều gọi đồng bộ nằm ở mục 5 của design doc. Đó là nguồn sự thật khi trả lời câu hỏi "việc này thuộc service nào".

## Bước 1 — Chọn chế độ (và chuyển việc không thuộc skill này)

| Chế độ | Khi nào | Đọc thêm |
|---|---|---|
| **Design doc** | Thiết kế luồng/tính năng xuyên service, cập nhật kiến trúc, thêm service | `references/design-doc-template.md`, `references/backend-spring.md`, `references/fintech-patterns.md`, `references/frontend-nextjs.md` (khi có phần BFF) |
| **Scaffold** | Dựng monorepo, nền tảng M1, thêm một service mới | `references/scaffold.md`, `references/backend-spring.md` |
| **Review** | Review thiết kế, design doc, PR kiến trúc | `references/review-checklist.md`, `references/fintech-patterns.md` |
| **Trade-off / ADR** | "Nên dùng A hay B", "viết ADR" | `references/tradeoffs.md` |

Việc **không thuộc skill này**, chuyển sang skill tương ứng:

| Việc | Skill |
|---|---|
| Bảng, cột, index, DDL chi tiết | `data-model-design` |
| Màn hình, user flow, wireframe | `ui-ux-spec` |
| Đặc tả yêu cầu từng tính năng | `srs-feature-map`, `srs-feature-spec` |
| Test case, code test | `srs-test-cases`, `unit-test-java-nextjs` |

Design doc vẫn nêu ở mức kiến trúc "service X sở hữu dữ liệu Y, ràng buộc chống trùng nằm ở đâu", còn chi tiết bảng thì giao cho `data-model-design`.

Chỉ đọc reference cần cho chế độ đang chạy.

## Bước 2 — Làm rõ trước khi thiết kế

- Đọc SRS của tính năng nếu có (`docs/srs/features/`): use case, quy tắc nghiệp vụ, danh mục lỗi. Thiết kế phục vụ SRS, không tự thêm nghiệp vụ.
- Xác định: actor, hệ thống ngoài liên quan, quy mô (mặc định theo NFR: 500k user, 500 TPS lúc cao điểm), ràng buộc pháp lý (⚖️).
- Thiếu thông tin mà không suy ra được từ repo → hỏi. Người dùng muốn đi nhanh → nêu giả định rồi làm.

## Bước 3 — Nguyên tắc thiết kế mặc định (theo ADR-0001)

Lệch khỏi các nguyên tắc này được, nhưng phải nói lý do, và nếu lệch lớn thì viết ADR.

**Ranh giới**
1. **Dùng service đã có trước khi tạo service mới.** Đối chiếu bảng service ở design §5. Chỉ tạo service mới khi có dữ liệu riêng, vòng đời riêng, và lý do scale hoặc cô lập rõ ràng. Mỗi service mới là thêm một thứ phải vận hành.
2. **Database-per-service.** Không service nào đọc hay ghi DB của service khác, không khoá ngoại xuyên service. Cần dữ liệu của service khác thì gọi API của chủ sở hữu, hoặc giữ bản sao nhận qua event (có `source_version`). **Không quyết định về tiền dựa trên bản sao.**

**Tiền**

3. **Chỉ `ledger` thay đổi số dư.** Mọi thao tác cần nguyên tử về tiền (trừ A, cộng B, ghi phí, giữ tiền) nằm trong **một DB transaction của `ledger`**. Nhờ vậy không cần transaction phân tán.
4. **Mọi lời gọi nội bộ có tác dụng phụ đều idempotent** theo id nghiệp vụ (`orderId`, `approvalId`, `externalRef`), nên bên gọi được retry. Timeout xếp lớp: timeout của bên gọi lớn hơn timeout của bên được gọi.
5. **Saga có điều phối (orchestration)**, `payment` điều phối các luồng tiền. Trạng thái và bước saga lưu trong DB của bên điều phối. **Ghi bước "đã gọi đối tác" trước khi gọi**; từ bước đó trở đi chỉ được hỏi trạng thái, không bao giờ gửi lại. Chỉ các bước trước `ledger` mới cần bù trừ.
6. **Tra idempotency key trước mọi kiểm tra khác** (OTP, phí, trạng thái ví), để gửi lại sau khi đã thành công luôn nhận lại kết quả cũ. **Kết quả không chắc chắn là `UNKNOWN`, không bao giờ là thất bại**; chỉ câu trả lời chắc chắn của đối tác hoặc đối soát mới chốt được. **Kiểm chủ sở hữu** (liên kết ngân hàng, lệnh, ví) ở service giữ dữ liệu đó.

**Giao tiếp**

7. **Đồng bộ (REST `/internal/**`)** chỉ khi cần câu trả lời ngay để quyết định. Chiều gọi một hướng, **không gọi vòng**, chuỗi gọi ngắn. `/internal/**` không bao giờ đi qua gateway; service mesh mTLS + AuthorizationPolicy quy định ai được gọi ai.
8. **Bất đồng bộ (Kafka)** để báo "việc đã xảy ra": outbox trong DB của service phát, topic `<service>.events`, envelope CloudEvents có `aggregateversion`, schema đăng ký trên Schema Registry và chỉ được thay đổi tương thích ngược. Consumer idempotent (bảng `inbox`), có DLT. **Không đưa dữ liệu nhạy cảm dạng rõ (OTP, CCCD) lên Kafka.**

**Biên ngoài & vận hành**

9. **Next.js là BFF mỏng** gọi `api-gateway`/`admin-gateway`; không chứa rule về tiền; token nằm trong cookie httpOnly.
10. **Quan sát được xuyên service:** `traceparent` đi qua cả HTTP lẫn header Kafka; audit phát qua outbox; đo consumer lag, outbox tồn đọng, lệnh `UNKNOWN`.

Chi tiết ở `references/backend-spring.md` và `references/fintech-patterns.md`.

## Bước 4 — Output theo chế độ

- **Design doc:** thay đổi kiến trúc hiện có thì **cập nhật design doc hiện hành** (tăng phiên bản, ghi lịch sử), không tạo tài liệu song song. Tính năng lớn có thể có tài liệu riêng `docs/design/<yyyy-mm-dd>-<slug>.md`, theo `references/design-doc-template.md` và liên kết về design doc chính. Diagram dùng Mermaid. Thay đổi API nội bộ hoặc event thì cập nhật luôn tài liệu API/event.
- **Scaffold:** theo `references/scaffold.md`. Chỉ sinh khung chạy được + một lát cắt end-to-end; không sinh hàng loạt service rỗng.
- **Review:** danh sách vấn đề xếp theo mức (🔴 chặn / 🟠 nên sửa / 🟡 gợi ý). Mỗi mục: vấn đề → tình huống gây lỗi cụ thể → cách sửa. Kết luận "OK để triển khai" hoặc "Cần sửa các mục 🔴 trước".
- **Trade-off / ADR:** theo `references/tradeoffs.md`; luôn chốt **một khuyến nghị**. Đánh số ADR tiếp theo số lớn nhất trong `docs/adr/`.
- **Mọi chế độ:** câu hỏi mở mới thêm vào `docs/open-questions.md` (ID `OQ-` tiếp theo); khi một thiết kế trả lời được câu hỏi đang mở, chuyển câu đó sang "Đã trả lời" và cập nhật tài liệu nguồn.

## Bước 5 — Tự kiểm trước khi giao

- Có luồng nào thay đổi số dư ngoài `ledger`, hoặc cần transaction xuyên service không?
- Mọi lời gọi nội bộ có tác dụng phụ đều có id idempotent? Có lời gọi vòng hay chuỗi gọi quá dài không?
- Lời gọi ra hệ thống ngoài có timeout, ghi bước trước khi gọi, trạng thái `UNKNOWN`, và đường chốt qua đối soát?
- Mỗi event mới có service phát, consumer, khoá partition, `aggregateversion`, và được ghi vào danh mục event?
- Có service hoặc thành phần nào được thêm vào mà không có lý do? Nếu có, bỏ đi.
- Mỗi nhánh lỗi quan trọng có cách tái hiện ở môi trường test (design doc §11.1)? Không có thì test case của nhánh đó sẽ bị chặn.
- Đã cập nhật design doc, API doc, ADR liên quan chưa?
