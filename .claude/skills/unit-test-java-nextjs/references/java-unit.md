# Java unit test

## Thư viện
`spring-boot-starter-test` đã có sẵn JUnit 5, AssertJ, Mockito, JsonPath. Chỉ thêm khi cần:
- `net.jqwik:jqwik`: property-based test cho bất biến (sổ cái cân, phí luôn trong [min, max]).
- `org.testcontainers:*`: xem `java-integration.md`.

Không thêm thư viện mới nếu repo chưa dùng, trừ khi người dùng đồng ý.

## Cấu trúc & đặt tên

```java
class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @Test
    void percentFee_isRoundedUpToNextDong() {
        var rule = aFeeRule().percentBp(10).build();          // 0,1%

        long fee = calculator.calculate(rule, 100_001);       // 100.001 đ × 0,1% = 100,001 đ

        assertThat(fee).isEqualTo(101);                       // làm tròn lên → 101 đ
    }

    @ParameterizedTest(name = "amount={0} → fee={1}")
    @CsvSource({
        "1,        1000",    // dưới min → min
        "1000000,  1000",
        "50000000, 20000",   // vượt max → max
    })
    void fee_isClampedBetweenMinAndMax(long amount, long expected) {
        var rule = aFeeRule().percentBp(10).minFee(1_000).maxFee(20_000).build();
        assertThat(calculator.calculate(rule, amount)).isEqualTo(expected);
    }
}
```

- Tên test theo dạng `method_condition_expectedResult` hoặc câu mô tả hành vi. Theo đúng quy ước repo đang dùng.
- `@Nested` để nhóm theo tình huống khi một class có nhiều nhánh (`class WhenBankTimesOut { ... }`).
- `@ParameterizedTest` cho bảng biên. Mỗi dòng có comment giải thích vì sao dòng đó quan trọng.

## Test data builder

```java
public final class TestData {
    public static PaymentOrderBuilder aTopupOrder() {
        return new PaymentOrderBuilder()
            .id(UUID.fromString("00000000-0000-7000-8000-000000000001"))
            .type(TOPUP).status(PROCESSING).amount(100_000).fee(0)
            .customerId(CUSTOMER_A).walletId(WALLET_A).bankCode("FAKE");
    }
}
```
- Mặc định phải **hợp lệ**. Test chỉ override những trường liên quan.
- Đặt trong `src/test/java/.../support/` (hoặc `testFixtures` của Gradle nếu nhiều module cùng dùng).

## Thời gian & ID

```java
var clock = Clock.fixed(Instant.parse("2026-11-04T16:59:59Z"), ZoneOffset.UTC); // 23:59:59 giờ VN
var service = new LimitService(repo, clock);
```
- Test kỳ ngày/tháng: chọn thời điểm **sát nửa đêm giờ VN** (16:59:59Z và 17:00:00Z) để bắt lỗi nhầm UTC với giờ VN.
- ID: inject `IdGenerator`, test dùng bản sinh tuần tự hoặc cố định.

## Fake vs mock

| Tình huống | Dùng |
|---|---|
| Provider có fake chính chủ (`FakeBankGateway`, `FakeEkycProvider`, `FakeNotificationSender`, `FakeAmlScreeningProvider`) | **Fake**: đặt kịch bản bằng `script(...)` |
| Repository trong unit test của service | Fake in-memory đơn giản (map) nếu logic phụ thuộc dữ liệu đã lưu; mock nếu chỉ cần kiểm lời gọi |
| Client gọi service khác (`LedgerClient`, `LimitClient`) | Mock (Mockito), hoặc stub trả kết quả theo kịch bản |
| Ép lỗi hiếm (timeout, exception lạ) | Mock `thenThrow` / `thenAnswer` |
| Value object, entity, class đang test | **Không bao giờ mock** |

Với Mockito:
- `verify` chỉ những lời gọi **là** hành vi cần kiểm (ví dụ "không gọi ngân hàng lần 2"), không verify mọi thứ.
- Dùng `verifyNoMoreInteractions` một cách có chủ đích, ví dụ: recovery ở step `BANK_CALLED` không được gọi `debit` thêm lần nào.
- Tránh `any()` cho tham số tiền: dùng giá trị cụ thể hoặc `ArgumentCaptor` rồi assert số tiền.

```java
@Test
void recovery_atBankCalledStep_onlyQueriesStatus_neverResends() {
    var order = aWithdrawOrder().sagaStep(BANK_CALLED).build();
    when(bankGateway.status(order.externalRef())).thenReturn(new StatusResult.Pending());

    recovery.resume(order);

    verify(bankGateway).status(order.externalRef());
    verify(bankGateway, never()).credit(any());
    assertThat(orders.get(order.id()).status()).isEqualTo(UNKNOWN);
}
```

## Kết quả kiểu sealed

```java
assertThat(result).isInstanceOfSatisfying(TransferResult.Failed.class,
    f -> assertThat(f.reasonCode()).isEqualTo("BANK_LINK_INVALID"));
```
Dùng `switch` pattern trong code production; trong test dùng `isInstanceOfSatisfying` để assert cả kiểu lẫn nội dung.

## Controller (`@WebMvcTest`)

```java
@WebMvcTest(TransferController.class)
@Import(SecurityTestConfig.class)
class TransferControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PaymentFacade payments;   // Spring Boot 3.4+; bản cũ dùng @MockBean

    @Test
    void missingIdempotencyKey_returns400ProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/transfers").with(jwt().jwt(j -> j.subject(CUSTOMER_A.toString())))
                .contentType(APPLICATION_JSON).content("""
                    {"recipientPhone":"+84900000002","amount":100000,"expectedFee":0}"""))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        verifyNoInteractions(payments);
    }
}
```
Kiểm ở mức controller:
- Validation và mã lỗi (`code` trong ProblemDetail).
- Thiếu hoặc sai JWT → 401; khác chủ sở hữu → 403/404.
- Header bắt buộc.
- Response 200 hay 202 theo trạng thái order.

Không kiểm lại logic nghiệp vụ ở đây.

## Property-based (jqwik), cho bất biến

```java
@Property
void anyBalancedJournal_keepsTrialBalanceZero(@ForAll("journals") List<Entry> entries) {
    var ledger = new InMemoryLedger();
    ledger.post(journalOf(entries));
    assertThat(ledger.sumDebits()).isEqualTo(ledger.sumCredits());
}
```
Dùng cho: sổ cái cân, phí nằm trong [min, max], hạn mức đã dùng không vượt max sau mọi chuỗi reserve/release.
