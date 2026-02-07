package net.shamansoft.cookbook.config;

import com.google.cloud.tasks.v2.CloudTasksClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@Slf4j
public class CloudTasksConfig {

    @Bean
    @ConditionalOnProperty(name = "cookbook.tasks.enabled", havingValue = "true", matchIfMissing = false)
    public CloudTasksClient cloudTasksClient() throws IOException {
        log.info("Initializing CloudTasksClient");
        return CloudTasksClient.create();
    }
}
