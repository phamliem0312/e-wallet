package com.ewallet.identity.web;

import static com.ewallet.identity.web.AuthController.client;

import com.ewallet.identity.application.AccountService;
import com.ewallet.identity.application.ChallengeService;
import com.ewallet.identity.domain.PhoneNumbers;
import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import com.ewallet.platform.core.error.PlatformException.FieldError;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hợp đồng: contracts/openapi/identity/public.yaml, tag me và challenges. Mọi thao tác chỉ trên dữ liệu của người gọi. */
@RestController
@RequestMapping("/api/v1")
class MeController {

    record ChangePasswordRequest(@NotEmpty String currentPassword, @NotEmpty String newPassword) {}

    record ChangePhoneRequest(@NotNull @Pattern(regexp = PhoneNumbers.PATTERN) String newPhone,
            @NotNull UUID faceChallengeId, @NotNull UUID otpChallengeId,
            @NotNull @Pattern(regexp = AuthController.OTP) String otp) {}

    record CreateChallengeRequest(@NotNull String purpose, String factor, @NotNull JsonNode payload) {}

    private final AccountService account;
    private final ChallengeService challenges;

    MeController(AccountService account, ChallengeService challenges) {
        this.account = account;
        this.challenges = challenges;
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request) {
        account.changePassword(customerId(jwt), sessionId(jwt), body.currentPassword(), body.newPassword(),
                client(request));
    }

    @PostMapping("/me/phone")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePhone(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePhoneRequest body,
            HttpServletRequest request) {
        account.changePhone(customerId(jwt), sessionId(jwt), body.newPhone(), body.faceChallengeId(),
                body.otpChallengeId(), body.otp(), client(request));
    }

    @GetMapping("/me/devices")
    Map<String, List<AccountService.DeviceView>> devices(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("items", account.devices(customerId(jwt), sessionId(jwt)));
    }

    @DeleteMapping("/me/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeDevice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID deviceId, HttpServletRequest request) {
        account.revokeDevice(customerId(jwt), deviceId, client(request));
    }

    @GetMapping("/me/consents")
    Map<String, Object> consents(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("items", account.consents(customerId(jwt)));
    }

    @PostMapping("/challenges")
    @ResponseStatus(HttpStatus.CREATED)
    ChallengeService.View createChallenge(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateChallengeRequest body) {
        String factor = body.factor() == null ? "OTP" : body.factor();
        if (!Set.of("OTP", "FACE").contains(factor)) {
            throw new PlatformException(CommonErrorCode.VALIDATION_ERROR, Map.of(),
                    List.of(new FieldError("factor", "UNSUPPORTED", "must be OTP or FACE")));
        }
        return challenges.create(customerId(jwt), body.purpose(), factor, body.payload());
    }

    private static UUID customerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Claim {@code sid} = id của token family: "phiên hiện tại". */
    private static UUID sessionId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("sid"));
    }
}
