package com.ewallet.platform.core;

import com.ewallet.platform.core.provider.FakeProviderGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class CoreAutoConfiguration {

    // static: BeanPostProcessor phải được tạo sớm, không kéo theo việc khởi tạo sớm class cấu hình
    @Bean
    static FakeProviderGuard fakeProviderGuard(Environment environment) {
        return new FakeProviderGuard(environment);
    }
}
