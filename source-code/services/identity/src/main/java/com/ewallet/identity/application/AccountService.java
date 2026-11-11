package com.ewallet.identity.application;

import static com.ewallet.identity.application.Support.error;
import static com.ewallet.identity.application.Support.normalizePhone;
import static com.ewallet.identity.application.Support.validation;

import com.ewallet.identity.application.Support.Audit;
import com.ewallet.identity.application.Support.Client;
import com.ewallet.identity.domain.FullNames;
import com.ewallet.identity.domain.IdentityErrorCode;
import com.ewallet.identity.domain.OtpCodes;
import com.ewallet.identity.domain.PhoneNumbers;
import com.ewallet.identity.infrastructure.ChallengeStore;
import com.ewallet.identity.infrastructure.ChallengeStore.Challenge;
import com.ewallet.identity.infrastructure.CustomerStore;
import com.ewallet.identity.infrastructure.CustomerStore.Consent;
import com.ewallet.identity.infrastructure.CustomerStore.Customer;
import com.ewallet.identity.infrastructure.SessionStore;
import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import com.ewallet.platform.messaging.OutboxService;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Thao tác của khách đã đăng nhập: đổi mật khẩu, đổi SĐT, thiết bị, đồng ý (UC-05..UC-07). */
@Service
public class AccountService {

    /** Khớp schema Device của hợp đồng; không có dấu vân tay. */
    public record DeviceView(UUID deviceId, String label, Instant lastSeenAt,
            @com.fasterxml.jackson.annotation.JsonProperty("isCurrent") boolean isCurrent) {}

    private final CustomerStore customers;
    private final SessionStore sessions;
    private final ChallengeStore challengeStore;
    private final ChallengeService challenges;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final OutboxService outbox;
    private final Audit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    AccountService(CustomerStore customers, SessionStore sessions, ChallengeStore challengeStore,
            ChallengeService challenges, AuthService auth, PasswordEncoder passwords, OutboxService outbox,
            Audit audit, TransactionTemplate tx, Clock clock) {
        this.customers = customers;
        this.sessions = sessions;
        this.challengeStore = challengeStore;
        this.challenges = challenges;
        this.auth = auth;
        this.passwords = passwords;
        this.outbox = outbox;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    /** FR-34: cần mật khẩu hiện tại; thu hồi mọi phiên khác, giữ phiên hiện tại. */
    public void changePassword(UUID customerId, UUID currentFamilyId, String currentPassword, String newPassword,
            Client client) {
        Instant now = clock.instant();
        Customer customer = activeCustomer(customerId);
        auth.requirePolicy("newPassword", newPassword, customer.phone());
        auth.verifyPassword(customer, currentPassword, now, client, "PASSWORD_CHANGE_FAILED");
        if (currentPassword.equals(newPassword)) {
            throw validation("newPassword", "SAME_AS_CURRENT", "must differ from the current password");
        }
        String passwordHash = passwords.encode(newPassword);
        tx.executeWithoutResult(status -> {
            customers.updatePassword(customerId, passwordHash, now);
            sessions.revokeAll(customerId, currentFamilyId, now);
            audit.record("PASSWORD_CHANGED", customerId, "customer:" + customerId, client, Map.of());
        });
        auth.notice("PASSWORD_CHANGED", customer.phone(), Map.of("at", now.toString()));
    }

    /**
     * FR-35..FR-37: khuôn mặt đã đạt (challenge FACE do F-ACC-02 chốt) rồi OTP tới SĐT mới. Cả hai challenge gắn với
     * đúng SĐT mới (mặc định bảo thủ cho OQ-045).
     */
    public void changePhone(UUID customerId, UUID currentFamilyId, String newPhoneInput, UUID faceChallengeId,
            UUID otpChallengeId, String otp, Client client) {
        Instant now = clock.instant();
        Customer customer = activeCustomer(customerId);
        String newPhone = normalizePhone("newPhone", newPhoneInput);
        if (customer.kycTier() == 0) throw error(IdentityErrorCode.KYC_REQUIRED);
        byte[] payloadHash = OtpCodes.sha256("CHANGE_PHONE|" + newPhone);
        Challenge face = challengeStore.find(faceChallengeId).orElse(null);
        boolean faceVerified = face != null && face.purpose().equals("CHANGE_PHONE") && face.factor().equals("FACE")
                && customerId.equals(face.customerId()) && face.consumedAt() != null
                && face.expiresAt().isAfter(now) && Arrays.equals(face.payloadHash(), payloadHash);
        if (!faceVerified) throw error(IdentityErrorCode.CHALLENGE_INVALID);
        if (customers.findByPhone(newPhone).isPresent()) throw error(IdentityErrorCode.PHONE_ALREADY_REGISTERED);
        Challenge challenge = challenges.verifyOtp(otpChallengeId, "CHANGE_PHONE", otp,
                c -> customerId.equals(c.customerId()) && Arrays.equals(c.payloadHash(), payloadHash));
        try {
            tx.executeWithoutResult(status -> {
                challenges.consume(challenge, now);
                long version = customers.updatePhone(customerId, newPhone, now);
                sessions.revokeAll(customerId, currentFamilyId, now);
                outbox.publish("identity.events", customerId.toString(), "CustomerUpdated", version,
                        Map.of("customerId", customerId.toString(), "phone", newPhone,
                                "displayMasked", FullNames.displayMasked(customer.fullName())));
                audit.record("PHONE_CHANGED", customerId, "customer:" + customerId, client,
                        Map.of("newPhone", PhoneNumbers.mask(newPhone)));
            });
        } catch (DuplicateKeyException e) {
            throw error(IdentityErrorCode.PHONE_ALREADY_REGISTERED);
        }
        Map<String, String> params = Map.of("at", now.toString(), "newPhone", PhoneNumbers.mask(newPhone));
        auth.notice("PHONE_CHANGED", customer.phone(), params);
        auth.notice("PHONE_CHANGED", newPhone, params);
    }

    /** FR-38. */
    public List<DeviceView> devices(UUID customerId, UUID currentFamilyId) {
        UUID current = sessions.deviceOfFamily(customerId, currentFamilyId).orElse(null);
        return sessions.trustedDevices(customerId).stream()
                .map(d -> new DeviceView(d.id(), d.label(), d.lastSeenAt(), d.id().equals(current))).toList();
    }

    /** FR-39. Thiết bị của người khác và thiết bị không tồn tại đều là NOT_FOUND. */
    public void revokeDevice(UUID customerId, UUID deviceId, Client client) {
        Instant now = clock.instant();
        if (!sessions.deviceExists(customerId, deviceId)) throw new PlatformException(CommonErrorCode.NOT_FOUND);
        tx.executeWithoutResult(status -> {
            sessions.untrustDevice(customerId, deviceId);
            sessions.revokeDevice(customerId, deviceId, now);
            audit.record("DEVICE_REVOKED", customerId, "device:" + deviceId, client, Map.of());
        });
    }

    /** FR-40. */
    public List<Consent> consents(UUID customerId) {
        return customers.currentConsents(customerId);
    }

    private Customer activeCustomer(UUID customerId) {
        return customers.findById(customerId).filter(Customer::active)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.UNAUTHENTICATED));
    }
}
