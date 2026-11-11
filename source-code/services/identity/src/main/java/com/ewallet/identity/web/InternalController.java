package com.ewallet.identity.web;

import com.ewallet.identity.application.ChallengeService;
import com.ewallet.identity.infrastructure.IdentityKeys;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nội bộ (contracts/openapi/identity/internal.yaml) và JWKS. {@code /internal/**} chỉ đi trong mesh:
 * mTLS + AuthorizationPolicy cho `payment`, `bank-gateway`; không bao giờ được route qua gateway.
 */
@RestController
class InternalController {

    record VerifyChallengeRequest(@Pattern(regexp = AuthController.OTP) String otp, @NotNull JsonNode payload,
            @NotBlank @Size(max = 64) String consumerRef) {}

    private final ChallengeService challenges;
    private final IdentityKeys keys;

    InternalController(ChallengeService challenges, IdentityKeys keys) {
        this.challenges = challenges;
        this.keys = keys;
    }

    @PostMapping("/internal/challenges/{challengeId}/verify")
    Map<String, String> verifyChallenge(@PathVariable UUID challengeId,
            @Valid @RequestBody VerifyChallengeRequest body) {
        return Map.of("factor",
                challenges.verifyForConsumer(challengeId, body.otp(), body.payload(), body.consumerRef()));
    }

    /** Khoá công khai để gateway và các service khác kiểm JWT của khách (design §10). */
    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return keys.jwks();
    }
}
