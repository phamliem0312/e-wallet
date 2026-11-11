package com.ewallet.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** api-gateway trước một identity giả (HTTP server của JDK) và Redis thật. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ewallet.gateway.rate-limit.limit=5",
        "ewallet.gateway.max-request-bytes=1024",
})
@Testcontainers
class GatewayTest {

    record Received(String method, String rawPath, String authorization, String forwardedFor) {}

    static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    static final ECKey KEY;
    static final ECKey OTHER_KEY;
    static final HttpServer IDENTITY;

    static {
        try {
            KEY = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
            OTHER_KEY = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
            IDENTITY = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            IDENTITY.createContext("/", exchange -> {
                String body;
                if (exchange.getRequestURI().getPath().equals("/.well-known/jwks.json")) {
                    body = new JWKSet(KEY.toPublicJWK()).toString();
                } else {
                    RECEIVED.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                            exchange.getRequestHeaders().getFirst("Authorization"),
                            exchange.getRequestHeaders().getFirst("X-Forwarded-For")));
                    exchange.getRequestBody().readAllBytes();
                    body = "{\"upstream\":\"identity\"}";
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            IDENTITY.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ewallet.gateway.identity-uri", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stopIdentity() {
        IDENTITY.stop(0);
    }

    static final AtomicInteger SEQUENCE = new AtomicInteger();

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();
    String clientIp;

    @BeforeEach
    void reset() {
        RECEIVED.clear();
        clientIp = "198.51.100." + SEQUENCE.incrementAndGet(); // IP riêng mỗi test: không dính rate limit của test khác
    }

    @Test
    @DisplayName("Endpoint công khai được chuyển tới identity, không cần token; IP của khách đi kèm")
    void publicEndpointIsRouted() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/auth/login", "{\"phone\":\"0900000001\"}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("identity");
        assertThat(RECEIVED).hasSize(1);
        assertThat(RECEIVED.get(0).rawPath()).isEqualTo("/api/v1/auth/login");
        assertThat(RECEIVED.get(0).forwardedFor()).startsWith(clientIp);
    }

    @Test
    @DisplayName("Endpoint cần đăng nhập: thiếu token, token sai chữ ký, sai issuer, hết hạn đều bị chặn ở gateway")
    void protectedEndpointRequiresValidToken() throws Exception {
        assertProblem(send("GET", "/api/v1/me/devices", null, null), 401, "UNAUTHENTICATED");
        assertProblem(send("GET", "/api/v1/me/devices", null, token(OTHER_KEY, "identity", 300)), 401, "UNAUTHENTICATED");
        assertProblem(send("GET", "/api/v1/me/devices", null, token(KEY, "someone-else", 300)), 401, "UNAUTHENTICATED");
        assertProblem(send("GET", "/api/v1/me/devices", null, token(KEY, "identity", -120)), 401, "TOKEN_EXPIRED");
        assertThat(RECEIVED).isEmpty();
    }

    @Test
    @DisplayName("Token hợp lệ: request tới identity kèm nguyên Authorization (service tự kiểm lại JWT)")
    void validTokenIsForwarded() throws Exception {
        String token = token(KEY, "identity", 300);
        HttpResponse<String> response = send("GET", "/api/v1/me/devices", null, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(RECEIVED).hasSize(1);
        assertThat(RECEIVED.get(0).authorization()).isEqualTo("Bearer " + token);
        assertThat(send("POST", "/api/v1/challenges", "{}", token).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("/internal/** và JWKS không bao giờ được định tuyến, kể cả có token hợp lệ")
    void internalPathsAreNeverRouted() throws Exception {
        String token = token(KEY, "identity", 300);
        assertThat(send("POST", "/internal/challenges/" + UUID.randomUUID() + "/verify", "{}", token).statusCode())
                .isEqualTo(404);
        assertThat(send("GET", "/internal/dev/messages?recipient=x", null, token).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/.well-known/jwks.json", null, token).statusCode()).isEqualTo(404);
        assertThat(RECEIVED).isEmpty();
    }

    @Test
    @DisplayName("Path traversal tới /internal bị chặn trước khi tới service phía sau")
    void pathTraversalIsRejected() throws Exception {
        String token = token(KEY, "identity", 300);
        for (String path : List.of("/api/v1/auth/../../internal/dev/messages", "/api/v1/auth/%2e%2e/%2e%2e/internal/dev/messages",
                "/api/v1/auth/..%2f..%2finternal/dev/messages", "/api/v1/me/./devices", "/api/v1/auth/..%5C..%5Cinternal")) {
            HttpResponse<String> response = send("GET", path, null, token);
            assertThat(response.statusCode()).as(path).isEqualTo(400);
        }
        assertThat(RECEIVED).isEmpty();
    }

    @Test
    @DisplayName("Rate limit theo IP: request thứ 6 trong cửa sổ bị chặn bằng Problem RATE_LIMITED kèm Retry-After")
    void rateLimitPerClientIp() throws Exception {
        for (int i = 0; i < 5; i++) assertThat(send("POST", "/api/v1/auth/login", "{}", null).statusCode()).isEqualTo(200);
        HttpResponse<String> limited = send("POST", "/api/v1/auth/login", "{}", null);
        assertProblem(limited, 429, "RATE_LIMITED");
        long retryAfter = Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow());
        assertThat(retryAfter).isBetween(1L, 60L);
        assertThat(RECEIVED).hasSize(5);

        clientIp = "198.51.100.250"; // IP khác không bị ảnh hưởng
        assertThat(send("POST", "/api/v1/auth/login", "{}", null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Request quá kích thước tối đa bị từ chối, không tới identity")
    void oversizedRequestIsRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/auth/login", "{\"x\":\"" + "a".repeat(2048) + "\"}", null);
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(RECEIVED).isEmpty();
    }

    // ---- helpers ----

    HttpResponse<String> send(String method, String rawPath, String body, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    void assertProblem(HttpResponse<String> response, int status, String code) throws IOException {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("traceId").asText()).isNotBlank();
    }

    static String token(ECKey key, String issuer, long secondsToExpiry) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).subject(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString()).issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.plusSeconds(secondsToExpiry))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID())
                .type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }
}
