package net.shamansoft.cookbook;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ServiceConfig {

    @Bean
    public Client geminiClient(@Value("${cookbook.gemini.api-key}") String apiKey) {
        log.debug("Creating Gemini client with api-key={}", apiKey);
        return Client.builder().apiKey(apiKey).build();
    }

}
