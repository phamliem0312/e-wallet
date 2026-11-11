package com.ewallet.gateway;

import com.ewallet.platform.core.error.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * api-gateway (design §5, §10): định tuyến theo prefix tới service sở hữu (api doc §2), kiểm JWT của khách bằng JWKS
 * của identity, rate limit theo IP trên Redis, giới hạn kích thước request. {@code /internal/**} không bao giờ được
 * định tuyến. Path traversal (`/api/v1/auth/../../internal/...`, `%2e`, `%2f`...) do tường lửa mặc định của Spring
 * Security (StrictServerWebExchangeFirewall) chặn bằng 400 trước khi tới route; GatewayTest giữ hành vi này.
 * Định tuyến cho service mới thêm vào {@link #routes} khi service đó có.
 */
@Configuration
class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    /** Endpoint không cần JWT (hợp đồng identity/public.yaml, `security: []`). */
    static final String[] PUBLIC_PATHS = { "/api/v1/auth/register", "/api/v1/auth/register/verify",
            "/api/v1/auth/login", "/api/v1/auth/login/verify", "/api/v1/auth/refresh", "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset", "/actuator/health/**" };

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties props) {
        DataSize maxSize = DataSize.ofBytes(props.maxRequestBytes());
        return builder.routes()
                .route("identity", r -> r
                        .path("/api/v1/auth/**", "/api/v1/me", "/api/v1/me/**", "/api/v1/challenges")
                        .filters(f -> f.setRequestSize(maxSize))
                        .uri(props.identityUri()))
                .build();
    }

    /**
     * Rate limit theo IP của khách (NFR-S-03), cửa sổ cố định trên Redis. IP lấy từ X-Forwarded-For, tin
     * {@code max-trusted-index} bước proxy (web BFF đứng trước gateway). Redis lỗi thì cho qua (ghi log): mất rate limit
     * tạm thời tốt hơn khách không đăng nhập được; identity vẫn còn giới hạn riêng của nó.
     * ponytail: cửa sổ cố định cho phép tối đa 2 lần ngưỡng quanh ranh giới cửa sổ.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    WebFilter rateLimit(ReactiveStringRedisTemplate redis, GatewayProperties props, ObjectMapper json) {
        XForwardedRemoteAddressResolver resolver = XForwardedRemoteAddressResolver.maxTrustedIndex(props.maxTrustedIndex());
        long windowSeconds = props.rateLimit().window().toSeconds();
        return (exchange, chain) -> {
            if (!exchange.getRequest().getPath().value().startsWith("/api/")) return chain.filter(exchange);
            String ip = resolver.resolve(exchange).getHostString();
            long now = Instant.now().getEpochSecond();
            String key = "gw:rl:" + ip + ":" + now / windowSeconds;
            Mono<Long> count = redis.opsForValue().increment(key)
                    .flatMap(n -> n == 1 ? redis.expire(key, props.rateLimit().window()).thenReturn(n) : Mono.just(n))
                    .onErrorResume(e -> {
                        log.warn("Rate limit store unavailable; letting request through", e);
                        return Mono.just(0L);
                    });
            return count.flatMap(n -> n > props.rateLimit().limit()
                    ? Problems.write(exchange, json, CommonErrorCode.RATE_LIMITED, windowSeconds - now % windowSeconds)
                    : chain.filter(exchange));
        };
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, ObjectMapper json) {
        ServerAuthenticationEntryPoint entryPoint = (exchange, e) -> Problems.write(exchange, json,
                isExpired(e) ? CommonErrorCode.TOKEN_EXPIRED : CommonErrorCode.UNAUTHENTICATED, null);
        return http
                // Gateway không dùng cookie: CSRF được chặn ở BFF, nơi giữ cookie (F-ACC-01.FR-30)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(a -> a.pathMatchers(PUBLIC_PATHS).permitAll().anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(x -> x.authenticationEntryPoint(entryPoint).accessDeniedHandler(
                        (exchange, e) -> Problems.write(exchange, json, CommonErrorCode.FORBIDDEN, null)))
                .build();
    }

    /** Chỉ nhận token ES256 do identity ký, đúng issuer; khoá công khai lấy từ JWKS của identity (có cache). */
    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewayProperties props) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(props.identityUri() + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    private static boolean isExpired(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("expired")) return true;
        }
        return false;
    }
}
