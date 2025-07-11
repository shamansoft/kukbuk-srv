package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentHashServiceTest {

    private ContentHashService contentHashService;

    @BeforeEach
    void setUp() {
        contentHashService = new ContentHashService();
    }

    @Test
    @DisplayName("Should generate consistent hashes for identical URLs")
    void shouldGenerateConsistentHashesForIdenticalUrls() {
        String url = "https://example.com/recipe";
        String hash1 = contentHashService.generateContentHash(url);
        String hash2 = contentHashService.generateContentHash(url);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 character hex string
    }

    @Test
    @DisplayName("Should generate different hashes for different URLs")
    void shouldGenerateDifferentHashesForDifferentUrls() {
        String url1 = "https://example.com/recipe1";
        String url2 = "https://example.com/recipe2";
        
        String hash1 = contentHashService.generateContentHash(url1);
        String hash2 = contentHashService.generateContentHash(url2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should normalize URLs by removing tracking parameters")
    void shouldNormalizeUrlsByRemovingTrackingParameters() throws URISyntaxException {
        String originalUrl = "https://example.com/recipe?utm_source=google&utm_medium=cpc&keep=this";
        String expected = "https://example.com/recipe?keep=this";
        
        String normalized = contentHashService.normalizeUrl(originalUrl);

        assertThat(normalized).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should generate identical hashes for URLs with and without tracking parameters")
    void shouldGenerateIdenticalHashesForUrlsWithAndWithoutTrackingParameters() {
        String cleanUrl = "https://example.com/recipe?category=dessert";
        String urlWithTracking = "https://example.com/recipe?category=dessert&utm_source=google&fbclid=123";
        
        String hash1 = contentHashService.generateContentHash(cleanUrl);
        String hash2 = contentHashService.generateContentHash(urlWithTracking);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should remove all supported tracking parameters")
    void shouldRemoveAllSupportedTrackingParameters() throws URISyntaxException {
        String urlWithAllTracking = "https://example.com/recipe?utm_source=google&utm_medium=cpc&utm_campaign=test&utm_term=keyword&utm_content=content&fbclid=123&gclid=456&dclid=789&msclkid=abc&twclid=def&ref=twitter&source=facebook&keep=this";
        String expected = "https://example.com/recipe?keep=this";
        
        String normalized = contentHashService.normalizeUrl(urlWithAllTracking);

        assertThat(normalized).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should handle URLs without query parameters")
    void shouldHandleUrlsWithoutQueryParameters() throws URISyntaxException {
        String url = "https://example.com/recipe";
        String normalized = contentHashService.normalizeUrl(url);

        assertThat(normalized).isEqualTo(url);
    }

    @Test
    @DisplayName("Should handle URLs with only tracking parameters")
    void shouldHandleUrlsWithOnlyTrackingParameters() throws URISyntaxException {
        String urlWithOnlyTracking = "https://example.com/recipe?utm_source=google&fbclid=123";
        String expected = "https://example.com/recipe";
        
        String normalized = contentHashService.normalizeUrl(urlWithOnlyTracking);

        assertThat(normalized).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should normalize domain and scheme to lowercase")
    void shouldNormalizeDomainAndSchemeToLowercase() throws URISyntaxException {
        String url = "HTTPS://EXAMPLE.COM/Recipe";
        String normalized = contentHashService.normalizeUrl(url);

        assertThat(normalized.startsWith("https://example.com")).isTrue();
    }

    @Test
    @DisplayName("Should remove fragment from URL")
    void shouldRemoveFragmentFromUrl() throws URISyntaxException {
        String url = "https://example.com/recipe#section1";
        String expected = "https://example.com/recipe";
        
        String normalized = contentHashService.normalizeUrl(url);

        assertThat(normalized).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should handle malformed URLs gracefully")
    void shouldHandleMalformedUrlsGracefully() {
        assertThatThrownBy(() -> {
            contentHashService.generateContentHash("ht tp://invalid url with spaces");
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should throw exception for null URL")
    void shouldThrowExceptionForNullUrl() {
        assertThatThrownBy(() -> {
            contentHashService.generateContentHash(null);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw exception for empty URL")
    void shouldThrowExceptionForEmptyUrl() {
        assertThatThrownBy(() -> {
            contentHashService.generateContentHash("");
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw exception for whitespace-only URL")
    void shouldThrowExceptionForWhitespaceOnlyUrl() {
        assertThatThrownBy(() -> {
            contentHashService.generateContentHash("   ");
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should preserve non-tracking query parameters")
    void shouldPreserveNonTrackingQueryParameters() throws URISyntaxException {
        String url = "https://example.com/recipe?category=dessert&difficulty=easy&utm_source=google";
        String normalized = contentHashService.normalizeUrl(url);

        assertThat(normalized.contains("category=dessert")).isTrue();
        assertThat(normalized.contains("difficulty=easy")).isTrue();
        assertThat(normalized.contains("utm_source=google")).isFalse();
    }

    @Test
    @DisplayName("Should handle URLs with port numbers")
    void shouldHandleUrlsWithPortNumbers() throws URISyntaxException {
        String url = "https://example.com:8080/recipe";
        String normalized = contentHashService.normalizeUrl(url);

        assertThat(normalized).isEqualTo("https://example.com:8080/recipe");
    }

    @Test
    @DisplayName("Should handle URLs with paths containing special characters")
    void shouldHandleUrlsWithPathsContainingSpecialCharacters() throws URISyntaxException {
        String url = "https://example.com/recipe/chocolate-chip-cookies";
        String normalized = contentHashService.normalizeUrl(url);

        assertThat(normalized).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/recipe1",
            "https://example.com/recipe2",
            "https://different-site.com/recipe",
            "https://example.com/recipe?param=value",
            "https://example.com/recipe/subpath"
    })
    @DisplayName("Should generate unique hashes for different URLs")
    void shouldGenerateUniqueHashesForDifferentUrls(String url) {
        String baseUrl = "https://example.com/recipe";
        String baseHash = contentHashService.generateContentHash(baseUrl);
        String testHash = contentHashService.generateContentHash(url);
        
        if (!url.equals(baseUrl)) {
            assertThat(baseHash).isNotEqualTo(testHash);
        }
    }

    @Test
    @DisplayName("Should use cache for repeated URL requests")
    void shouldUseCacheForRepeatedUrlRequests() {
        String url = "https://example.com/recipe";

        assertThat(contentHashService.getCacheSize()).isEqualTo(0);
        
        contentHashService.generateContentHash(url);
        assertThat(contentHashService.getCacheSize()).isEqualTo(1);
        
        contentHashService.generateContentHash(url);
        assertThat(contentHashService.getCacheSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should clear cache when requested")
    void shouldClearCacheWhenRequested() {
        String url = "https://example.com/recipe";
        
        contentHashService.generateContentHash(url);
        assertThat(contentHashService.getCacheSize()).isEqualTo(1);
        
        contentHashService.clearCache();
        assertThat(contentHashService.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle URLs with international domain names")
    void shouldHandleUrlsWithInternationalDomainNames() throws URISyntaxException {
        String url = "https://example.com/recipe/café";
        String normalized = contentHashService.normalizeUrl(url);

        assertThat(normalized.startsWith("https://example.com")).isTrue();
    }

    @Test
    @DisplayName("Should be case-insensitive for tracking parameters")
    void shouldBeCaseInsensitiveForTrackingParameters() throws URISyntaxException {
        String url = "https://example.com/recipe?UTM_SOURCE=google&fbclid=123&keep=this";
        String normalized = contentHashService.normalizeUrl(url);

        assertThat(normalized.contains("UTM_SOURCE")).isFalse();
        assertThat(normalized.contains("fbclid")).isFalse();
        assertThat(normalized.contains("keep=this")).isTrue();
    }
}