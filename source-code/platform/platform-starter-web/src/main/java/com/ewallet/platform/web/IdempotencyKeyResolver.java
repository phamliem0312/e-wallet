package com.ewallet.platform.web;

import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import com.ewallet.platform.core.error.PlatformException.FieldError;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class IdempotencyKeyResolver implements HandlerMethodArgumentResolver {

    static final String HEADER = "Idempotency-Key";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return IdempotencyKey.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            throw new PlatformException(CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        try {
            UUID uuid = UUID.fromString(header);
            // UUID.fromString chấp nhận cả "1-1-1-1-1"; chỉ nhận dạng chuẩn 8-4-4-4-12
            if (uuid.toString().equalsIgnoreCase(header)) return new IdempotencyKey(uuid);
        } catch (IllegalArgumentException ignored) {
            // rơi xuống lỗi bên dưới
        }
        throw new PlatformException(CommonErrorCode.VALIDATION_ERROR, Map.of(),
                List.of(new FieldError(HEADER, "INVALID_FORMAT", "must be a UUID")));
    }
}
