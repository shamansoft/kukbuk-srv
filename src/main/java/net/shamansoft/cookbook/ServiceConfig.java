package net.shamansoft.cookbook;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Slf4j
public class ServiceConfig {

    @Bean
    public Client geminiClient(@Value("${cookbook.gemini.api-key}") String apiKey) {
        log.debug("Creating Gemini client with api-key={}", apiKey);
        Client client = Client.builder().apiKey(apiKey).build();
        log.debug("Gemini client created: {}", client);
        return client;
    }

    @Bean
    public WebClient geminiWebClient(@Value("${cookbook.gemini.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
//                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

}
