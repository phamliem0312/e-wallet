package com.ewallet.identity.web;

import com.ewallet.identity.application.AuthService;
import com.ewallet.identity.application.ChallengeService;
import com.ewallet.identity.application.SessionService;
import com.ewallet.identity.application.Support.AuthSession;
import com.ewallet.identity.application.Support.Client;
import com.ewallet.identity.domain.PhoneNumbers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hợp đồng: contracts/openapi/identity/public.yaml, tag auth. Người gọi là BFF. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String OTP = "^[0-9]{6}$";

    record PhoneRequest(@NotNull @Pattern(regexp = PhoneNumbers.PATTERN) String phone) {}

    record CompleteRegistrationRequest(@NotNull UUID challengeId, @NotNull @Pattern(regexp = OTP) String otp,
            @NotEmpty String password, @NotNull @Size(min = 1, max = 128) String fullName,
            @Size(max = 16) String termsVersion, @NotNull @Size(min = 1, max = 256) String deviceFingerprint) {}

    record LoginRequest(@NotNull @Pattern(regexp = PhoneNumbers.PATTERN) String phone, @NotEmpty String password,
            @NotNull @Size(min = 1, max = 256) String deviceFingerprint) {}

    record VerifyLoginRequest(@NotNull UUID challengeId, @NotNull @Pattern(regexp = OTP) String otp,
            @NotNull @Size(min = 1, max = 256) String deviceFingerprint) {}

    record RefreshRequest(@NotEmpty String refreshToken) {}

    record CompletePasswordResetRequest(@NotNull UUID challengeId, @NotNull @Pattern(regexp = OTP) String otp,
            @NotEmpty String newPassword) {}

    private final AuthService auth;
    private final SessionService sessions;

    AuthController(AuthService auth, SessionService sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ChallengeService.View startRegistration(@Valid @RequestBody PhoneRequest body, HttpServletRequest request) {
        return auth.startRegistration(body.phone(), client(request));
    }

    @PostMapping("/register/verify")
    @ResponseStatus(HttpStatus.CREATED)
    AuthSession completeRegistration(@Valid @RequestBody CompleteRegistrationRequest body,
            HttpServletRequest request) {
        return auth.completeRegistration(body.challengeId(), body.otp(), body.password(), body.fullName(),
                body.termsVersion(), body.deviceFingerprint(), client(request));
    }

    @PostMapping("/login")
    AuthSession login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return auth.login(body.phone(), body.password(), body.deviceFingerprint(), client(request));
    }

    @PostMapping("/login/verify")
    AuthSession verifyLogin(@Valid @RequestBody VerifyLoginRequest body, HttpServletRequest request) {
        return auth.verifyLogin(body.challengeId(), body.otp(), body.deviceFingerprint(), client(request));
    }

    @PostMapping("/refresh")
    AuthSession refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        return sessions.refresh(body.refreshToken(), client(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal Jwt jwt) {
        sessions.logout(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")));
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.CREATED)
    ChallengeService.View startPasswordReset(@Valid @RequestBody PhoneRequest body, HttpServletRequest request) {
        return auth.startPasswordReset(body.phone(), client(request));
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void completePasswordReset(@Valid @RequestBody CompletePasswordResetRequest body, HttpServletRequest request) {
        auth.completePasswordReset(body.challengeId(), body.otp(), body.newPassword(), client(request));
    }

    /** IP lấy sau khi Tomcat đã xử lý X-Forwarded-For của gateway (server.forward-headers-strategy=native). */
    static Client client(HttpServletRequest request) {
        return new Client(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
