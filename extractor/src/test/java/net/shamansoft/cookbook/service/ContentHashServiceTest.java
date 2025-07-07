package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

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
        
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 produces 64 character hex string
    }

    @Test
    @DisplayName("Should generate different hashes for different URLs")
    void shouldGenerateDifferentHashesForDifferentUrls() {
        String url1 = "https://example.com/recipe1";
        String url2 = "https://example.com/recipe2";
        
        String hash1 = contentHashService.generateContentHash(url1);
        String hash2 = contentHashService.generateContentHash(url2);
        
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Should normalize URLs by removing tracking parameters")
    void shouldNormalizeUrlsByRemovingTrackingParameters() throws URISyntaxException {
        String originalUrl = "https://example.com/recipe?utm_source=google&utm_medium=cpc&keep=this";
        String expected = "https://example.com/recipe?keep=this";
        
        String normalized = contentHashService.normalizeUrl(originalUrl);
        
        assertEquals(expected, normalized);
    }

    @Test
    @DisplayName("Should generate identical hashes for URLs with and without tracking parameters")
    void shouldGenerateIdenticalHashesForUrlsWithAndWithoutTrackingParameters() {
        String cleanUrl = "https://example.com/recipe?category=dessert";
        String urlWithTracking = "https://example.com/recipe?category=dessert&utm_source=google&fbclid=123";
        
        String hash1 = contentHashService.generateContentHash(cleanUrl);
        String hash2 = contentHashService.generateContentHash(urlWithTracking);
        
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Should remove all supported tracking parameters")
    void shouldRemoveAllSupportedTrackingParameters() throws URISyntaxException {
        String urlWithAllTracking = "https://example.com/recipe?utm_source=google&utm_medium=cpc&utm_campaign=test&utm_term=keyword&utm_content=content&fbclid=123&gclid=456&dclid=789&msclkid=abc&twclid=def&ref=twitter&source=facebook&keep=this";
        String expected = "https://example.com/recipe?keep=this";
        
        String normalized = contentHashService.normalizeUrl(urlWithAllTracking);
        
        assertEquals(expected, normalized);
    }

    @Test
    @DisplayName("Should handle URLs without query parameters")
    void shouldHandleUrlsWithoutQueryParameters() throws URISyntaxException {
        String url = "https://example.com/recipe";
        String normalized = contentHashService.normalizeUrl(url);
        
        assertEquals(url, normalized);
    }

    @Test
    @DisplayName("Should handle URLs with only tracking parameters")
    void shouldHandleUrlsWithOnlyTrackingParameters() throws URISyntaxException {
        String urlWithOnlyTracking = "https://example.com/recipe?utm_source=google&fbclid=123";
        String expected = "https://example.com/recipe";
        
        String normalized = contentHashService.normalizeUrl(urlWithOnlyTracking);
        
        assertEquals(expected, normalized);
    }

    @Test
    @DisplayName("Should normalize domain and scheme to lowercase")
    void shouldNormalizeDomainAndSchemeToLowercase() throws URISyntaxException {
        String url = "HTTPS://EXAMPLE.COM/Recipe";
        String normalized = contentHashService.normalizeUrl(url);
        
        assertTrue(normalized.startsWith("https://example.com"));
    }

    @Test
    @DisplayName("Should remove fragment from URL")
    void shouldRemoveFragmentFromUrl() throws URISyntaxException {
        String url = "https://example.com/recipe#section1";
        String expected = "https://example.com/recipe";
        
        String normalized = contentHashService.normalizeUrl(url);
        
        assertEquals(expected, normalized);
    }

    @Test
    @DisplayName("Should handle malformed URLs gracefully")
    void shouldHandleMalformedUrlsGracefully() {
        assertThrows(RuntimeException.class, () -> {
            contentHashService.generateContentHash("ht tp://invalid url with spaces");
        });
    }

    @Test
    @DisplayName("Should throw exception for null URL")
    void shouldThrowExceptionForNullUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            contentHashService.generateContentHash(null);
        });
    }

    @Test
    @DisplayName("Should throw exception for empty URL")
    void shouldThrowExceptionForEmptyUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            contentHashService.generateContentHash("");
        });
    }

    @Test
    @DisplayName("Should throw exception for whitespace-only URL")
    void shouldThrowExceptionForWhitespaceOnlyUrl() {
        assertThrows(IllegalArgumentException.class, () -> {
            contentHashService.generateContentHash("   ");
        });
    }

    @Test
    @DisplayName("Should preserve non-tracking query parameters")
    void shouldPreserveNonTrackingQueryParameters() throws URISyntaxException {
        String url = "https://example.com/recipe?category=dessert&difficulty=easy&utm_source=google";
        String normalized = contentHashService.normalizeUrl(url);
        
        assertTrue(normalized.contains("category=dessert"));
        assertTrue(normalized.contains("difficulty=easy"));
        assertFalse(normalized.contains("utm_source=google"));
    }

    @Test
    @DisplayName("Should handle URLs with port numbers")
    void shouldHandleUrlsWithPortNumbers() throws URISyntaxException {
        String url = "https://example.com:8080/recipe";
        String normalized = contentHashService.normalizeUrl(url);
        
        assertEquals("https://example.com:8080/recipe", normalized);
    }

    @Test
    @DisplayName("Should handle URLs with paths containing special characters")
    void shouldHandleUrlsWithPathsContainingSpecialCharacters() throws URISyntaxException {
        String url = "https://example.com/recipe/chocolate-chip-cookies";
        String normalized = contentHashService.normalizeUrl(url);
        
        assertEquals(url, normalized);
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
            assertNotEquals(baseHash, testHash);
        }
    }

    @Test
    @DisplayName("Should use cache for repeated URL requests")
    void shouldUseCacheForRepeatedUrlRequests() {
        String url = "https://example.com/recipe";
        
        assertEquals(0, contentHashService.getCacheSize());
        
        contentHashService.generateContentHash(url);
        assertEquals(1, contentHashService.getCacheSize());
        
        contentHashService.generateContentHash(url);
        assertEquals(1, contentHashService.getCacheSize());
    }

    @Test
    @DisplayName("Should clear cache when requested")
    void shouldClearCacheWhenRequested() {
        String url = "https://example.com/recipe";
        
        contentHashService.generateContentHash(url);
        assertEquals(1, contentHashService.getCacheSize());
        
        contentHashService.clearCache();
        assertEquals(0, contentHashService.getCacheSize());
    }

    @Test
    @DisplayName("Should handle URLs with international domain names")
    void shouldHandleUrlsWithInternationalDomainNames() throws URISyntaxException {
        String url = "https://example.com/recipe/café";
        String normalized = contentHashService.normalizeUrl(url);
        
        assertTrue(normalized.startsWith("https://example.com"));
    }

    @Test
    @DisplayName("Should be case-insensitive for tracking parameters")
    void shouldBeCaseInsensitiveForTrackingParameters() throws URISyntaxException {
        String url = "https://example.com/recipe?UTM_SOURCE=google&fbclid=123&keep=this";
        String normalized = contentHashService.normalizeUrl(url);
        
        assertFalse(normalized.contains("UTM_SOURCE"));
        assertFalse(normalized.contains("fbclid"));
        assertTrue(normalized.contains("keep=this"));
    }
}