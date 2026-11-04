package com.ewallet.platform.core.provider;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Khởi động thất bại nếu một bean {@link FakeProvider} được tạo khi profile {@code prod} đang bật. */
public class FakeProviderGuard implements BeanPostProcessor {

    private final Environment environment;

    public FakeProviderGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (AnnotationUtils.findAnnotation(bean.getClass(), FakeProvider.class) != null
                && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "Fake provider '" + beanName + "' must not be used with profile 'prod'");
        }
        return bean;
    }
}
