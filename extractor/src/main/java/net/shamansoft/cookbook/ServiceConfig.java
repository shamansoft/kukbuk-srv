package net.shamansoft.cookbook;


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


    // Hide API key in URL query (keeps first 2 chars)
    // We avoid referencing ClientRequest in a method signature to prevent
    // classloading issues when reactive classes are not present at runtime.

    // shows first 3 chars of the key and last 3 chars, e.g. "abc***xyz"
    static String hideKey(String apiKey) {
        if (apiKey.length() < 6) {
            return apiKey;
        }
        return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
    }

    @Bean
    ExchangeFilterFunction loggingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(
                clientRequest -> {
                    // build masked path+query without using ClientRequest in other signatures
                    String path = clientRequest.url().getPath();
                    String query = clientRequest.url().getQuery();
                    String masked = "";
                    if (query != null) {
                        masked = query.replaceAll("key=([^&]{2})[^&]+", "key=$1***");
                        masked = "?" + masked;
                    }
                    log.info("Request: {} {}", clientRequest.method(), path + masked);
                    return Mono.just(clientRequest);
                });
    }

    @Bean
    public WebClient geminiWebClient(@Value("${cookbook.gemini.base-url}") String baseUrl,
                                     ExchangeFilterFunction loggingFilter) {
        return WebClient.builder()
                .filter(loggingFilter)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public WebClient authWebClient(@Value("${cookbook.drive.auth-url}") String authUrl) {
        return WebClient.builder()
                .baseUrl(authUrl)
                .build();
    }

    @Bean
    public WebClient driveWebClient(@Value("${cookbook.drive.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public WebClient uploadWebClient(@Value("${cookbook.drive.upload-url}") String uploadUrl) {
        return WebClient.builder()
                .baseUrl(uploadUrl)
                .build();
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        return new InMemoryHttpExchangeRepository();
    }

}
