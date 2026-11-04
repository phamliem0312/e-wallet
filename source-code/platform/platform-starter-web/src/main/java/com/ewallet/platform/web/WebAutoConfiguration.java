package com.ewallet.platform.web;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebAutoConfiguration {

    @Bean
    ProblemExceptionHandler problemExceptionHandler() {
        return new ProblemExceptionHandler();
    }

    @Bean
    WebMvcConfigurer idempotencyKeyConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new IdempotencyKeyResolver());
            }
        };
    }
}
