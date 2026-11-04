package com.ewallet.platform.web;

import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.ErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import com.ewallet.platform.core.error.PlatformException.FieldError;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Mọi lỗi trả về là Problem (RFC 9457) có {@code code} và {@code traceId}, khớp schema {@code Problem} trong common.yaml.
 * Lỗi không lường trước trả 500 {@code INTERNAL_ERROR} và không để lộ nội dung exception.
 */
@RestControllerAdvice
public class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemExceptionHandler.class);

    @ExceptionHandler(PlatformException.class)
    ResponseEntity<Object> handlePlatform(PlatformException e, WebRequest request) {
        ProblemDetail pd = problem(e.errorCode(), request);
        if (!e.params().isEmpty()) pd.setProperty("params", e.params());
        if (!e.errors().isEmpty()) pd.setProperty("errors", e.errors());
        return ResponseEntity.status(e.errorCode().httpStatus()).body(pd);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception e, WebRequest request) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(500).body(problem(CommonErrorCode.INTERNAL_ERROR, request));
    }

    /** Lỗi của chính Spring MVC (sai kiểu, thiếu tham số, 404, 405...) cũng phải ra dạng Problem của dự án. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            ErrorCode code = status.value() == 404 ? CommonErrorCode.NOT_FOUND
                    : status.is5xxServerError() ? CommonErrorCode.INTERNAL_ERROR : CommonErrorCode.VALIDATION_ERROR;
            pd.setType(typeUri(code));
            pd.setProperty("code", code.code());
            pd.setProperty("traceId", traceId(request));
            if (ex instanceof MethodArgumentNotValidException invalid) {
                List<FieldError> errors = invalid.getBindingResult().getFieldErrors().stream()
                        .map(f -> new FieldError(f.getField(), String.valueOf(f.getCode()), f.getDefaultMessage()))
                        .toList();
                pd.setProperty("errors", errors);
            }
        }
        return response;
    }

    private ProblemDetail problem(ErrorCode code, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatus(code.httpStatus());
        pd.setType(typeUri(code));
        pd.setTitle(title(code));
        pd.setProperty("code", code.code());
        pd.setProperty("traceId", traceId(request));
        return pd;
    }

    private static URI typeUri(ErrorCode code) {
        return URI.create("https://ewallet/errors/" + code.code().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static String title(ErrorCode code) {
        String words = code.code().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** MDC (do observability starter đặt) > trace-id trong {@code traceparent} > id ngẫu nhiên. */
    static String traceId(WebRequest request) {
        String fromMdc = MDC.get("traceId");
        if (fromMdc != null) return fromMdc;
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length == 4 && parts[1].length() == 32) return parts[1];
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
