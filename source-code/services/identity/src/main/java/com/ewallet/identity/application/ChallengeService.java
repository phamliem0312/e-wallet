package com.ewallet.identity.application;

import static com.ewallet.identity.application.Support.count;
import static com.ewallet.identity.application.Support.error;
import static com.ewallet.identity.application.Support.normalizePhone;
import static com.ewallet.identity.application.Support.validation;

import com.ewallet.identity.config.AuthProperties;
import com.ewallet.identity.domain.IdentityErrorCode;
import com.ewallet.identity.domain.OtpCodes;
import com.ewallet.identity.domain.PhoneNumbers;
import com.ewallet.identity.infrastructure.ChallengeStore;
import com.ewallet.identity.infrastructure.ChallengeStore.Challenge;
import com.ewallet.identity.infrastructure.Counters;
import com.ewallet.identity.infrastructure.CustomerStore;
import com.ewallet.identity.infrastructure.CustomerStore.Customer;
import com.ewallet.identity.infrastructure.IdentityKeys;
import com.ewallet.platform.core.Ids;
import com.ewallet.platform.core.Sensitive;
import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Cơ chế challenge dùng chung: cấp, gửi OTP, xác minh (F-ACC-01.FR-09..14, FR-41, FR-42). */
@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);
    private static final Set<String> TRANSACTION_PURPOSES = Set.of("P2P", "WITHDRAW");

    /** Khớp schema Challenge của hợp đồng. */
    public record View(UUID challengeId, String factor, Instant expiresAt, String destinationMasked,
            Instant resendAfter, Integer attemptsRemaining) {

        /** Dạng nằm trong Problem.params của CHALLENGE_REQUIRED khi đăng nhập thiết bị mới. */
        Map<String, Object> asParams() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("challengeId", challengeId.toString());
            params.put("factor", factor);
            params.put("expiresAt", expiresAt.toString());
            params.put("destinationMasked", destinationMasked);
            params.put("resendAfter", resendAfter.toString());
            params.put("attemptsRemaining", attemptsRemaining);
            return params;
        }
    }

    private final ChallengeStore store;
    private final CustomerStore customers;
    private final Counters counters;
    private final MessageSender sender;
    private final IdentityKeys keys;
    private final AuthProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    ChallengeService(ChallengeStore store, CustomerStore customers, Counters counters, MessageSender sender,
            IdentityKeys keys, AuthProperties props, TransactionTemplate tx, Clock clock) {
        this.store = store;
        this.customers = customers;
        this.counters = counters;
        this.sender = sender;
        this.keys = keys;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Cấp challenge OTP và gửi mã tới {@code phone}. Challenge cũ cùng đối tượng và nội dung bị vô hiệu (FR-12).
     * @param customerId null chỉ với mục đích REGISTER
     */
    View issueOtp(String purpose, UUID customerId, String phone, byte[] payloadHash) {
        Instant now = clock.instant();
        counters.acquireSend(purpose, phone, payloadHash, now);
        UUID id = Ids.uuidV7();
        String code = OtpCodes.generate();
        Instant expiresAt = now.plus(props.otp().ttl());
        try {
            tx.executeWithoutResult(status -> store.replace(id, customerId, phone, purpose, "OTP", payloadHash,
                    keys.otpHash(id, code), now, expiresAt));
        } catch (DuplicateKeyException concurrent) {
            // Hai yêu cầu cấp cùng lúc: uq_challenge_active chỉ cho một bên thắng
            throw new PlatformException(CommonErrorCode.RATE_LIMITED, Map.of("retryAfterSeconds", 1));
        }
        try {
            // Gửi ngoài transaction: không giữ kết nối DB trong lúc gọi dịch vụ khác
            sender.sendOtp(id, phone, new Sensitive<>(code), purpose, expiresAt);
        } catch (RuntimeException e) {
            log.warn("Cannot hand over OTP for purpose {}", purpose, e);
            store.supersede(id, now);
            counters.releaseSend(purpose, phone, payloadHash);
            count("identity.otp", "purpose", purpose, "result", "failed");
            throw error(IdentityErrorCode.OTP_SEND_FAILED);
        }
        counters.recordSend(purpose, phone);
        count("identity.otp", "purpose", purpose, "result", "sent");
        return view(id, "OTP", expiresAt, phone, now);
    }

    /**
     * Challenge giả: phản hồi và giới hạn giống hệt challenge thật nhưng không có mã nào đúng (FR-02, FR-32).
     * @param instead việc làm thay cho gửi OTP (ví dụ gửi thông báo "đã đăng ký"); null nếu không có
     */
    View issueDecoy(String purpose, String phone, Runnable instead) {
        Instant now = clock.instant();
        counters.acquireSend(purpose, phone, null, now);
        if (instead != null) {
            try {
                instead.run();
            } catch (RuntimeException e) {
                counters.releaseSend(purpose, phone, null);
                throw error(IdentityErrorCode.OTP_SEND_FAILED);
            }
        }
        counters.recordSend(purpose, phone);
        UUID id = Ids.uuidV7();
        counters.registerDecoy(id);
        return view(id, "OTP", now.plus(props.otp().ttl()), phone, now);
    }

    private View view(UUID id, String factor, Instant expiresAt, String phone, Instant now) {
        return new View(id, factor, expiresAt, PhoneNumbers.mask(phone), counters.resendAfter(now),
                props.otp().maxAttempts());
    }

    /** Challenge thật của đúng mục đích, không đụng tới lượt nhập; null nếu không có (hoặc là challenge giả). */
    Challenge peek(UUID id, String purpose) {
        return store.find(id).filter(c -> c.purpose().equals(purpose)).orElse(null);
    }

    /**
     * Kiểm mã OTP của một challenge đúng mục đích và đúng chủ. Mỗi lần gọi tốn một lượt (FR-11).
     * Chưa chốt challenge: bên gọi gọi {@link #consume} trong transaction nghiệp vụ của mình.
     */
    Challenge verifyOtp(UUID id, String purpose, String otp, Predicate<Challenge> owner) {
        Instant now = clock.instant();
        int max = props.otp().maxAttempts();
        Challenge challenge = store.find(id)
                .filter(c -> c.purpose().equals(purpose) && c.factor().equals("OTP") && owner.test(c)).orElse(null);
        if (challenge == null) {
            OptionalInt decoy = counters.decoyAttempt(id);
            throw invalid(decoy.isPresent() ? max - decoy.getAsInt() : null);
        }
        OptionalInt attempts = store.registerAttempt(id, now, max);
        if (attempts.isEmpty()) throw invalid(null);
        if (otp == null || !OtpCodes.matches(challenge.codeHash(), keys.otpHash(id, otp))) {
            throw invalid(max - attempts.getAsInt());
        }
        return challenge;
    }

    /** Chốt challenge trong transaction của bên gọi; thua cuộc đua thì CHALLENGE_INVALID. */
    void consume(Challenge challenge, Instant now) {
        if (!store.consume(challenge.id(), null, now)) throw invalid(null);
    }

    static PlatformException invalid(Integer attemptsRemaining) {
        return attemptsRemaining == null ? error(IdentityErrorCode.CHALLENGE_INVALID)
                : new PlatformException(IdentityErrorCode.CHALLENGE_INVALID,
                        Map.of("attemptsRemaining", attemptsRemaining));
    }

    // ---- Challenge do khách xin (POST /challenges) ----

    /** FR-41 (P2P) và FR-35 (CHANGE_PHONE). */
    public View create(UUID customerId, String purpose, String factor, JsonNode payload) {
        Customer customer = customers.findById(customerId).filter(Customer::active)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.UNAUTHENTICATED));
        byte[] payloadHash = payloadHash(purpose, payload);
        Instant now = clock.instant();
        // Khuôn mặt cần dữ liệu eKYC; đổi SĐT luôn cần khuôn mặt (FR-37)
        boolean needsFace = "FACE".equals(factor) || "CHANGE_PHONE".equals(purpose);
        if (needsFace && customer.kycTier() == 0) throw error(IdentityErrorCode.KYC_REQUIRED);

        if ("FACE".equals(factor)) {
            UUID id = Ids.uuidV7();
            Instant expiresAt = now.plus(props.otp().ttl());
            tx.executeWithoutResult(status ->
                    store.replace(id, customerId, null, purpose, "FACE", payloadHash, null, now, expiresAt));
            // Nộp ảnh và xác thực khuôn mặt thuộc F-ACC-02 (OQ-028); ở đây chỉ cấp challenge.
            return new View(id, "FACE", expiresAt, null, null, null);
        }
        if ("CHANGE_PHONE".equals(purpose)) {
            // FR-35: chỉ gửi OTP tới SĐT mới sau khi khuôn mặt đã đạt cho đúng SĐT đó
            if (!store.hasVerifiedFace(customerId, purpose, payloadHash, now)) {
                throw error(IdentityErrorCode.CHALLENGE_REQUIRED);
            }
            return issueOtp(purpose, customerId, normalizePhone("payload.newPhone", payload.path("newPhone").asText()),
                    payloadHash);
        }
        // P2P: luôn gửi tới SĐT đăng ký của khách, không bao giờ tới SĐT do client chỉ định (FR-41)
        return issueOtp(purpose, customerId, customer.phone(), payloadHash);
    }

    /** Băm nội dung lệnh ở dạng đã chuẩn hoá, để cấp và xác minh luôn cho cùng một giá trị. */
    static byte[] payloadHash(String purpose, JsonNode payload) {
        if (payload == null || !payload.isObject()) throw validation("payload", "REQUIRED", "must be an object");
        switch (purpose) {
            case "P2P" -> {
                requireFields(payload, Set.of("recipientPhone", "amount"));
                JsonNode amount = payload.get("amount");
                if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.asLong() < 1) {
                    throw validation("payload.amount", "INVALID", "must be an integer >= 1");
                }
                String recipient = normalizePhone("payload.recipientPhone", payload.get("recipientPhone").asText());
                return OtpCodes.sha256("P2P|" + recipient + "|" + amount.asLong());
            }
            case "CHANGE_PHONE" -> {
                requireFields(payload, Set.of("newPhone"));
                return OtpCodes.sha256("CHANGE_PHONE|"
                        + normalizePhone("payload.newPhone", payload.get("newPhone").asText()));
            }
            default -> throw validation("purpose", "UNSUPPORTED", "must be P2P or CHANGE_PHONE");
        }
    }

    private static void requireFields(JsonNode payload, Set<String> fields) {
        Set<String> actual = new java.util.HashSet<>();
        payload.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(fields)) throw validation("payload", "INVALID_FIELDS", "must have exactly " + fields);
    }

    // ---- Xác minh cho lệnh tiền (POST /internal/challenges/{id}/verify) ----

    /**
     * FR-42: challenge chỉ phục vụ một {@code consumerRef}; xác minh lại với cùng ref luôn thành công.
     * @return yếu tố của challenge, để service gọi so với yếu tố lệnh yêu cầu (OQ-049)
     */
    public String verifyForConsumer(UUID id, String otp, JsonNode payload, String consumerRef) {
        Instant now = clock.instant();
        Challenge challenge = store.find(id).filter(c -> TRANSACTION_PURPOSES.contains(c.purpose())).orElse(null);
        if (challenge == null || !Arrays.equals(challenge.payloadHash(), payloadHash(challenge.purpose(), payload))) {
            throw invalid(null);
        }
        if (challenge.consumerRef() != null) {
            if (challenge.consumerRef().equals(consumerRef)) return challenge.factor();
            throw invalid(null);
        }
        if (challenge.factor().equals("FACE")) {
            if (store.claimVerifiedFace(id, consumerRef, now) || sameConsumer(id, consumerRef)) return "FACE";
            throw invalid(null);
        }
        verifyOtp(id, challenge.purpose(), otp, c -> true);
        // Thua cuộc đua với chính lần gửi lại của cùng lệnh thì vẫn là thành công
        if (store.consume(id, consumerRef, now) || sameConsumer(id, consumerRef)) return "OTP";
        throw invalid(null);
    }

    private boolean sameConsumer(UUID id, String consumerRef) {
        return store.find(id).map(c -> consumerRef.equals(c.consumerRef())).orElse(false);
    }
}
