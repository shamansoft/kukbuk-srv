package net.shamansoft.cookbook.entitlement;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration class for the entitlement system.
 * Registers EntitlementPlanConfig as a configuration properties bean.
 * (Records cannot use @Component, so @EnableConfigurationProperties is required.)
 */
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(EntitlementPlanConfig.class)
public class EntitlementConfig {
}
