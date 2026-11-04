# Java integration test (Testcontainers)

Dùng khi hành vi cần kiểm **nằm trong DB hoặc broker**: lock, unique constraint, `UPDATE` có điều kiện, partition, Flyway migration, transaction rollback, outbox/inbox, Kafka consumer. Mock không chứng minh được những thứ này.

**Không dùng H2**: H2 khác PostgreSQL về lock, `ON CONFLICT`, partial index và `FOR UPDATE SKIP LOCKED`, nên test sẽ pass trên H2 nhưng fail trên production.

## Khung dùng chung

```java
@SpringBootTest
@Testcontainers
abstract class IntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
    // Kafka nếu service có consumer/producer:
    // @Container @ServiceConnection static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");
}
```
- Container `static` dùng chung cho cả class; bật reuse ở máy dev (`testcontainers.reuse.enable=true`) để chạy nhanh.
- Dọn dữ liệu giữa các test bằng `TRUNCATE` các bảng của service, **không** dùng `@Transactional` rollback trên test có nhiều thread hoặc test kiểm commit. Rollback sẽ che mất hành vi commit thật.
- Thiếu Docker thì test sẽ bị skip hoặc fail. Khi báo kết quả, nói rõ là chưa chạy.

## Ca bắt buộc cho code tiền

### Đồng thời: trừ tiền không âm

```java
@Test
void concurrentDebits_neverOverdraw() throws Exception {
    var wallet = openWalletWithBalance(1_000_000);
    int threads = 50;                                   // mỗi lệnh 100.000 → chỉ 10 lệnh thành công
    var pool = Executors.newFixedThreadPool(threads);
    var start = new CountDownLatch(1);
    var results = new ConcurrentLinkedQueue<Outcome>();

    for (int i = 0; i < threads; i++) {
        var ref = UUID.randomUUID();
        pool.submit(() -> { start.await(); results.add(ledger.debit(wallet, 100_000, ref)); return null; });
    }
    start.countDown();
    pool.shutdown(); pool.awaitTermination(30, SECONDS);

    assertThat(results).filteredOn(Outcome::ok).hasSize(10);
    assertThat(balanceOf(wallet)).isZero();
    assertThat(trialBalance()).isZero();                // Σ D = Σ C
}
```

### Idempotency song song
Gửi N request cùng `Idempotency-Key` cùng lúc → chỉ có **một** order, chỉ có **một** journal, và mọi response trả về cùng một `orderId`.

### Chống ghi sổ hai lần
Gọi `postJournal` hai lần cùng `(referenceType, referenceId, purpose)`:
- Lần 2 trả về journal cũ.
- Số entry không đổi.
- Nếu nội dung khác → `409`.

### Thứ tự lock / deadlock
Chạy song song P2P A→B và B→A nhiều vòng → không có deadlock mà không được retry, và tổng số dư A + B giữ nguyên.

### Outbox
Nghiệp vụ rollback → không có dòng `outbox`. Nghiệp vụ commit → đúng một dòng có `aggregateversion` đúng.

### Consumer idempotent
Giao cùng một event 2 lần (cùng `event_id`) → tác dụng chỉ xảy ra một lần, ví dụ hạn mức chỉ được release một lần. Giao event có version cũ sau event mới → bản sao dữ liệu không bị ghi lùi.

## Contract test cho provider

```java
abstract class BankGatewayContractTest {
    protected abstract BankGateway gateway();

    @Test void debit_sameExternalRefTwice_movesMoneyOnce() { ... }
    @Test void queryStatus_matchesDebitOutcome() { ... }
    @Test void technicalError_returnsUnknown_neverThrows() { ... }
}
class FakeBankGatewayContractTest extends BankGatewayContractTest { ... }
```
Fake và mọi adapter thật sau này đều phải extend class này.

## Contract test giữa các service
Dùng công cụ repo đã chọn (Pact hoặc Spring Cloud Contract). Phía consumer mô tả request/response nó cần; CI của provider chạy verify. Không viết test gọi service thật của team khác trong unit/integration test.
