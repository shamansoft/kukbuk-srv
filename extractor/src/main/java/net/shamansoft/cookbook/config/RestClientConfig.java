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

    @Bean
    public ClientHttpRequestFactory httpRequestFactory() {
        // Connection-level configuration (connect timeout)
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(2))
                .build();

        // Connection pooling
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);  // Total connections
        connectionManager.setDefaultMaxPerRoute(20);  // Per-host
        connectionManager.setDefaultConnectionConfig(connectionConfig);

        // Request-level configuration (response timeout, connection request timeout)
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    @Bean
    public RestClient geminiRestClient(
            @Value("${cookbook.gemini.base-url}") String baseUrl,
            ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
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
}
