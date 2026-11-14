package net.shamansoft.cookbook.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@Slf4j
public class RestClientConfig {

    // Logging interceptor (replaces WebClient ExchangeFilterFunction)
    private static String hideKey(String uri) {
        return uri.replaceAll("key=([^&]{2})[^&]+", "key=$1***");
    }

    /** A pooled Apache HTTP client factory. */
    private static HttpComponentsClientHttpRequestFactory pooledFactory(int maxTotal, int maxPerRoute,
                                                                        int connectSeconds, int responseSeconds) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(maxPerRoute);
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(connectSeconds))
                .build());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(connectSeconds))
                .setResponseTimeout(Timeout.ofSeconds(responseSeconds))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    @Bean
    @Primary
    public ClientHttpRequestFactory httpRequestFactory() {
        return pooledFactory(200, 20, 2, 30);
    }

    @Bean
    public ClientHttpRequestFactory geminiHttpRequestFactory(
            @Value("${cookbook.gemini.timeout-seconds:90}") int timeoutSeconds) {
        return pooledFactory(50, 10, 5, timeoutSeconds);
    }

    @Bean
    public RestClient geminiRestClient(
            @Value("${cookbook.gemini.base-url}") String baseUrl,
            ClientHttpRequestFactory geminiHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(geminiHttpRequestFactory)
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    log.info("Request: {} {}", request.getMethod(),
                            hideKey(request.getURI().toString()));
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public RestClient authRestClient(
            @Value("${cookbook.drive.auth-url}") String authUrl,
            ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(authUrl)
                .build();
    }

    @Bean
    public RestClient driveRestClient(
            @Value("${cookbook.drive.base-url}") String baseUrl,
            ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public RestClient uploadRestClient(
            @Value("${cookbook.drive.upload-url}") String uploadUrl,
            ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(uploadUrl)
                .build();
    }

    @Bean
    public RestClient genericRestClient(ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /** TypeSafe (Jev) API. Short timeout: the page screen fails open, so a slow answer only delays extraction. */
    @Bean
    public RestClient typesafeRestClient(
            @Value("${cookbook.typesafe.base-url:https://api.typesafe.ai}") String baseUrl,
            @Value("${cookbook.typesafe.api-key:}") String apiKey,
            @Value("${cookbook.typesafe.timeout-seconds:5}") int timeoutSeconds) {
        return RestClient.builder()
                .requestFactory(pooledFactory(50, 20, 2, timeoutSeconds))
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Bean
    public RestClient markdownNewRestClient(ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://markdown.new")
                .build();
    }
}
