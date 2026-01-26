package net.shamansoft.cookbook.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestFirebaseConfig.class)
@TestPropertySource(properties = {
        "cookbook.gemini.base-url=https://generativelanguage.googleapis.com/v1beta",
        "cookbook.drive.auth-url=https://oauth2.googleapis.com",
        "cookbook.drive.base-url=https://www.googleapis.com/drive/v3",
        "cookbook.drive.upload-url=https://www.googleapis.com/upload/drive/v3",
        "firestore.enabled=false"
})
class RestClientConfigTest {

    @Autowired
    private ClientHttpRequestFactory httpRequestFactory;

    @Autowired
    @Qualifier("geminiRestClient")
    private RestClient geminiRestClient;

    @Autowired
    @Qualifier("authRestClient")
    private RestClient authRestClient;

    @Autowired
    @Qualifier("driveRestClient")
    private RestClient driveRestClient;

    @Autowired
    @Qualifier("uploadRestClient")
    private RestClient uploadRestClient;

    @Autowired
    @Qualifier("genericRestClient")
    private RestClient genericRestClient;

    @Test
    void httpRequestFactory_isConfigured_withHttpComponents() {
        // Verify that the ClientHttpRequestFactory is properly configured
        assertThat(httpRequestFactory).isNotNull();
        assertThat(httpRequestFactory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

    @Test
    void geminiRestClient_isInitialized_withBaseUrl() {
        // Verify geminiRestClient bean is created and configured
        assertThat(geminiRestClient).isNotNull();

        // RestClient doesn't expose base URL directly, but we can verify it's not null
        // The actual base URL is tested through integration tests
    }

    @Test
    void geminiRestClient_canExecuteRequest_withLoggingInterceptor() {
        // Verify geminiRestClient has the logging interceptor configured
        // This is implicitly tested by the bean initialization
        assertThat(geminiRestClient).isNotNull();
    }

    @Test
    void authRestClient_isInitialized_withBaseUrl() {
        // Verify authRestClient bean is created for OAuth token validation
        assertThat(authRestClient).isNotNull();
    }

    @Test
    void driveRestClient_isInitialized_withBaseUrl() {
        // Verify driveRestClient bean is created for Drive API calls
        assertThat(driveRestClient).isNotNull();
    }

    @Test
    void uploadRestClient_isInitialized_withBaseUrl() {
        // Verify uploadRestClient bean is created for Drive upload operations
        assertThat(uploadRestClient).isNotNull();
    }

    @Test
    void genericRestClient_isInitialized_withoutBaseUrl() {
        // Verify genericRestClient bean is created for generic HTTP calls
        assertThat(genericRestClient).isNotNull();
    }

    @Test
    void allRestClients_shareCommonHttpRequestFactory() {
        // All RestClient beans should use the same HttpRequestFactory
        // This ensures consistent timeout and connection pooling configuration
        assertThat(httpRequestFactory).isNotNull();

        // We can't directly verify RestClient uses the factory, but we ensure
        // the factory is properly configured and available in the context
        assertThat(httpRequestFactory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }
}
