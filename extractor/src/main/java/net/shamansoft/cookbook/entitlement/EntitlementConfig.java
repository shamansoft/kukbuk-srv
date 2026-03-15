package net.shamansoft.cookbook.entitlement;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the entitlement system.
 * Registers EntitlementPlanConfig as a configuration properties bean.
 * (Records cannot use @Component, so @EnableConfigurationProperties is required.)
 */
@Configuration
@EnableConfigurationProperties(EntitlementPlanConfig.class)
public class EntitlementConfig {
}
