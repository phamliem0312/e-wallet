package com.ewallet.platform.web;

import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Mọi service có {@code spring-boot-starter-oauth2-resource-server} tự kiểm lại JWT của khách (design §10).
 * {@code /internal/**} không cần JWT: chỉ đi trong mesh (mTLS + AuthorizationPolicy), không bao giờ qua gateway.
 * Đường dẫn công khai khác khai báo ở {@code ewallet.security.public-paths}. Lỗi xác thực trả dạng Problem.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ BearerTokenAuthenticationToken.class, SecurityFilterChain.class })
public class ResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain ewalletSecurityFilterChain(HttpSecurity http, ObjectMapper json,
            @Value("${ewallet.security.public-paths:}") String[] publicPaths) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, e) -> write(json, request, response,
                isExpired(e) ? CommonErrorCode.TOKEN_EXPIRED : CommonErrorCode.UNAUTHENTICATED);
        return http
                // API không dùng cookie: CSRF được chặn ở BFF, nơi giữ cookie (F-ACC-01.FR-30)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers("/internal/**", "/actuator/health/**", "/error").permitAll();
                    if (publicPaths.length > 0) a.requestMatchers(publicPaths).permitAll();
                    a.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(x -> x
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, e) ->
                                write(json, request, response, CommonErrorCode.FORBIDDEN)))
                .build();
    }

    private static boolean isExpired(AuthenticationException e) {
        return e.getCause() instanceof JwtValidationException invalid && invalid.getErrors().stream()
                .anyMatch(error -> error.getDescription() != null && error.getDescription().contains("expired"));
    }

    private static void write(ObjectMapper json, HttpServletRequest request, HttpServletResponse response,
            ErrorCode code) throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(),
                ProblemExceptionHandler.problem(code, request.getHeader("traceparent")));
    }
}
