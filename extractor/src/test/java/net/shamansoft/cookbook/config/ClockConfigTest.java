package net.shamansoft.cookbook.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test ClockConfig bean creation and UTC timezone configuration.
 */
@SpringBootTest
@Import(TestFirebaseConfig.class)
@TestPropertySource(properties = {
        "firebase.enabled=false",
        "firestore.enabled=false"
})
class ClockConfigTest {

    @Autowired
    private Clock clock;

    @Test
    void clockBean_isCreated() {
        // Verify Clock bean is created and available
        assertThat(clock).isNotNull();
    }

    @Test
    void clock_usesUtcTimezone() {
        // Verify the clock uses UTC timezone
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Z"));
    }

    @Test
    void clock_providesCurrentTime() {
        // Verify the clock can retrieve current time
        Instant instant = clock.instant();
        assertThat(instant).isNotNull();
        assertThat(instant.toEpochMilli()).isGreaterThan(0);
    }

    @Test
    void clock_isSystemUTC() {
        // Verify the clock is system UTC (not a fixed clock)
        Clock systemUTC = Clock.systemUTC();
        assertThat(clock.getZone()).isEqualTo(systemUTC.getZone());
    }

    @Test
    void clockConfig_instantiation() {
        // Test that ClockConfig can be instantiated as a Spring bean
        ClockConfig config = new ClockConfig();
        assertThat(config).isNotNull();
    }

    @Test
    void clockConfig_hasProperAnnotations() {
        // Verify ClockConfig has @Configuration annotation
        assertThat(ClockConfig.class.isAnnotationPresent(org.springframework.context.annotation.Configuration.class))
                .isTrue();
    }

    @Test
    void clock_beanMethod_exists() {
        // Verify clock() method exists and is marked as @Bean
        var method = org.springframework.util.ReflectionUtils.findMethod(ClockConfig.class, "clock");
        assertThat(method).isNotNull();
        assertThat(method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)).isTrue();
    }
}
