package com.ewallet.identity.config;

import com.ewallet.identity.infrastructure.IdentityKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
class IdentityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Argon2id (design §10, NFR-02); bảng credential từ chối băm bằng thuật toán khác. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * identity kiểm token của chính nó bằng khoá công khai trong bộ nhớ, không gọi JWKS qua mạng.
     * Hết hạn đúng tại {@code exp} (không có độ trễ cho phép): access token sống đúng auth.access.ttl (FR-24).
     */
    @Bean
    JwtDecoder jwtDecoder(IdentityKeys keys, AuthProperties props, Clock clock) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256,
                new ImmutableJWKSet<>(new JWKSet(keys.publicKey()))));
        processor.setJWTClaimsSetVerifier((claims, context) -> { }); // thời hạn và issuer kiểm ở validator bên dưới
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(jwt -> {
            if (!props.jwt().issuer().equals(jwt.getClaimAsString("iss"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid issuer", null));
            }
            if (jwt.getExpiresAt() == null || !clock.instant().isBefore(jwt.getExpiresAt())) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Jwt expired at " + jwt.getExpiresAt(), null));
            }
            return OAuth2TokenValidatorResult.success();
        });
        return decoder;
    }
}
