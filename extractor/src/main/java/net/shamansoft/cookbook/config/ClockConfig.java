package net.shamansoft.cookbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuration for time-related beans.
 * Provides Clock bean for consistent time handling across the application.
 */
@Configuration
public class ClockConfig {

    /**
     * Provides a Clock instance using UTC timezone.
     * This ensures consistent date/time values regardless of server location.
     *
     * @return Clock instance using UTC timezone
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
