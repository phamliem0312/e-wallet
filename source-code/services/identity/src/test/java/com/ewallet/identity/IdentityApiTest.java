package com.ewallet.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ewallet.identity.domain.OtpCodes;
import com.ewallet.identity.infrastructure.InMemoryMessageSender;
import com.ewallet.identity.infrastructure.InMemoryMessageSender.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test hợp đồng HTTP của identity trên Postgres, Kafka và Redis thật. ID theo
 * docs/test-cases/F-ACC-01-dang-ky-dang-nhap.md. Mỗi test dùng SĐT và IP riêng; đồng hồ điều khiển được.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ewallet.outbox.poll-interval=PT0.1S",
        "spring.kafka.consumer.auto-offset-reset=earliest",
})
@Testcontainers
class IdentityApiTest {

    static final String PASSWORD = "matkhau-dai-va-kho-doan";
    static final String TERMS = "2026-11";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-10T08:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired TestRestTemplate rest;
    @Autowired InMemoryMessageSender sender;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper json;

    String ip;

    @BeforeEach
    void isolate() {
        ip = "203.0.113." + (SEQUENCE.incrementAndGet() % 250 + 1); // IP riêng: không dính giới hạn tần suất của test khác
        sender.setFailing(false);
    }

    // ================= Đăng ký =================

    @Test
    @DisplayName("TC-ACC-020, 022, 025, 026: đăng ký tạo tài khoản, đồng ý, thiết bị, phiên và event; challenge không dùng lại được")
    void registrationCreatesEverythingOnce() throws Exception {
        String phone = newPhone();
        Instant start = clock.instant();

        ResponseEntity<JsonNode> started = post("/api/v1/auth/register", Map.of("phone", phone));
        assertThat(started.getStatusCode().value()).isEqualTo(201);
        JsonNode challenge = started.getBody();
        assertThat(challenge.get("factor").asText()).isEqualTo("OTP");
        assertThat(Instant.parse(challenge.get("expiresAt").asText())).isEqualTo(start.plus(Duration.ofMinutes(3)));
        assertThat(Instant.parse(challenge.get("resendAfter").asText())).isEqualTo(start.plusSeconds(30));
        assertThat(challenge.get("attemptsRemaining").asInt()).isEqualTo(5);
        assertThat(challenge.get("destinationMasked").asText()).isEqualTo("0900 *** " + phone.substring(7))
                .doesNotContain(phone.substring(4, 7) + phone.substring(7));
        assertThat(otpsTo(phone)).hasSize(1).first().asString().matches("[0-9]{6}");
        assertThat(count("select count(*) from identity.customer where phone = ?", e164(phone))).isZero();

        Map<String, Object> request = registration(challenge, otpsTo(phone).get(0), "dev-1");
        ResponseEntity<JsonNode> done = post("/api/v1/auth/register/verify", request);
        assertThat(done.getStatusCode().value()).isEqualTo(201);
        assertThat(fieldNames(done.getBody())).containsExactlyInAnyOrder("accessToken", "refreshToken",
                "accessTokenExpiresAt", "refreshTokenExpiresAt");

        UUID customerId = customerId(phone);
        assertThat(jdbc.sql("select status || ':' || kyc_tier || ':' || version || ':' || full_name "
                + "from identity.customer where id = ?").param(customerId).query(String.class).single())
                .isEqualTo("ACTIVE:0:1:Nguyễn Văn A");
        assertThat(jdbc.sql("select password_hash from identity.credential where customer_id = ?").param(customerId)
                .query(String.class).single()).startsWith("$argon2id$").doesNotContain(PASSWORD);
        assertThat(jdbc.sql("select purpose || ':' || terms_version || ':' || granted || ':' || host(ip) "
                + "from identity.consent where customer_id = ?").param(customerId).query(String.class).single())
                .isEqualTo("TERMS:" + TERMS + ":true:" + ip);
        assertThat(count("select count(*) from identity.device where customer_id = ? and trusted_at is not null "
                + "and octet_length(fingerprint_hash) = 32", customerId)).isEqualTo(1);

        // EV-01: đúng một CustomerRegistered trên Kafka, không có mật khẩu hay họ tên đầy đủ
        List<JsonNode> events = eventsFor("identity.events", customerId.toString(), 1);
        JsonNode event = events.get(0);
        assertThat(event.get("type").asText()).isEqualTo("CustomerRegistered");
        assertThat(event.get("source").asText()).isEqualTo("identity");
        assertThat(event.get("aggregateversion").asLong()).isEqualTo(1);
        assertThat(event.get("data").get("phone").asText()).isEqualTo(e164(phone));
        assertThat(event.get("data").get("displayMasked").asText()).isEqualTo("NGUYEN V*** A");
        assertThat(event.toString()).doesNotContain(PASSWORD).doesNotContain("Nguyễn Văn A");
        // EV-08: audit không chứa mật khẩu hay OTP
        String audit = jdbc.sql("select payload::text from outbox where topic = 'audit.records' and event_key = ?")
                .param(customerId.toString()).query(String.class).single();
        assertThat(audit).contains("CUSTOMER_REGISTERED").contains(ip).doesNotContain(PASSWORD)
                .doesNotContain(otpsTo(phone).get(0));

        // TC-025: challenge đã dùng
        ResponseEntity<JsonNode> again = post("/api/v1/auth/register/verify", request);
        assertProblem(again, 422, "CHALLENGE_INVALID");
        assertThat(count("select count(*) from identity.customer where phone = ?", e164(phone))).isEqualTo(1);

        // TC-026: thiết bị đăng ký là thiết bị đã biết
        assertThat(login(phone, PASSWORD, "dev-1").getStatusCode().value()).isEqualTo(200);
        assertThat(otpsTo(phone)).hasSize(1);
    }

    @Test
    @DisplayName("TC-ACC-021: SĐT đã đăng ký nhận phản hồi giống hệt, không có OTP, và challenge giả hành xử như thật")
    void registeredPhoneIsNotRevealed() {
        String fresh = newPhone();
        String taken = newPhone();
        register(taken, "dev-1");
        int otpsBefore = otpsTo(taken).size();
        // trong 30 giây đầu yêu cầu bị chặn gửi lại (429) y như SĐT chưa đăng ký; qua thời gian đó mới so phản hồi
        assertProblem(post("/api/v1/auth/register", Map.of("phone", taken)), 429, "RATE_LIMITED");
        clock.advance(Duration.ofSeconds(30));

        JsonNode real = post("/api/v1/auth/register", Map.of("phone", fresh)).getBody();
        ResponseEntity<JsonNode> decoy = post("/api/v1/auth/register", Map.of("phone", taken));

        assertThat(decoy.getStatusCode().value()).isEqualTo(201);
        assertThat(fieldNames(decoy.getBody())).isEqualTo(fieldNames(real));
        assertThat(otpsTo(taken)).hasSize(otpsBefore);
        assertThat(noticesTo(taken, "PHONE_ALREADY_REGISTERED")).hasSize(1);
        assertThat(noticesTo(taken, "PHONE_ALREADY_REGISTERED").get(0).code()).isNull();

        // Nhập mã bất kỳ: bị từ chối và đếm lượt y như challenge thật với mã sai
        ResponseEntity<JsonNode> guess = post("/api/v1/auth/register/verify", registration(decoy.getBody(), "000000", "dev-2"));
        assertProblem(guess, 422, "CHALLENGE_INVALID");
        assertThat(guess.getBody().get("params").get("attemptsRemaining").asInt()).isEqualTo(4);
        ResponseEntity<JsonNode> wrongOnReal = post("/api/v1/auth/register/verify", registration(real, wrongOtp(fresh), "dev-2"));
        assertThat(fieldNames(wrongOnReal.getBody())).isEqualTo(fieldNames(guess.getBody()));
        assertThat(count("select count(*) from identity.customer where phone = ?", e164(taken))).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-ACC-023, 024: thiếu đồng ý hoặc mật khẩu sai chính sách không tiêu hao lượt OTP")
    void formatAndConsentAreCheckedBeforeOtp() {
        String phone = newPhone();
        JsonNode challenge = post("/api/v1/auth/register", Map.of("phone", phone)).getBody();
        String otp = otpsTo(phone).get(0);

        Map<String, Object> noTerms = registration(challenge, otp, "dev-1");
        noTerms.remove("termsVersion");
        ResponseEntity<JsonNode> missing = post("/api/v1/auth/register/verify", noTerms);
        assertProblem(missing, 422, "CONSENT_REQUIRED");
        assertThat(missing.getBody().get("params").get("currentTermsVersion").asText()).isEqualTo(TERMS);

        Map<String, Object> oldTerms = registration(challenge, otp, "dev-1");
        oldTerms.put("termsVersion", "2026-01");
        assertProblem(post("/api/v1/auth/register/verify", oldTerms), 422, "CONSENT_REQUIRED");

        Map<String, Object> shortPassword = registration(challenge, otp, "dev-1");
        shortPassword.put("password", "1234567");
        ResponseEntity<JsonNode> policy = post("/api/v1/auth/register/verify", shortPassword);
        assertProblem(policy, 400, "VALIDATION_ERROR");
        assertThat(policy.getBody().get("errors").get(0).get("field").asText()).isEqualTo("password");

        Map<String, Object> phoneAsPassword = registration(challenge, otp, "dev-1");
        phoneAsPassword.put("password", phone);
        assertProblem(post("/api/v1/auth/register/verify", phoneAsPassword), 400, "VALIDATION_ERROR");

        Map<String, Object> unknownField = registration(challenge, otp, "dev-1");
        unknownField.put("isAdmin", true);
        assertProblem(post("/api/v1/auth/register/verify", unknownField), 400, "VALIDATION_ERROR");

        assertThat(count("select attempts from identity.otp_challenge where id = ?",
                UUID.fromString(challenge.get("challengeId").asText()))).isZero();
        assertThat(count("select count(*) from identity.customer where phone = ?", e164(phone))).isZero();
        // cùng OTP vẫn dùng được
        assertThat(post("/api/v1/auth/register/verify", registration(challenge, otp, "dev-1")).getStatusCode().value())
                .isEqualTo(201);
    }

    @Test
    @DisplayName("TC-ACC-010: hai yêu cầu hoàn tất đồng thời với cùng challenge chỉ tạo một tài khoản")
    void concurrentCompletionCreatesOneAccount() throws Exception {
        String phone = newPhone();
        JsonNode challenge = post("/api/v1/auth/register", Map.of("phone", phone)).getBody();
        Map<String, Object> request = registration(challenge, otpsTo(phone).get(0), "dev-1");

        List<Integer> statuses = inParallel(() -> post("/api/v1/auth/register/verify", request).getStatusCode().value(),
                () -> post("/api/v1/auth/register/verify", request).getStatusCode().value());

        assertThat(statuses).containsOnlyOnce(201);
        assertThat(statuses).allMatch(status -> status == 201 || status == 409 || status == 422);
        assertThat(count("select count(*) from identity.customer where phone = ?", e164(phone))).isEqualTo(1);
        assertThat(count("select count(*) from outbox where event_type = 'CustomerRegistered' and event_key = ?",
                customerId(phone).toString())).isEqualTo(1);
    }

    // ================= OTP và challenge =================

    @Test
    @DisplayName("TC-ACC-030: OTP hết hạn đúng tại thời điểm hết hạn")
    void otpExpiresExactlyAtExpiry() {
        String early = newPhone();
        JsonNode first = post("/api/v1/auth/register", Map.of("phone", early)).getBody();
        clock.advance(Duration.ofSeconds(179));
        assertThat(post("/api/v1/auth/register/verify", registration(first, otpsTo(early).get(0), "dev-1"))
                .getStatusCode().value()).isEqualTo(201);

        String late = newPhone();
        JsonNode second = post("/api/v1/auth/register", Map.of("phone", late)).getBody();
        clock.advance(Duration.ofSeconds(180));
        assertProblem(post("/api/v1/auth/register/verify", registration(second, otpsTo(late).get(0), "dev-1")),
                422, "CHALLENGE_INVALID");
        assertThat(count("select count(*) from identity.customer where phone = ?", e164(late))).isZero();
    }

    @Test
    @DisplayName("TC-ACC-032: challenge không dùng được cho mục đích khác")
    void challengeIsBoundToItsPurpose() {
        String phone = newPhone();
        JsonNode registerChallenge = post("/api/v1/auth/register", Map.of("phone", phone)).getBody();
        String otp = otpsTo(phone).get(0);
        String id = registerChallenge.get("challengeId").asText();

        assertProblem(post("/api/v1/auth/login/verify",
                Map.of("challengeId", id, "otp", otp, "deviceFingerprint", "dev-1")), 422, "CHALLENGE_INVALID");
        assertProblem(post("/api/v1/auth/password/reset",
                Map.of("challengeId", id, "otp", otp, "newPassword", PASSWORD)), 422, "CHALLENGE_INVALID");
        // dùng sai chỗ không làm hỏng challenge
        assertThat(post("/api/v1/auth/register/verify", registration(registerChallenge, otp, "dev-1"))
                .getStatusCode().value()).isEqualTo(201);

        JsonNode resetChallenge = post("/api/v1/auth/password/forgot", Map.of("phone", phone)).getBody();
        assertProblem(post("/api/v1/auth/login/verify", Map.of("challengeId", resetChallenge.get("challengeId").asText(),
                "otp", otpsTo(phone).get(0), "deviceFingerprint", "dev-1")), 422, "CHALLENGE_INVALID");
    }

    @Test
    @DisplayName("TC-ACC-033: nhập sai 5 lần thì mã đúng cũng bị từ chối")
    void attemptsAreExhaustedAfterFiveWrongCodes() {
        String phone = newPhone();
        JsonNode challenge = post("/api/v1/auth/register", Map.of("phone", phone)).getBody();
        String wrong = wrongOtp(phone);

        for (int remaining = 4; remaining >= 0; remaining--) {
            ResponseEntity<JsonNode> response = post("/api/v1/auth/register/verify", registration(challenge, wrong, "dev-1"));
            assertProblem(response, 422, "CHALLENGE_INVALID");
            assertThat(response.getBody().get("params").get("attemptsRemaining").asInt()).isEqualTo(remaining);
        }
        ResponseEntity<JsonNode> correct = post("/api/v1/auth/register/verify",
                registration(challenge, otpsTo(phone).get(0), "dev-1"));
        assertProblem(correct, 422, "CHALLENGE_INVALID");
        assertThat(count("select count(*) from identity.customer where phone = ?", e164(phone))).isZero();
    }

    @Test
    @DisplayName("TC-ACC-034: gửi lại OTP bị chặn trước 30 giây, sau đó challenge mới thay challenge cũ")
    void resendIsThrottledThenSupersedesOldChallenge() {
        String phone = newPhone();
        JsonNode first = post("/api/v1/auth/register", Map.of("phone", phone)).getBody();
        String firstOtp = otpsTo(phone).get(0);

        clock.advance(Duration.ofSeconds(29));
        ResponseEntity<JsonNode> tooSoon = post("/api/v1/auth/register", Map.of("phone", phone));
        assertProblem(tooSoon, 429, "RATE_LIMITED");
        assertThat(tooSoon.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(otpsTo(phone)).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        ResponseEntity<JsonNode> second = post("/api/v1/auth/register", Map.of("phone", phone));
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getBody().get("challengeId")).isNotEqualTo(first.get("challengeId"));
        String secondOtp = otpsTo(phone).get(0);

        assertProblem(post("/api/v1/auth/register/verify", registration(first, firstOtp, "dev-1")), 422, "CHALLENGE_INVALID");
        assertThat(post("/api/v1/auth/register/verify", registration(second.getBody(), secondOtp, "dev-1"))
                .getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("TC-ACC-035: lần gửi OTP thứ 6 trong một giờ bị chặn")
    void sendsAreCappedPerPhone() {
        String phone = newPhone();
        for (int i = 1; i <= 5; i++) {
            assertThat(post("/api/v1/auth/register", Map.of("phone", phone)).getStatusCode().value()).isEqualTo(201);
            clock.advance(Duration.ofSeconds(30));
        }
        assertProblem(post("/api/v1/auth/register", Map.of("phone", phone)), 429, "RATE_LIMITED");
        assertThat(otpsTo(phone)).hasSize(5);
    }

    @Test
    @DisplayName("TC-ACC-036: không giao được OTP thì OTP_SEND_FAILED, không tính vào trần và không khoá gửi lại")
    void failedHandoverIsReportedAndNotCounted() {
        String phone = newPhone();
        sender.setFailing(true);
        ResponseEntity<JsonNode> failed = post("/api/v1/auth/register", Map.of("phone", phone));
        assertProblem(failed, 503, "OTP_SEND_FAILED");
        assertThat(count("select count(*) from identity.otp_challenge where phone = ? and superseded_at is null",
                e164(phone))).isZero();

        sender.setFailing(false);
        assertThat(post("/api/v1/auth/register", Map.of("phone", phone)).getStatusCode().value()).isEqualTo(201);
        assertThat(otpsTo(phone)).hasSize(1);
    }

    // ================= Đăng nhập =================

    @Test
    @DisplayName("TC-ACC-040, 041: đăng nhập thiết bị đã biết; sai SĐT và sai mật khẩu không phân biệt được")
    void loginFromKnownDeviceAndIndistinguishableFailures() {
        String phone = newPhone();
        register(phone, "dev-1");
        clock.advance(Duration.ofMinutes(10));

        ResponseEntity<JsonNode> ok = login(phone, PASSWORD, "dev-1");
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(otpsTo(phone)).hasSize(1); // chỉ có OTP lúc đăng ký
        assertThat(jdbc.sql("select last_seen_at from identity.device where customer_id = ?").param(customerId(phone))
                .query(OffsetDateTime.class).single().toInstant()).isEqualTo(clock.instant());

        ResponseEntity<JsonNode> wrongPassword = login(phone, "sai-mat-khau-roi", "dev-1");
        ResponseEntity<JsonNode> unknownPhone = login(newPhone(), PASSWORD, "dev-1");
        assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS");
        assertProblem(unknownPhone, 401, "INVALID_CREDENTIALS");
        assertThat(withoutTraceId(wrongPassword.getBody())).isEqualTo(withoutTraceId(unknownPhone.getBody()));
    }

    @Test
    @DisplayName("TC-ACC-042, 043: khoá tạm sau 5 lần sai, không cộng khi đang khoá, hết 15 phút thì vào được; đúng thì bộ đếm về 0")
    void temporaryLockout() {
        String phone = newPhone();
        register(phone, "dev-1");
        UUID id = customerId(phone);

        for (int i = 0; i < 4; i++) assertProblem(login(phone, "sai-mat-khau-roi", "dev-1"), 401, "INVALID_CREDENTIALS");
        assertThat(login(phone, PASSWORD, "dev-1").getStatusCode().value()).isEqualTo(200);
        assertThat(count("select failed_count from identity.credential where customer_id = ?", id)).isZero();

        for (int i = 0; i < 5; i++) assertProblem(login(phone, "sai-mat-khau-roi", "dev-1"), 401, "INVALID_CREDENTIALS");
        ResponseEntity<JsonNode> locked = login(phone, PASSWORD, "dev-1");
        assertProblem(locked, 403, "ACCOUNT_TEMP_LOCKED");
        assertThat(locked.getBody().get("params").get("retryAfterSeconds").asLong()).isEqualTo(900);
        assertThat(locked.getHeaders().getFirst("Retry-After")).isEqualTo("900");
        assertProblem(login(phone, "sai-mat-khau-roi", "dev-1"), 403, "ACCOUNT_TEMP_LOCKED");
        assertThat(count("select failed_count from identity.credential where customer_id = ?", id)).isEqualTo(5);

        clock.advance(Duration.ofMinutes(15));
        assertThat(login(phone, PASSWORD, "dev-1").getStatusCode().value()).isEqualTo(200);
        assertThat(count("select failed_count from identity.credential where customer_id = ?", id)).isZero();
    }

    @Test
    @DisplayName("TC-ACC-044, 048, 049, 055: tài khoản LOCKED/CLOSED, thứ tự ưu tiên lỗi, thu hồi phiên, mở khoá")
    void accountStatusAndErrorPriority() {
        String phone = newPhone();
        JsonNode session = register(phone, "dev-1");
        setStatus(phone, "LOCKED");

        // BR-05: mật khẩu sai → INVALID_CREDENTIALS (không lộ trạng thái); đúng → ACCOUNT_LOCKED, không OTP dù thiết bị mới
        assertProblem(login(phone, "sai-mat-khau-roi", "dev-1"), 401, "INVALID_CREDENTIALS");
        assertProblem(login(phone, PASSWORD, "dev-moi"), 403, "ACCOUNT_LOCKED");
        assertThat(otpsTo(phone)).hasSize(1);
        assertProblem(login("090000001", "x", "dev-1"), 400, "VALIDATION_ERROR");
        // FR-31: tài khoản bị khoá thì không làm mới được phiên
        assertProblem(refresh(session.get("refreshToken").asText()), 401, "UNAUTHENTICATED");

        setStatus(phone, "ACTIVE");
        assertThat(login(phone, PASSWORD, "dev-1").getStatusCode().value()).isEqualTo(200);
        assertProblem(refresh(session.get("refreshToken").asText()), 401, "UNAUTHENTICATED");

        setStatus(phone, "CLOSED");
        ResponseEntity<JsonNode> closed = login(phone, PASSWORD, "dev-1");
        ResponseEntity<JsonNode> unknown = login(newPhone(), PASSWORD, "dev-1");
        assertProblem(closed, 401, "INVALID_CREDENTIALS");
        assertThat(withoutTraceId(closed.getBody())).isEqualTo(withoutTraceId(unknown.getBody()));
    }

    @Test
    @DisplayName("TC-ACC-045, 046, 047, 031: thiết bị mới phải qua OTP; sai mật khẩu không gửi OTP; OTP không dùng lại được")
    void newDeviceRequiresOtp() {
        String phone = newPhone();
        register(phone, "dev-1");

        assertProblem(login(phone, "sai-mat-khau-roi", "dev-2"), 401, "INVALID_CREDENTIALS");
        assertThat(otpsTo(phone)).hasSize(1);

        ResponseEntity<JsonNode> challenged = login(phone, PASSWORD, "dev-2");
        assertProblem(challenged, 422, "CHALLENGE_REQUIRED");
        JsonNode params = challenged.getBody().get("params");
        assertThat(fieldNames(params)).containsExactlyInAnyOrder("challengeId", "factor", "expiresAt",
                "destinationMasked", "resendAfter", "attemptsRemaining");
        assertThat(challenged.getBody().has("accessToken")).isFalse();
        assertThat(otpsTo(phone)).hasSize(2);

        Map<String, Object> verify = Map.of("challengeId", params.get("challengeId").asText(),
                "otp", otpsTo(phone).get(0), "deviceFingerprint", "dev-2");
        ResponseEntity<JsonNode> verified = post("/api/v1/auth/login/verify", verify);
        assertThat(verified.getStatusCode().value()).isEqualTo(200);
        assertThat(verified.getBody().has("accessToken")).isTrue();
        List<Message> notices = noticesTo(phone, "NEW_DEVICE_LOGIN");
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).toString()).doesNotContain(PASSWORD).doesNotContain(otpsTo(phone).get(0));

        assertProblem(post("/api/v1/auth/login/verify", verify), 422, "CHALLENGE_INVALID");
        assertThat(login(phone, PASSWORD, "dev-2").getStatusCode().value()).isEqualTo(200);
        assertThat(otpsTo(phone)).hasSize(2);
    }

    @Test
    @DisplayName("TC-ACC-080: yêu cầu đăng nhập thứ 21 từ một IP trong một phút bị chặn")
    void loginIsRateLimitedPerIp() {
        String phone = newPhone();
        for (int i = 1; i <= 20; i++) assertProblem(login(phone, "x", "dev-1"), 401, "INVALID_CREDENTIALS");
        ResponseEntity<JsonNode> limited = login(phone, "x", "dev-1");
        assertProblem(limited, 429, "RATE_LIMITED");
        assertThat(Long.parseLong(limited.getHeaders().getFirst("Retry-After"))).isBetween(1L, 60L);
    }

    // ================= Phiên =================

    @Test
    @DisplayName("TC-ACC-050: access token ký ES256, kiểm được bằng JWKS, hết hạn đúng sau 5 phút")
    void accessTokenIsEs256AndExpiresAfterFiveMinutes() throws Exception {
        String phone = newPhone();
        JsonNode session = register(phone, "dev-1");
        String accessToken = session.get("accessToken").asText();

        SignedJWT jwt = SignedJWT.parse(accessToken);
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        JsonNode jwks = rest.getForObject("/.well-known/jwks.json", JsonNode.class);
        ECKey key = (ECKey) JWKSet.parse(jwks.toString()).getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(key.isPrivate()).isFalse();
        assertThat(jwt.verify(new ECDSAVerifier(key))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(customerId(phone).toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("sid")).isNotBlank();
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().getTime()
                - jwt.getJWTClaimsSet().getIssueTime().getTime()).isEqualTo(300_000);
        assertThat(Instant.parse(session.get("accessTokenExpiresAt").asText()))
                .isEqualTo(jwt.getJWTClaimsSet().getExpirationTime().toInstant());

        clock.advance(Duration.ofSeconds(299));
        assertThat(get("/api/v1/me/devices", accessToken).getStatusCode().value()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(1));
        assertProblem(get("/api/v1/me/devices", accessToken), 401, "TOKEN_EXPIRED");
        assertProblem(get("/api/v1/me/devices", null), 401, "UNAUTHENTICATED");
        assertProblem(get("/api/v1/me/devices", accessToken.substring(0, accessToken.length() - 4) + "AAAA"),
                401, "UNAUTHENTICATED");
    }

    @Test
    @DisplayName("TC-ACC-051, 052: làm mới xoay vòng token; dùng lại token cũ thu hồi cả family")
    void refreshRotatesAndReuseRevokesFamily() {
        String phone = newPhone();
        String first = register(phone, "dev-1").get("refreshToken").asText();
        clock.advance(Duration.ofMinutes(5));

        ResponseEntity<JsonNode> rotated = refresh(first);
        assertThat(rotated.getStatusCode().value()).isEqualTo(200);
        String second = rotated.getBody().get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);
        assertThat(get("/api/v1/me/devices", rotated.getBody().get("accessToken").asText()).getStatusCode().value())
                .isEqualTo(200);

        assertProblem(refresh(first), 401, "UNAUTHENTICATED");
        assertProblem(refresh(second), 401, "UNAUTHENTICATED");
        assertThat(count("select count(*) from outbox where topic = 'audit.records' and event_key = ? "
                + "and payload->>'action' = 'REFRESH_TOKEN_REUSED'", customerId(phone).toString())).isEqualTo(1);
        assertProblem(refresh("rt_khong-ton-tai"), 401, "UNAUTHENTICATED");
    }

    @Test
    @DisplayName("TC-ACC-012: hai lần làm mới đồng thời bằng cùng token: đúng một lần thắng")
    void concurrentRefreshHasOneWinner() throws Exception {
        String token = register(newPhone(), "dev-1").get("refreshToken").asText();
        List<Integer> statuses = inParallel(() -> refresh(token).getStatusCode().value(),
                () -> refresh(token).getStatusCode().value());
        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
    }

    @Test
    @DisplayName("TC-ACC-053: hết hạn do không hoạt động (30 phút) và hạn tuyệt đối (30 ngày)")
    void idleAndAbsoluteExpiry() {
        String token = register(newPhone(), "dev-1").get("refreshToken").asText();
        clock.advance(Duration.ofMinutes(30).minusSeconds(1));
        ResponseEntity<JsonNode> justInTime = refresh(token);
        assertThat(justInTime.getStatusCode().value()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(30));
        assertProblem(refresh(justInTime.getBody().get("refreshToken").asText()), 401, "UNAUTHENTICATED");

        // Hoạt động liên tục: vẫn hết hạn sau 30 ngày kể từ lúc đăng nhập
        Instant loginAt = clock.instant();
        String active = register(newPhone(), "dev-1").get("refreshToken").asText();
        Instant absolute = loginAt.plus(Duration.ofDays(30));
        while (clock.instant().plus(Duration.ofMinutes(20)).isBefore(absolute)) {
            clock.advance(Duration.ofMinutes(20));
            JsonNode next = refresh(active).getBody();
            active = next.get("refreshToken").asText();
            assertThat(Instant.parse(next.get("refreshTokenExpiresAt").asText())).isBeforeOrEqualTo(absolute);
        }
        clock.advance(Duration.between(clock.instant(), absolute).plusSeconds(1));
        assertProblem(refresh(active), 401, "UNAUTHENTICATED");
    }

    @Test
    @DisplayName("TC-ACC-054: đăng xuất chỉ thu hồi phiên hiện tại")
    void logoutRevokesOnlyCurrentSession() {
        String phone = newPhone();
        JsonNode first = register(phone, "dev-1");
        JsonNode second = loginNewDevice(phone, "dev-2");

        assertThat(post("/api/v1/auth/logout", null, first.get("accessToken").asText()).getStatusCode().value())
                .isEqualTo(204);
        assertProblem(refresh(first.get("refreshToken").asText()), 401, "UNAUTHENTICATED");
        assertThat(refresh(second.get("refreshToken").asText()).getStatusCode().value()).isEqualTo(200);
        assertProblem(post("/api/v1/auth/logout", null, null), 401, "UNAUTHENTICATED");
    }

    // ================= Mật khẩu =================

    @Test
    @DisplayName("TC-ACC-060, 061, 062: quên mật khẩu không lộ SĐT; đặt lại thu hồi mọi phiên và gỡ khoá tạm")
    void passwordReset() {
        String phone = newPhone();
        JsonNode first = register(phone, "dev-1");
        JsonNode second = loginNewDevice(phone, "dev-2");
        for (int i = 0; i < 3; i++) login(phone, "sai-mat-khau-roi", "dev-1");
        int otpsBefore = otpsTo(phone).size();

        String unknown = newPhone();
        ResponseEntity<JsonNode> forUnknown = post("/api/v1/auth/password/forgot", Map.of("phone", unknown));
        ResponseEntity<JsonNode> forKnown = post("/api/v1/auth/password/forgot", Map.of("phone", phone));
        assertThat(forUnknown.getStatusCode().value()).isEqualTo(201);
        assertThat(forKnown.getStatusCode().value()).isEqualTo(201);
        assertThat(fieldNames(forUnknown.getBody())).isEqualTo(fieldNames(forKnown.getBody()));
        assertThat(otpsTo(unknown)).isEmpty();
        assertThat(otpsTo(phone)).hasSize(otpsBefore + 1);
        String id = forKnown.getBody().get("challengeId").asText();
        String otp = otpsTo(phone).get(0);

        // TC-062: mật khẩu sai chính sách không tiêu hao lượt
        assertProblem(post("/api/v1/auth/password/reset", Map.of("challengeId", id, "otp", otp, "newPassword", "1234567")),
                400, "VALIDATION_ERROR");
        String newPassword = "mat-khau-moi-dai-hon";
        assertThat(post("/api/v1/auth/password/reset", Map.of("challengeId", id, "otp", otp, "newPassword", newPassword))
                .getStatusCode().value()).isEqualTo(204);

        assertProblem(login(phone, PASSWORD, "dev-1"), 401, "INVALID_CREDENTIALS");
        assertProblem(refresh(first.get("refreshToken").asText()), 401, "UNAUTHENTICATED");
        assertProblem(refresh(second.get("refreshToken").asText()), 401, "UNAUTHENTICATED");
        assertThat(noticesTo(phone, "PASSWORD_CHANGED")).hasSize(1);
        assertThat(login(phone, newPassword, "dev-1").getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.sql("select failed_count || ':' || coalesce(locked_until::text, 'null') "
                + "from identity.credential where customer_id = ?").param(customerId(phone)).query(String.class).single())
                .isEqualTo("0:null");

        // tài khoản LOCKED không nhận OTP nhưng phản hồi giống hệt
        setStatus(phone, "LOCKED");
        clock.advance(Duration.ofSeconds(30));
        int before = otpsTo(phone).size();
        ResponseEntity<JsonNode> forLocked = post("/api/v1/auth/password/forgot", Map.of("phone", phone));
        assertThat(forLocked.getStatusCode().value()).isEqualTo(201);
        assertThat(otpsTo(phone)).hasSize(before);
    }

    @Test
    @DisplayName("TC-ACC-063: đổi mật khẩu cần mật khẩu hiện tại, khác mật khẩu cũ, thu hồi phiên khác")
    void changePassword() {
        String phone = newPhone();
        JsonNode current = register(phone, "dev-1");
        JsonNode other = loginNewDevice(phone, "dev-2");
        String token = current.get("accessToken").asText();
        String newPassword = "mat-khau-moi-dai-hon";

        assertProblem(post("/api/v1/me/password", Map.of("currentPassword", "sai-mat-khau-roi", "newPassword", newPassword), token),
                401, "INVALID_CREDENTIALS");
        assertThat(count("select failed_count from identity.credential where customer_id = ?", customerId(phone)))
                .isEqualTo(1);
        assertProblem(post("/api/v1/me/password", Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD), token),
                400, "VALIDATION_ERROR");
        assertProblem(post("/api/v1/me/password", Map.of("currentPassword", PASSWORD, "newPassword", newPassword), null),
                401, "UNAUTHENTICATED");

        assertThat(post("/api/v1/me/password", Map.of("currentPassword", PASSWORD, "newPassword", newPassword), token)
                .getStatusCode().value()).isEqualTo(204);
        assertProblem(refresh(other.get("refreshToken").asText()), 401, "UNAUTHENTICATED");
        assertThat(refresh(current.get("refreshToken").asText()).getStatusCode().value()).isEqualTo(200);
        assertThat(noticesTo(phone, "PASSWORD_CHANGED")).hasSize(1);
        assertThat(login(phone, newPassword, "dev-1").getStatusCode().value()).isEqualTo(200);
    }

    // ================= Số điện thoại =================

    @Test
    @DisplayName("TC-ACC-065..068: đổi SĐT cần eKYC, khuôn mặt đạt rồi OTP tới SĐT mới; SĐT trùng bị từ chối")
    void changePhone() throws Exception {
        String phone = newPhone();
        String newPhone = newPhone();
        String taken = newPhone();
        register(taken, "dev-x");
        JsonNode current = register(phone, "dev-1");
        JsonNode other = loginNewDevice(phone, "dev-2");
        String token = current.get("accessToken").asText();
        UUID customerId = customerId(phone);
        Map<String, Object> otpChallengeRequest = Map.of("purpose", "CHANGE_PHONE", "factor", "OTP",
                "payload", Map.of("newPhone", newPhone));
        Map<String, Object> faceChallengeRequest = Map.of("purpose", "CHANGE_PHONE", "factor", "FACE",
                "payload", Map.of("newPhone", newPhone));

        // TC-068: chưa eKYC
        assertProblem(post("/api/v1/challenges", faceChallengeRequest, token), 403, "KYC_REQUIRED");
        assertProblem(post("/api/v1/challenges", otpChallengeRequest, token), 403, "KYC_REQUIRED");
        assertProblem(post("/api/v1/me/phone", Map.of("newPhone", newPhone, "faceChallengeId", UUID.randomUUID().toString(),
                "otpChallengeId", UUID.randomUUID().toString(), "otp", "123456"), token), 403, "KYC_REQUIRED");

        jdbc.sql("update identity.customer set kyc_tier = 1 where id = ?").param(customerId).update();

        // TC-067: chưa qua khuôn mặt thì không gửi OTP tới SĐT mới
        ResponseEntity<JsonNode> face = post("/api/v1/challenges", faceChallengeRequest, token);
        assertThat(face.getStatusCode().value()).isEqualTo(201);
        assertThat(face.getBody().get("factor").asText()).isEqualTo("FACE");
        assertThat(face.getBody().has("destinationMasked")).isFalse();
        String faceId = face.getBody().get("challengeId").asText();
        assertProblem(post("/api/v1/challenges", otpChallengeRequest, token), 422, "CHALLENGE_REQUIRED");
        assertThat(otpsTo(newPhone)).isEmpty();
        assertProblem(post("/api/v1/me/phone", Map.of("newPhone", newPhone, "faceChallengeId", faceId,
                "otpChallengeId", UUID.randomUUID().toString(), "otp", "123456"), token), 422, "CHALLENGE_INVALID");

        // Xác thực khuôn mặt thuộc F-ACC-02 (OQ-028): dựng trạng thái "đã đạt" bằng cách ghi DB
        jdbc.sql("update identity.otp_challenge set consumed_at = ? where id = ?")
                .params(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), UUID.fromString(faceId)).update();

        ResponseEntity<JsonNode> otpChallenge = post("/api/v1/challenges", otpChallengeRequest, token);
        assertThat(otpChallenge.getStatusCode().value()).isEqualTo(201);
        assertThat(otpsTo(newPhone)).hasSize(1);
        assertThat(otpsTo(phone)).hasSize(2); // đăng ký + thiết bị mới; không có OTP đổi SĐT tới SĐT cũ
        String otpId = otpChallenge.getBody().get("challengeId").asText();

        // TC-066: khuôn mặt cấp cho SĐT khác không dùng được; SĐT đã thuộc tài khoản khác
        assertProblem(post("/api/v1/me/phone", Map.of("newPhone", taken, "faceChallengeId", faceId,
                "otpChallengeId", otpId, "otp", otpsTo(newPhone).get(0)), token), 422, "CHALLENGE_INVALID");
        UUID faceForTaken = UUID.randomUUID();
        jdbc.sql("insert into identity.otp_challenge (id, customer_id, purpose, factor, payload_hash, created_at, "
                        + "expires_at, consumed_at) values (?, ?, 'CHANGE_PHONE', 'FACE', ?, ?, ?, ?)")
                .params(faceForTaken, customerId, OtpCodes.sha256("CHANGE_PHONE|" + e164(taken)), ts(clock.instant()),
                        ts(clock.instant().plusSeconds(180)), ts(clock.instant())).update();
        assertProblem(post("/api/v1/me/phone", Map.of("newPhone", taken, "faceChallengeId", faceForTaken.toString(),
                "otpChallengeId", otpId, "otp", "123456"), token), 409, "PHONE_ALREADY_REGISTERED");
        assertThat(jdbc.sql("select phone from identity.customer where id = ?").param(customerId).query(String.class)
                .single()).isEqualTo(e164(phone));

        // TC-065: thành công
        assertThat(post("/api/v1/me/phone", Map.of("newPhone", newPhone, "faceChallengeId", faceId,
                "otpChallengeId", otpId, "otp", otpsTo(newPhone).get(0)), token).getStatusCode().value()).isEqualTo(204);
        assertProblem(login(phone, PASSWORD, "dev-1"), 401, "INVALID_CREDENTIALS");
        assertThat(login(newPhone, PASSWORD, "dev-1").getStatusCode().value()).isEqualTo(200);
        assertProblem(refresh(other.get("refreshToken").asText()), 401, "UNAUTHENTICATED");
        assertThat(refresh(current.get("refreshToken").asText()).getStatusCode().value()).isEqualTo(200);
        assertThat(noticesTo(phone, "PHONE_CHANGED")).hasSize(1);
        assertThat(noticesTo(newPhone, "PHONE_CHANGED")).hasSize(1);
        JsonNode updated = eventsFor("identity.events", customerId.toString(), 2).get(1);
        assertThat(updated.get("type").asText()).isEqualTo("CustomerUpdated");
        assertThat(updated.get("aggregateversion").asLong()).isEqualTo(2);
        assertThat(updated.get("data").get("phone").asText()).isEqualTo(e164(newPhone));
    }

    // ================= Thiết bị, đồng ý =================

    @Test
    @DisplayName("TC-ACC-070, 071, 072: danh sách thiết bị, thu hồi thiết bị, đồng ý hiện tại")
    void devicesAndConsents() {
        String phone = newPhone();
        JsonNode current = register(phone, "dev-1");
        JsonNode other = loginNewDevice(phone, "dev-2");
        String token = current.get("accessToken").asText();
        String strangerToken = register(newPhone(), "dev-z").get("accessToken").asText();

        JsonNode devices = get("/api/v1/me/devices", token).getBody().get("items");
        assertThat(devices).hasSize(2);
        List<JsonNode> currentDevices = new ArrayList<>();
        devices.forEach(d -> { if (d.get("isCurrent").asBoolean()) currentDevices.add(d); });
        assertThat(currentDevices).hasSize(1);
        devices.forEach(d -> assertThat(fieldNames(d)).isSubsetOf("deviceId", "label", "lastSeenAt", "isCurrent"));
        assertThat(devices.toString()).doesNotContain("dev-1").doesNotContain("dev-2");
        String otherDeviceId = null;
        for (JsonNode d : devices) if (!d.get("isCurrent").asBoolean()) otherDeviceId = d.get("deviceId").asText();

        // thiết bị của người khác và id không tồn tại: 404, không đổi gì
        assertProblem(delete("/api/v1/me/devices/" + otherDeviceId, strangerToken), 404, "NOT_FOUND");
        assertProblem(delete("/api/v1/me/devices/" + UUID.randomUUID(), token), 404, "NOT_FOUND");
        assertThat(refresh(other.get("refreshToken").asText()).getStatusCode().value()).isEqualTo(200);

        assertThat(delete("/api/v1/me/devices/" + otherDeviceId, token).getStatusCode().value()).isEqualTo(204);
        assertThat(delete("/api/v1/me/devices/" + otherDeviceId, token).getStatusCode().value()).isEqualTo(204);
        assertThat(get("/api/v1/me/devices", token).getBody().get("items")).hasSize(1);
        clock.advance(Duration.ofSeconds(31));
        assertProblem(login(phone, PASSWORD, "dev-2"), 422, "CHALLENGE_REQUIRED");

        jdbc.sql("insert into identity.consent (id, customer_id, purpose, terms_version, granted, ip, user_agent, "
                        + "created_at) values (?, ?, 'TERMS', '2026-01', true, '10.0.0.1', 'ua', ?)")
                .params(UUID.randomUUID(), customerId(phone), ts(clock.instant().minus(Duration.ofDays(300)))).update();
        JsonNode consents = get("/api/v1/me/consents", token).getBody().get("items");
        assertThat(consents).hasSize(1);
        assertThat(consents.get(0).get("purpose").asText()).isEqualTo("TERMS");
        assertThat(consents.get(0).get("termsVersion").asText()).isEqualTo(TERMS);
        assertThat(consents.get(0).get("granted").asBoolean()).isTrue();
    }

    // ================= Challenge giao dịch =================

    @Test
    @DisplayName("TC-ACC-075, 076: challenge P2P gửi OTP tới SĐT đăng ký; xác minh gắn với một consumerRef")
    void transactionChallenge() {
        String phone = newPhone();
        String recipient = newPhone();
        String token = register(phone, "dev-1").get("accessToken").asText();
        Map<String, Object> payload = Map.of("recipientPhone", recipient, "amount", 3_000_000);

        ResponseEntity<JsonNode> created = post("/api/v1/challenges", Map.of("purpose", "P2P", "payload", payload), token);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(otpsTo(phone)).hasSize(2);
        assertThat(otpsTo(recipient)).isEmpty();
        String id = created.getBody().get("challengeId").asText();
        String otp = otpsTo(phone).get(0);

        assertProblem(post("/api/v1/challenges", Map.of("purpose", "P2P", "payload", payload), token), 429, "RATE_LIMITED");
        ResponseEntity<JsonNode> otherPayload = post("/api/v1/challenges",
                Map.of("purpose", "P2P", "payload", Map.of("recipientPhone", recipient, "amount", 4_000_000)), token);
        assertThat(otherPayload.getStatusCode().value()).isEqualTo(201);
        assertProblem(post("/api/v1/challenges", Map.of("purpose", "P2P", "payload", Map.of("amount", 1)), token),
                400, "VALIDATION_ERROR");
        assertProblem(post("/api/v1/challenges", Map.of("purpose", "P2P", "payload", payload), null), 401, "UNAUTHENTICATED");

        String verify = "/internal/challenges/" + id + "/verify";
        // (d) nội dung khác
        assertProblem(post(verify, Map.of("otp", otp, "consumerRef", "K1",
                "payload", Map.of("recipientPhone", recipient, "amount", 3_000_001))), 422, "CHALLENGE_INVALID");
        // (f) lần đầu không kèm OTP
        assertProblem(post(verify, Map.of("consumerRef", "K1", "payload", payload)), 422, "CHALLENGE_INVALID");
        // (a) đúng
        ResponseEntity<JsonNode> ok = post(verify, Map.of("otp", otp, "consumerRef", "K1", "payload", payload));
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getBody().get("factor").asText()).isEqualTo("OTP");
        // (b) cùng consumerRef, không cần OTP, kể cả khi challenge đã quá hạn
        clock.advance(Duration.ofMinutes(10));
        assertThat(post(verify, Map.of("consumerRef", "K1", "payload", payload)).getStatusCode().value()).isEqualTo(200);
        // (c) consumerRef khác
        assertProblem(post(verify, Map.of("otp", otp, "consumerRef", "K2", "payload", payload)), 422, "CHALLENGE_INVALID");

        // (e) OTP sai trả số lần còn lại; (g) challenge không phải giao dịch
        String second = otherPayload.getBody().get("challengeId").asText();
        clock.advance(Duration.ofMinutes(-10));
        ResponseEntity<JsonNode> wrong = post("/internal/challenges/" + second + "/verify", Map.of("otp", wrongOtp(phone),
                "consumerRef", "K3", "payload", Map.of("recipientPhone", recipient, "amount", 4_000_000)));
        assertProblem(wrong, 422, "CHALLENGE_INVALID");
        assertThat(wrong.getBody().get("params").get("attemptsRemaining").asInt()).isEqualTo(4);
        String loginChallenge = login(phone, PASSWORD, "dev-9").getBody().get("params").get("challengeId").asText();
        assertProblem(post("/internal/challenges/" + loginChallenge + "/verify",
                Map.of("otp", otpsTo(phone).get(0), "consumerRef", "K4", "payload", payload)), 422, "CHALLENGE_INVALID");
    }

    // ================= helpers =================

    String newPhone() {
        return String.format("09%08d", SEQUENCE.incrementAndGet());
    }

    static String e164(String phone) {
        return "+84" + phone.substring(1);
    }

    static OffsetDateTime ts(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Đăng ký hoàn chỉnh; trả phiên. */
    JsonNode register(String phone, String device) {
        ResponseEntity<JsonNode> started = post("/api/v1/auth/register", Map.of("phone", phone));
        assertThat(started.getStatusCode().value()).as("register %s", started.getBody()).isEqualTo(201);
        ResponseEntity<JsonNode> done = post("/api/v1/auth/register/verify",
                registration(started.getBody(), otpsTo(phone).get(0), device));
        assertThat(done.getStatusCode().value()).as("register verify %s", done.getBody()).isEqualTo(201);
        return done.getBody();
    }

    Map<String, Object> registration(JsonNode challenge, String otp, String device) {
        Map<String, Object> body = new HashMap<>();
        body.put("challengeId", challenge.get("challengeId").asText());
        body.put("otp", otp);
        body.put("password", PASSWORD);
        body.put("fullName", "  Nguyễn   Văn A ");
        body.put("termsVersion", TERMS);
        body.put("deviceFingerprint", device);
        return body;
    }

    ResponseEntity<JsonNode> login(String phone, String password, String device) {
        return post("/api/v1/auth/login", Map.of("phone", phone, "password", password, "deviceFingerprint", device));
    }

    /** Đăng nhập từ thiết bị mới qua OTP; trả phiên. */
    JsonNode loginNewDevice(String phone, String device) {
        clock.advance(Duration.ofSeconds(31)); // qua thời gian chờ gửi lại của OTP trước đó
        ResponseEntity<JsonNode> challenged = login(phone, PASSWORD, device);
        assertThat(challenged.getStatusCode().value()).as("login %s", challenged.getBody()).isEqualTo(422);
        ResponseEntity<JsonNode> verified = post("/api/v1/auth/login/verify", Map.of(
                "challengeId", challenged.getBody().get("params").get("challengeId").asText(),
                "otp", otpsTo(phone).get(0), "deviceFingerprint", device));
        assertThat(verified.getStatusCode().value()).isEqualTo(200);
        return verified.getBody();
    }

    ResponseEntity<JsonNode> refresh(String refreshToken) {
        return post("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    ResponseEntity<JsonNode> post(String path, Object body) {
        return post(path, body, null);
    }

    ResponseEntity<JsonNode> post(String path, Object body, String bearer) {
        return exchange(HttpMethod.POST, path, body, bearer);
    }

    ResponseEntity<JsonNode> get(String path, String bearer) {
        return exchange(HttpMethod.GET, path, null, bearer);
    }

    ResponseEntity<JsonNode> delete(String path, String bearer) {
        return exchange(HttpMethod.DELETE, path, null, bearer);
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, Object body, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);
        headers.set("User-Agent", "IdentityApiTest");
        if (bearer != null) headers.setBearerAuth(bearer);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    static void assertProblem(ResponseEntity<JsonNode> response, int status, String code) {
        assertThat(response.getStatusCode().value()).as("body: %s", response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(response.getBody().get("code").asText()).isEqualTo(code);
        assertThat(response.getBody().get("status").asInt()).isEqualTo(status);
        assertThat(response.getBody().get("traceId").asText()).isNotBlank();
    }

    /** OTP đã gửi tới một SĐT, mới nhất trước. */
    List<String> otpsTo(String phone) {
        return sender.messagesTo(e164(phone)).stream().filter(m -> m.type().equals("OTP")).map(Message::code).toList();
    }

    List<Message> noticesTo(String phone, String type) {
        return sender.messagesTo(e164(phone)).stream().filter(m -> m.type().equals(type)).toList();
    }

    /** Một mã chắc chắn khác OTP mới nhất của SĐT. */
    String wrongOtp(String phone) {
        return otpsTo(phone).get(0).equals("000000") ? "000001" : "000000";
    }

    UUID customerId(String phone) {
        return jdbc.sql("select id from identity.customer where phone = ?").param(e164(phone)).query(UUID.class).single();
    }

    void setStatus(String phone, String status) {
        jdbc.sql("update identity.customer set status = ? where phone = ?").params(status, e164(phone)).update();
    }

    long count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    static JsonNode withoutTraceId(JsonNode problem) {
        return ((com.fasterxml.jackson.databind.node.ObjectNode) problem.deepCopy()).without("traceId");
    }

    @SafeVarargs
    static <T> List<T> inParallel(Callable<T>... calls) throws Exception {
        var pool = Executors.newFixedThreadPool(calls.length);
        try {
            List<T> results = new ArrayList<>();
            for (var future : pool.invokeAll(List.of(calls))) results.add(future.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Đợi tới khi có đủ {@code expected} event của một key trên topic; trả theo thứ tự. */
    List<JsonNode> eventsFor(String topic, String key, int expected) throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<JsonNode> found = new CopyOnWriteArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(topic));
            await().atMost(60, TimeUnit.SECONDS).pollInSameThread().until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (key.equals(record.key())) found.add(json.readTree(record.value()));
                }
                return found.size() >= expected;
            });
        }
        assertThat(found).hasSize(expected);
        return found;
    }
}
