package net.shamansoft.cookbook.entitlement;

import net.shamansoft.cookbook.config.TestFirebaseConfig;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.html.HtmlCleaner;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.html.HtmlFetcher;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RecipeService;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.StorageService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test that verifies the AOP aspect ({@link EntitlementAspect}),
 * response advice ({@link EntitlementResponseAdvice}), and exception handler
 * ({@link net.shamansoft.cookbook.CookbookExceptionHandler}) all work together
 * within a real Spring Boot context.
 *
 * <p>Catches proxy-bypass issues and missing bean wiring that pure unit tests cannot detect.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestFirebaseConfig.class)
class EntitlementEndToEndTest {

    private static final String RECIPE_PATH = "/v1/recipes";

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private EntitlementService entitlementService;

    @MockitoBean
    private RecipeService recipeService;

    // Mocks required to prevent startup failures for beans not needed in these tests
    @MockitoBean
    private HtmlExtractor htmlExtractor;
    @MockitoBean
    private HtmlCleaner htmlCleaner;
    @MockitoBean
    private Transformer transformer;
    @MockitoBean
    private DriveService driveService;
    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private RecipeStoreService recipeStoreService;
    @MockitoBean
    private ContentHashService contentHashService;
    @MockitoBean
    private UserProfileService userProfileService;
    @MockitoBean
    private StorageService storageService;
    @MockitoBean
    private Compressor compressor;
    @MockitoBean
    private HtmlFetcher htmlFetcher;
    @MockitoBean
    private RecipeValidationService recipeValidationService;

    @BeforeEach
    void setUp() {
        // Default: entitlement allowed (prevents unexpected 429s in non-entitlement tests)
        when(entitlementService.check(anyString(), any(), any()))
                .thenReturn(EntitlementResult.paid());
    }

    private HttpHeaders headersWithFirebaseToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-firebase-token");
        return headers;
    }

    @Test
    void postRecipe_quotaExhausted_returns429WithRetryAfterHeader() {
        Instant resetsAt = Instant.parse("2026-03-20T00:00:00Z");
        EntitlementResult denied = new EntitlementResult(
                false, EntitlementOutcome.DENIED_QUOTA, 0, null, resetsAt);
        when(entitlementService.check(anyString(), any(), any())).thenReturn(denied);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("url", "http://example.com", "title", "Test"),
                headersWithFirebaseToken()
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(RECIPE_PATH, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(response.getBody()).containsEntry("error", "QUOTA_EXCEEDED");
    }

    @Test
    void postRecipe_quotaAllowed_returnsQuotaHeaders() throws Exception {
        Instant resetsAt = Instant.parse("2026-03-20T00:00:00Z");
        EntitlementResult allowed = new EntitlementResult(
                true, EntitlementOutcome.ALLOWED_FREE_QUOTA, 4, null, resetsAt);
        when(entitlementService.check(anyString(), any(), any())).thenReturn(allowed);
        when(recipeService.createRecipe(anyString(), anyString(), any(), any(), any()))
                .thenReturn(RecipeResponse.builder()
                        .url("http://example.com")
                        .title("Test")
                        .isRecipe(true)
                        .build());

        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("url", "http://example.com", "title", "Test"),
                headersWithFirebaseToken()
        );

        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                RECIPE_PATH, request, RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Quota-Outcome")).isEqualTo("ALLOWED_FREE_QUOTA");
        assertThat(response.getHeaders().getFirst("X-Quota-Remaining")).isEqualTo("4");
    }
}
