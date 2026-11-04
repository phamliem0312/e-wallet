---
name: unit-test-java-nextjs
description: Viết và sửa unit test (và integration test khi cần) cho backend Java/Spring Boot và frontend Next.js/React, ưu tiên code tài chính/ví điện tử (ledger, số dư, phí, hạn mức, idempotency, state machine, saga). Dùng skill này khi người dùng muốn viết test, thêm test cho class/service/component, tăng coverage, viết test trước khi code (TDD), sửa test đang fail hoặc chạy chập chờn, hoặc review chất lượng test. Hãy dùng kể cả khi người dùng chỉ nói "test cái này", "viết test cho PaymentService", "cover case lỗi", "kiểm tra hàm tính phí", hay nhắc JUnit, Mockito, AssertJ, Testcontainers, Vitest, Jest, Testing Library, @WebMvcTest, @SpringBootTest.
---

# Unit test — Java (Spring Boot) + Next.js

Mục tiêu của test là **bắt được bug trước khi bug chạm vào tiền thật**, chứ không phải đạt một con số coverage. Một test tốt fail vì đúng một lý do, đọc vào là hiểu hành vi được kiểm, và không vỡ khi refactor phần bên trong mà hành vi không đổi.

Viết giải thích bằng **tiếng Việt**. Tên test, code và thuật ngữ giữ tiếng Anh, hoặc dùng `@DisplayName` tiếng Việt nếu repo đã có quy ước đó.

## Bước 1 — Đọc trước khi viết

1. Đọc code cần test và **mọi chỗ gọi nó** để biết hành vi thật được kỳ vọng là gì.
2. Đọc các test đang có cạnh đó: theo đúng quy ước của repo (đặt tên, builder, base class, fake có sẵn). Repo đã có cách làm thì không tự nghĩ ra cách mới.
3. Nếu có tài liệu thiết kế (`docs/design/`, `docs/requirements.md`), đối chiếu quy tắc nghiệp vụ ở đó: test phải kiểm **quy tắc**, không phải kiểm lại đúng những gì code đang làm.
4. Xác định **mức test** phù hợp:

| Code cần test | Mức test | Đọc thêm |
|---|---|---|
| Domain, value object, tính toán (phí, hạn mức, state machine), hàm thuần | Unit thuần, không có Spring | `references/java-unit.md` |
| Application service điều phối nhiều port | Unit, port thay bằng fake/mock | `references/java-unit.md` |
| Controller: validate, mã lỗi, ProblemDetail, security | `@WebMvcTest` | `references/java-unit.md` |
| SQL, lock, constraint, transaction, outbox, Kafka consumer | Integration test với Testcontainers (không dùng H2) | `references/java-integration.md` |
| Component, hook, schema Zod, hàm format, server action | Vitest + Testing Library | `references/nextjs-unit.md` |
| Bất kỳ code nào chạm tới tiền | Thêm các ca trong checklist | `references/fintech-test-cases.md` |

Chỉ đọc reference cần cho mức test đang viết.

## Bước 2 — Liệt kê ca test trước khi code

Viết ra danh sách ca test ngắn gọn trước, cho từng nhóm:
- **Happy path:** một ca đại diện, không cần nhiều.
- **Biên:** 0, 1, đúng ngưỡng, ngưỡng ± 1, giá trị lớn nhất.
- **Lỗi nghiệp vụ:** từng mã lỗi mà code có thể trả về.
- **Lỗi kỹ thuật:** timeout, `Unknown`, retry, gọi trùng.
- **Bất biến:** những điều phải luôn đúng, ví dụ sổ cái cân, số dư không âm.

Nếu người dùng chỉ yêu cầu "viết test", hãy ưu tiên các ca **có khả năng bắt bug cao nhất**: nhánh lỗi và biên. Đừng dàn đều cho mọi getter.

## Bước 3 — Viết test theo nguyên tắc

1. **Kiểm hành vi quan sát được** (giá trị trả về, trạng thái được lưu, event được phát, lời gọi ra port), không kiểm chi tiết bên trong như thứ tự gọi hàm private. Test ràng chặt vào implementation sẽ vỡ mỗi lần refactor và mất lòng tin của team.
2. **Arrange – Act – Assert**, mỗi test một hành vi. Tên test nói rõ điều kiện và kết quả: `withdraw_whenBankTimesOut_keepsHoldAndMarksUnknown`.
3. **Ưu tiên fake hơn mock.** Provider đã có fake chính chủ (`FakeBankGateway`, `FakeNotificationSender`...) thì dùng fake: nó mô phỏng hành vi thật, như idempotency theo `externalRef`, nên test không phải tự dựng lại. Chỉ mock các port đơn giản, hoặc khi cần ép một lỗi khó tạo. **Không mock** value object, domain entity hay class đang được test.
4. **Xác định (deterministic):** inject `Clock`, sinh ID qua một generator inject được, không `Thread.sleep`, không phụ thuộc thứ tự chạy test, không phụ thuộc múi giờ của máy (đặt `Asia/Ho_Chi_Minh` một cách tường minh khi test kỳ ngày/tháng).
5. **Dữ liệu test qua builder/fixture** có giá trị mặc định hợp lệ. Test chỉ ghi ra những giá trị liên quan đến hành vi đang kiểm, để người đọc thấy ngay điều gì quan trọng.
6. **Assert cụ thể:** `assertThat(order.status()).isEqualTo(UNKNOWN)`, không `assertNotNull(order)`. Với số tiền, assert đúng con số, kể cả phí.
7. **Không viết test chỉ để tăng coverage** (getter/setter, record, mapping đơn giản). Một test không thể fail thì không có giá trị.

## Bước 4 — Chạy và báo cáo trung thực

- Chạy đúng phạm vi vừa viết trước, sau đó chạy cả module:
  - Gradle: `./gradlew :services:<svc>:test --tests '<Class>'`, sau đó `./gradlew :services:<svc>:test`
  - Maven: `mvn -pl <module> test -Dtest=<Class>`
  - Frontend: `pnpm vitest run <path>`, sau đó `pnpm vitest run`
- Test mới **phải fail** khi hành vi sai. Với logic quan trọng (tiền, trạng thái), làm thử: tạm sửa sai code hoặc đổi kỳ vọng, xác nhận test đỏ, rồi hoàn tác. Nếu không làm được thì nói rõ là chưa kiểm.
- Báo kết quả thật: số test pass/fail, kèm output lỗi nếu có. Không chạy được (thiếu Docker cho Testcontainers, thiếu JDK...) thì nói rõ là **chưa chạy**, không viết "test đã pass".
- Nếu viết test mà phát hiện **bug trong code**: đừng sửa test cho khớp với bug. Báo bug, ghi rõ ca test nào chứng minh nó, rồi hỏi người dùng có muốn sửa code không.

## Bước 5 — Tự kiểm trước khi giao

- Mỗi ca trong danh sách ở Bước 2 đã có test, hoặc đã nói rõ vì sao bỏ?
- Có test nào luôn pass bất kể code (assert yếu, mock trả đúng thứ đang assert)?
- Code tiền đã qua `references/fintech-test-cases.md` chưa?
- Test có chạy ổn định khi chạy song song và chạy nhiều lần không?
