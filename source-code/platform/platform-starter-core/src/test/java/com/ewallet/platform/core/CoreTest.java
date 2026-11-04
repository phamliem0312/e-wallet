package com.ewallet.platform.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewallet.platform.core.provider.FakeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CoreTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void moneyRejectsNegativeAndOverflowInsteadOfWrapping() {
        assertThatThrownBy(() -> Money.of(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(100).minus(Money.of(101))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE).plus(Money.of(1))).isInstanceOf(ArithmeticException.class);
        assertThat(Money.of(1_000).plus(Money.of(500)).minus(Money.of(1_500))).isEqualTo(Money.ZERO);
    }

    @Test
    void moneyIsABareIntegerInJson() throws Exception {
        assertThat(json.writeValueAsString(Money.of(100_000))).isEqualTo("100000");
        assertThat(json.readValue("100000", Money.class)).isEqualTo(Money.of(100_000));
        assertThatThrownBy(() -> json.readValue("-5", Money.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uuidV7HasVersionAndVariantAndSortsByTime() throws Exception {
        UUID first = Ids.uuidV7();
        Thread.sleep(2);
        UUID second = Ids.uuidV7();
        assertThat(first.version()).isEqualTo(7);
        assertThat(first.variant()).isEqualTo(2);
        assertThat(second.getMostSignificantBits() >>> 16).isGreaterThan(first.getMostSignificantBits() >>> 16);
    }

    @Test
    void sensitiveNeverPrintsItsValue() {
        assertThat(new Sensitive<>("123456").toString()).doesNotContain("123456");
        assertThat("otp=" + new Sensitive<>("123456")).isEqualTo("otp=***");
    }

    @FakeProvider
    static class FakeBank {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class))
            .withBean(FakeBank.class);

    @Test
    void fakeProviderIsAllowedOutsideProd() {
        runner.withInitializer(c -> c.getEnvironment().setActiveProfiles("dev"))
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void fakeProviderBlocksStartupInProd() {
        runner.withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("must not be used with profile 'prod'"));
    }
}
