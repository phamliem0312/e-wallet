package com.ewallet.identity.application;

import static com.ewallet.identity.application.Support.count;
import static com.ewallet.identity.application.Support.error;
import static com.ewallet.identity.application.Support.normalizePhone;
import static com.ewallet.identity.application.Support.validation;

import com.ewallet.identity.application.Support.Audit;
import com.ewallet.identity.application.Support.AuthSession;
import com.ewallet.identity.application.Support.Client;
import com.ewallet.identity.config.AuthProperties;
import com.ewallet.identity.domain.DeviceTrust;
import com.ewallet.identity.domain.FullNames;
import com.ewallet.identity.domain.IdentityErrorCode;
import com.ewallet.identity.domain.PasswordPolicy;
import com.ewallet.identity.domain.PhoneNumbers;
import com.ewallet.identity.infrastructure.ChallengeStore.Challenge;
import com.ewallet.identity.infrastructure.Counters;
import com.ewallet.identity.infrastructure.CustomerStore;
import com.ewallet.identity.infrastructure.CustomerStore.Credential;
import com.ewallet.identity.infrastructure.CustomerStore.Customer;
import com.ewallet.identity.infrastructure.IdentityKeys;
import com.ewallet.identity.infrastructure.SessionStore;
import com.ewallet.identity.infrastructure.SessionStore.Device;
import com.ewallet.platform.core.Ids;
import com.ewallet.platform.core.error.PlatformException;
import com.ewallet.platform.messaging.OutboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Đăng ký, đăng nhập, quên mật khẩu (F-ACC-01.UC-01, UC-02, UC-04). */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CustomerStore customers;
    private final SessionStore sessions;
    private final ChallengeService challenges;
    private final SessionService sessionService;
    private final Counters counters;
    private final MessageSender sender;
    private final PasswordEncoder passwords;
    private final IdentityKeys keys;
    private final OutboxService outbox;
    private final Audit audit;
    private final AuthProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;
    /** Băm của một mật khẩu ngẫu nhiên: để SĐT không tồn tại cũng tốn một lần kiểm Argon2id (NFR-03). */
    private final String dummyHash;

    AuthService(CustomerStore customers, SessionStore sessions, ChallengeService challenges,
            SessionService sessionService, Counters counters, MessageSender sender, PasswordEncoder passwords,
            IdentityKeys keys, OutboxService outbox, Audit audit, AuthProperties props, TransactionTemplate tx,
            Clock clock) {
        this.customers = customers;
        this.sessions = sessions;
        this.challenges = challenges;
        this.sessionService = sessionService;
        this.counters = counters;
        this.sender = sender;
        this.passwords = passwords;
        this.keys = keys;
        this.outbox = outbox;
        this.audit = audit;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    // ---- Đăng ký ----

    /** FR-01, FR-02. */
    public ChallengeService.View startRegistration(String phoneInput, Client client) {
        counters.checkRate("register-ip", client.ip());
        String phone = normalizePhone("phone", phoneInput);
        if (customers.findByPhone(phone).isPresent()) {
            // Không tiết lộ SĐT đã đăng ký: phản hồi như thật, báo cho chủ SĐT qua tin nhắn (OQ-031)
            return challenges.issueDecoy("REGISTER", phone,
                    () -> sender.sendNotice("PHONE_ALREADY_REGISTERED", phone, Map.of()));
        }
        return challenges.issueOtp("REGISTER", null, phone, null);
    }

    /** FR-03..FR-08. Thứ tự kiểm tra theo BR-06. */
    public AuthSession completeRegistration(UUID challengeId, String otp, String password, String fullName,
            String termsVersion, String deviceFingerprint, Client client) {
        counters.checkRate("otp-verify-ip", client.ip());
        Instant now = clock.instant();
        // (1) Định dạng và chính sách: không tiêu hao lượt OTP, challenge giữ nguyên
        Challenge known = challenges.peek(challengeId, "REGISTER");
        String name = FullNames.normalize(fullName)
                .orElseThrow(() -> validation("fullName", "INVALID", "must be 1-128 characters without control characters"));
        requirePolicy("password", password, known == null ? null : known.phone());
        String currentTerms = props.terms().currentVersion();
        if (!currentTerms.equals(termsVersion)) {
            throw new PlatformException(IdentityErrorCode.CONSENT_REQUIRED, Map.of("currentTermsVersion", currentTerms));
        }
        // (2) Challenge và OTP
        Challenge challenge = challenges.verifyOtp(challengeId, "REGISTER", otp, c -> true);
        String passwordHash = passwords.encode(password);
        UUID customerId = Ids.uuidV7();
        try {
            // (3) SĐT chưa có tài khoản: uq_customer_phone quyết định; mọi thứ cùng thành công hoặc không có gì (FR-05)
            return tx.execute(status -> {
                challenges.consume(challenge, now);
                customers.insert(customerId, challenge.phone(), name, passwordHash, now);
                customers.insertConsent(Ids.uuidV7(), customerId, "TERMS", termsVersion, true, client.ip(),
                        client.userAgent(), now);
                UUID deviceId = sessions.trustDevice(Ids.uuidV7(), customerId,
                        keys.fingerprintHash(deviceFingerprint), deviceLabel(client), now);
                outbox.publish("identity.events", customerId.toString(), "CustomerRegistered", 1,
                        Map.of("customerId", customerId.toString(), "phone", challenge.phone(),
                                "displayMasked", FullNames.displayMasked(name)));
                audit.record("CUSTOMER_REGISTERED", customerId, "customer:" + customerId, client, Map.of());
                return sessionService.issue(customerId, deviceId, now);
            });
        } catch (DuplicateKeyException e) {
            throw error(IdentityErrorCode.PHONE_ALREADY_REGISTERED);
        }
    }

    // ---- Đăng nhập ----

    /** FR-15..FR-20. Thứ tự lỗi theo BR-05. */
    public AuthSession login(String phoneInput, String password, String deviceFingerprint, Client client) {
        String phone = normalizePhone("phone", phoneInput);
        counters.checkRate("login-ip", client.ip());
        counters.checkRate("login-phone", phone);
        Instant now = clock.instant();
        // Tài khoản CLOSED (và trạng thái khác chưa dùng) coi như không tồn tại (FR-18)
        Customer customer = customers.findByPhone(phone)
                .filter(c -> c.active() || c.status().equals("LOCKED")).orElse(null);
        if (customer == null) {
            passwords.matches(password, dummyHash);
            audit.record("LOGIN_FAILED", null, "phone:" + PhoneNumbers.mask(phone), client,
                    Map.of("reason", "INVALID_CREDENTIALS"));
            count("identity.login", "result", "invalid_credentials");
            throw error(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        verifyPassword(customer, password, now, client, "LOGIN_FAILED");
        // Trạng thái đứng sau mật khẩu: người không biết mật khẩu không dò được tài khoản nào đang bị khoá
        if (!customer.active()) {
            count("identity.login", "result", "account_locked");
            throw error(IdentityErrorCode.ACCOUNT_LOCKED);
        }
        Device device = sessions.findDevice(customer.id(), keys.fingerprintHash(deviceFingerprint)).orElse(null);
        if (device == null || DeviceTrust.isNew(device.trustedAt(), now, trustTtl())) {
            ChallengeService.View view = challenges.issueOtp("LOGIN_NEW_DEVICE", customer.id(), customer.phone(), null);
            count("identity.login", "result", "challenge_required");
            throw new PlatformException(IdentityErrorCode.CHALLENGE_REQUIRED, view.asParams());
        }
        return tx.execute(status -> {
            sessions.touchDevice(device.id(), now);
            audit.record("LOGIN_SUCCEEDED", customer.id(), "customer:" + customer.id(), client, Map.of());
            count("identity.login", "result", "success");
            return sessionService.issue(customer.id(), device.id(), now);
        });
    }

    /** FR-21, FR-22. */
    public AuthSession verifyLogin(UUID challengeId, String otp, String deviceFingerprint, Client client) {
        counters.checkRate("otp-verify-ip", client.ip());
        Instant now = clock.instant();
        Challenge challenge = challenges.verifyOtp(challengeId, "LOGIN_NEW_DEVICE", otp, c -> true);
        Customer customer = customers.findById(challenge.customerId()).orElseThrow();
        if (!customer.active()) {
            throw error(customer.status().equals("LOCKED") ? IdentityErrorCode.ACCOUNT_LOCKED
                    : IdentityErrorCode.INVALID_CREDENTIALS);
        }
        String label = deviceLabel(client);
        AuthSession session = tx.execute(status -> {
            challenges.consume(challenge, now);
            UUID deviceId = sessions.trustDevice(Ids.uuidV7(), customer.id(),
                    keys.fingerprintHash(deviceFingerprint), label, now);
            audit.record("LOGIN_SUCCEEDED", customer.id(), "customer:" + customer.id(), client,
                    Map.of("newDevice", true));
            count("identity.login", "result", "success");
            return sessionService.issue(customer.id(), deviceId, now);
        });
        notice("NEW_DEVICE_LOGIN", customer.phone(), Map.of("device", label, "at", now.toString()));
        return session;
    }

    /**
     * Kiểm mật khẩu kèm khoá tạm (FR-17, BR-07); dùng cho đăng nhập và đổi mật khẩu.
     * @throws PlatformException ACCOUNT_TEMP_LOCKED khi đang khoá (không kiểm mật khẩu, không cộng bộ đếm),
     *                           INVALID_CREDENTIALS khi sai mật khẩu
     */
    void verifyPassword(Customer customer, String password, Instant now, Client client, String failureAction) {
        Credential credential = customers.credential(customer.id());
        if (credential.lockedUntil() != null) {
            if (credential.lockedUntil().isAfter(now)) {
                long seconds = Math.max(1, Duration.between(now, credential.lockedUntil()).toSeconds());
                throw new PlatformException(IdentityErrorCode.ACCOUNT_TEMP_LOCKED, Map.of("retryAfterSeconds", seconds));
            }
            customers.resetFailures(customer.id());
        }
        if (!passwords.matches(password, credential.passwordHash())) {
            Credential after = customers.recordFailure(customer.id(), now, props.login().maxFailures(),
                    props.login().lockDuration());
            audit.record(failureAction, customer.id(), "customer:" + customer.id(), client,
                    Map.of("reason", "INVALID_CREDENTIALS"));
            count("identity.login", "result", "invalid_credentials");
            if (after.lockedUntil() != null && after.lockedUntil().isAfter(now)) {
                audit.record("ACCOUNT_TEMP_LOCKED", customer.id(), "customer:" + customer.id(), client, Map.of());
                count("identity.lockout");
            }
            throw error(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        customers.resetFailures(customer.id());
    }

    // ---- Quên mật khẩu ----

    /** FR-32: phản hồi giống nhau dù SĐT có tài khoản hay không; chỉ tài khoản ACTIVE nhận OTP. */
    public ChallengeService.View startPasswordReset(String phoneInput, Client client) {
        counters.checkRate("forgot-ip", client.ip());
        String phone = normalizePhone("phone", phoneInput);
        Customer customer = customers.findByPhone(phone).filter(Customer::active).orElse(null);
        return customer == null ? challenges.issueDecoy("RESET_PASSWORD", phone, null)
                : challenges.issueOtp("RESET_PASSWORD", customer.id(), phone, null);
    }

    /** FR-33. Chỉ cần OTP; có thêm khuôn mặt hay không đang chờ OQ-034. */
    public void completePasswordReset(UUID challengeId, String otp, String newPassword, Client client) {
        counters.checkRate("otp-verify-ip", client.ip());
        Instant now = clock.instant();
        Challenge known = challenges.peek(challengeId, "RESET_PASSWORD");
        requirePolicy("newPassword", newPassword, known == null ? null : known.phone());
        Challenge challenge = challenges.verifyOtp(challengeId, "RESET_PASSWORD", otp, c -> true);
        String passwordHash = passwords.encode(newPassword);
        tx.executeWithoutResult(status -> {
            challenges.consume(challenge, now);
            customers.updatePassword(challenge.customerId(), passwordHash, now);
            sessions.revokeAll(challenge.customerId(), null, now);
            audit.record("PASSWORD_RESET", challenge.customerId(), "customer:" + challenge.customerId(), client,
                    Map.of());
        });
        notice("PASSWORD_CHANGED", challenge.phone(), Map.of("at", now.toString()));
    }

    // ---- dùng chung ----

    void requirePolicy(String field, String password, String phone) {
        if (!PasswordPolicy.accepts(password, phone, props.password().minLength(), props.password().maxLength())) {
            throw validation(field, "PASSWORD_POLICY", "must be " + props.password().minLength() + "-"
                    + props.password().maxLength() + " characters and differ from the phone number");
        }
    }

    /** Thông báo bảo mật lỗi thì không làm hỏng thao tác chính đã thành công (SRS §4.3). */
    void notice(String type, String phone, Map<String, String> params) {
        try {
            sender.sendNotice(type, phone, params);
        } catch (RuntimeException e) {
            log.warn("Cannot send security notice {}", type, e);
        }
    }

    private Duration trustTtl() {
        return props.device() == null ? null : props.device().trustTtl();
    }

    /** Nhãn thiết bị lấy từ user agent, cắt còn 64 ký tự (cột device.label); null nếu không có. */
    static String deviceLabel(Client client) {
        String userAgent = client.userAgent();
        return userAgent.isBlank() ? null : userAgent.substring(0, Math.min(64, userAgent.length()));
    }
}
