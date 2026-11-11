package com.ewallet.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewallet.platform.core.error.ErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ProblemWebTest.App.class)
@AutoConfigureMockMvc
class ProblemWebTest {

    static final String KEY = "0193f2a4-7c1e-7b2a-9f00-1a2b3c4d5e01";

    enum TestCode implements ErrorCode {
        LIMIT_EXCEEDED;

        public String code() { return name(); }
        public int httpStatus() { return 422; }
    }

    record Transfer(@Min(1) long amount) {}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
        @RestController
        static class Api {
            @PostMapping("/transfer")
            Map<String, Object> transfer(IdempotencyKey key, @Valid @RequestBody Transfer body) {
                return Map.of("key", key.value().toString(), "amount", body.amount());
            }

            @GetMapping("/limit")
            void limit() {
                throw new PlatformException(TestCode.LIMIT_EXCEEDED, Map.of("remaining", 5_000));
            }

            @GetMapping("/slow-down")
            void slowDown() {
                throw new PlatformException(com.ewallet.platform.core.error.CommonErrorCode.RATE_LIMITED, Map.of("retryAfterSeconds", 30));
            }

            @GetMapping("/boom")
            void boom() {
                throw new IllegalStateException("jdbc:postgresql://secret-host/db password=hunter2");
            }
        }
    }

    @Autowired MockMvc mvc;

    @Test
    void validKeyAndBodyPasses() throws Exception {
        mvc.perform(post("/transfer").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(KEY));
    }

    @Test
    void missingKeyIsRejectedBeforeBodyIsValidated() throws Exception {
        // body cũng sai: lỗi phải là thiếu khoá, vì khoá được tra trước mọi kiểm tra khác
        mvc.perform(post("/transfer").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedKeyIsAValidationError() throws Exception {
        mvc.perform(post("/transfer").header("Idempotency-Key", "1-1-1-1-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    }

    @Test
    void bodyViolationListsFieldErrors() throws Exception {
        mvc.perform(post("/transfer").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void businessErrorCarriesStatusCodeAndParams() throws Exception {
        mvc.perform(get("/limit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.params.remaining").value(5_000))
                .andExpect(jsonPath("$.type").value("https://ewallet/errors/limit-exceeded"));
    }

    @Test
    void retryAfterSecondsParamBecomesRetryAfterHeader() throws Exception {
        mvc.perform(get("/slow-down"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void unexpectedExceptionDoesNotLeakItsMessage() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))));
    }

    @Test
    void springMvcErrorsAlsoUseTheProblemShape() throws Exception {
        mvc.perform(get("/does-not-exist").header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"));
    }
}
