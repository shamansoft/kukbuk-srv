package net.shamansoft.cookbook;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@Slf4j
public class ServiceConfig {

    @Bean
    public Client geminiClient(@Value("${cookbook.gemini.api-key}") String apiKey) {
        log.debug("Creating Gemini client with API key {}", apiKey);
        return Client.builder().apiKey(apiKey).build();
    }

    @Bean
    ExchangeFilterFunction loggingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(
                clientRequest -> {
                    log.info("Request: {} {}", clientRequest.method(), hideKey(clientRequest));
                    return Mono.just(clientRequest);
                });
    }

    static String hideKey(ClientRequest clientRequest) {
        return clientRequest.url().getPath()
                + "?"
                + clientRequest.url()
                .getQuery()
                .replaceAll("key=([^&]{2})[^&]+", "key=$1***");
    }

    @Bean
    public WebClient geminiWebClient(
            @Value("${cookbook.gemini.base-url}") String baseUrl,
            ExchangeFilterFunction loggingFilter) {
        return WebClient.builder()
                .filter(loggingFilter)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        return new InMemoryHttpExchangeRepository();
    }

}
