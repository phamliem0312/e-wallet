package com.ewallet.identity.application;

import static com.ewallet.identity.application.Support.count;

import com.ewallet.identity.application.Support.Audit;
import com.ewallet.identity.application.Support.AuthSession;
import com.ewallet.identity.application.Support.Client;
import com.ewallet.identity.config.AuthProperties;
import com.ewallet.identity.domain.OtpCodes;
import com.ewallet.identity.infrastructure.CustomerStore;
import com.ewallet.identity.infrastructure.IdentityKeys;
import com.ewallet.identity.infrastructure.SessionStore;
import com.ewallet.identity.infrastructure.SessionStore.Token;
import com.ewallet.platform.core.Ids;
import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Phiên: cấp, làm mới xoay vòng, phát hiện dùng lại, đăng xuất (F-ACC-01.FR-24..28, FR-31). */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionStore sessions;
    private final CustomerStore customers;
    private final IdentityKeys keys;
    private final AuthProperties props;
    private final Audit audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    SessionService(SessionStore sessions, CustomerStore customers, IdentityKeys keys, AuthProperties props,
            Audit audit, TransactionTemplate tx, Clock clock) {
        this.sessions = sessions;
        this.customers = customers;
        this.keys = keys;
        this.props = props;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    /** Phiên mới (token family mới) sau khi đăng ký hoặc đăng nhập. Gọi trong transaction của bên gọi. */
    AuthSession issue(UUID customerId, UUID deviceId, Instant now) {
        return newToken(customerId, deviceId, Ids.uuidV7(), now.plus(props.refresh().ttl()), now);
    }

    private AuthSession newToken(UUID customerId, UUID deviceId, UUID familyId, Instant familyExpiresAt,
            Instant now) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String refreshToken = "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        // Một cột hạn cho cả hai quy tắc của FR-27: không hoạt động và hạn tuyệt đối của family
        Instant idleExpiry = now.plus(props.session().idleTimeout());
        Instant expiresAt = idleExpiry.isBefore(familyExpiresAt) ? idleExpiry : familyExpiresAt;
        sessions.insertToken(Ids.uuidV7(), customerId, deviceId, familyId, OtpCodes.sha256(refreshToken), now,
                expiresAt, familyExpiresAt);
        return new AuthSession(keys.accessToken(customerId, familyId, now), refreshToken,
                keys.accessTokenExpiry(now), expiresAt);
    }

    /** FR-25..27, FR-31. Mọi trường hợp không hợp lệ đều trả UNAUTHENTICATED, không nói lý do. */
    public AuthSession refresh(String refreshToken, Client client) {
        Instant now = clock.instant();
        byte[] hash = OtpCodes.sha256(refreshToken);
        AuthSession session = tx.execute(status -> {
            Token token = sessions.useToken(hash, now).orElse(null);
            if (token == null) {
                sessions.findToken(hash).filter(used -> reusedOutsideGrace(used, now)).ifPresent(used -> {
                    // Token đã dùng lại xuất hiện: coi như bị đánh cắp, thu hồi cả chuỗi (FR-26)
                    sessions.revokeFamily(used.customerId(), used.familyId(), now);
                    audit.record("REFRESH_TOKEN_REUSED", used.customerId(), "session:" + used.familyId(), client,
                            Map.of());
                    count("identity.refresh.reuse");
                    log.warn("Refresh token reuse detected; family {} revoked", used.familyId());
                });
                return null;
            }
            boolean active = customers.findById(token.customerId()).map(CustomerStore.Customer::active).orElse(false);
            if (!active || !token.familyExpiresAt().isAfter(now)) {
                sessions.revokeFamily(token.customerId(), token.familyId(), now);
                return null;
            }
            return newToken(token.customerId(), token.deviceId(), token.familyId(), token.familyExpiresAt(), now);
        });
        if (session == null) throw new PlatformException(CommonErrorCode.UNAUTHENTICATED);
        return session;
    }

    private boolean reusedOutsideGrace(Token token, Instant now) {
        return token.usedAt() != null && token.revokedAt() == null
                && !token.usedAt().plus(props.refresh().reuseGrace()).isAfter(now);
    }

    /** FR-28: chỉ thu hồi phiên hiện tại. Gọi lại khi đã thu hồi vẫn thành công. */
    public void logout(UUID customerId, UUID familyId) {
        sessions.revokeFamily(customerId, familyId, clock.instant());
    }
}
