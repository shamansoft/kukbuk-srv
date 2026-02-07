package net.shamansoft.cookbook.config;

import com.google.cloud.tasks.v2.CloudTasksClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@Slf4j
public class CloudTasksConfig {

    private CloudTasksClient cloudTasksClient;

    @Bean
    @ConditionalOnProperty(name = "cookbook.tasks.enabled", havingValue = "true", matchIfMissing = false)
    public CloudTasksClient cloudTasksClient() throws IOException {
        log.info("Initializing CloudTasksClient");
        this.cloudTasksClient = CloudTasksClient.create();
        return this.cloudTasksClient;
    }

    @PreDestroy
    public void cleanup() {
        if (cloudTasksClient != null) {
            log.info("Closing CloudTasksClient");
            cloudTasksClient.close();
        }
    }
}
