package com.ewallet.identity.infrastructure;

import com.ewallet.identity.config.AuthProperties;
import com.ewallet.identity.domain.OtpCodes;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Khoá ký JWT (ES256) và khoá HMAC của identity. Thiếu cấu hình thì sinh khoá tạm cho dev: token và thiết bị tin cậy
 * mất hiệu lực mỗi lần khởi động lại, và không dùng được khi chạy nhiều bản. Ở profile prod thiếu khoá là lỗi.
 * ponytail: một khoá ký, chưa xoay vòng khoá; khi cần thì thêm khoá cũ vào JWKS trong thời gian chuyển tiếp.
 */
@Component
public class IdentityKeys {

    private static final Logger log = LoggerFactory.getLogger(IdentityKeys.class);

    private final ECKey signingKey;
    private final byte[] hmacKey;
    private final AuthProperties props;

    public IdentityKeys(AuthProperties props, Environment environment) throws JOSEException, ParseException {
        this.props = props;
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        String jwk = props.jwt().privateJwk();
        String hmac = props.hmacKey();
        if (prod && (isBlank(jwk) || isBlank(hmac))) {
            throw new IllegalStateException("auth.jwt.private-jwk and auth.hmac-key are required with profile 'prod'");
        }
        if (isBlank(jwk)) {
            log.warn("auth.jwt.private-jwk is not set: using an ephemeral signing key (dev only)");
            signingKey = new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).keyIDFromThumbprint(true).generate();
        } else {
            signingKey = ECKey.parse(jwk);
            if (!signingKey.isPrivate() || !Curve.P_256.equals(signingKey.getCurve())) {
                throw new IllegalStateException("auth.jwt.private-jwk must be a private EC P-256 JWK");
            }
        }
        if (isBlank(hmac)) {
            log.warn("auth.hmac-key is not set: using an ephemeral HMAC key (dev only)");
            hmacKey = new byte[32];
            new SecureRandom().nextBytes(hmacKey);
        } else {
            hmacKey = Base64.getDecoder().decode(hmac);
            if (hmacKey.length < 32) throw new IllegalStateException("auth.hmac-key must be at least 32 bytes");
        }
    }

    /** Access token (FR-24): sub = khách, sid = token family (phiên hiện tại). */
    public String accessToken(UUID customerId, UUID familyId, Instant now) {
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(props.jwt().issuer())
                .subject(customerId.toString())
                .claim("sid", familyId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(accessTokenExpiry(now)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new ECDSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign access token", e);
        }
        return jwt.serialize();
    }

    public Instant accessTokenExpiry(Instant now) {
        return now.truncatedTo(ChronoUnit.SECONDS).plus(props.access().ttl());
    }

    public ECKey publicKey() {
        return signingKey.toPublicJWK();
    }

    public Map<String, Object> jwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    /** HMAC cho dấu vân tay thiết bị (FR-23); không lưu bản gốc. */
    public byte[] fingerprintHash(String fingerprint) {
        return OtpCodes.hmac(hmacKey, "fp:" + fingerprint);
    }

    public byte[] otpHash(UUID challengeId, String code) {
        return OtpCodes.hash(hmacKey, challengeId, code);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
