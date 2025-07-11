package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashServiceBenchmarkTest {

    private ContentHashService contentHashService;
    private final Random random = new Random();

    @BeforeEach
    void setUp() {
        contentHashService = new ContentHashService();
    }

    @Test
    @DisplayName("Should generate hash for typical recipe URL in under 50ms")
    void shouldGenerateHashForTypicalRecipeUrlInUnder50ms() {
        String typicalRecipeUrl = "https://www.allrecipes.com/recipe/213742/cheesy-chicken-broccoli-casserole/?utm_source=google&utm_medium=cpc&utm_campaign=recipe";
        
        long startTime = System.nanoTime();
        String hash = contentHashService.generateContentHash(typicalRecipeUrl);
        long endTime = System.nanoTime();
        
        long durationMs = (endTime - startTime) / 1_000_000;

        assertThat(hash).isNotNull();
        assertThat(durationMs)
                .as("Hash generation took %dms, which exceeds the 50ms requirement", durationMs)
                .isLessThan(50L);
    }

    @Test
    @DisplayName("Should generate hashes for multiple URLs within performance requirements")
    void shouldGenerateHashesForMultipleUrlsWithinPerformanceRequirements() {
        List<String> urls = generateTestUrls(100);
        
        long startTime = System.nanoTime();
        List<String> hashes = new ArrayList<>();
        
        for (String url : urls) {
            hashes.add(contentHashService.generateContentHash(url));
        }
        
        long endTime = System.nanoTime();
        long totalDurationMs = (endTime - startTime) / 1_000_000;
        double avgDurationMs = (double) totalDurationMs / urls.size();

        assertThat(hashes).hasSameSizeAs(urls);
        assertThat(avgDurationMs)
                .as("Average hash generation took %.2fms, which exceeds the 50ms requirement", avgDurationMs)
                .isLessThan(50.0);
    }

    @RepeatedTest(10)
    @DisplayName("Should consistently perform under 50ms across multiple runs")
    void shouldConsistentlyPerformUnder50msAcrossMultipleRuns() {
        String url = "https://example.com/recipe/" + random.nextInt(1000) + "?param=value&utm_source=test";
        
        long startTime = System.nanoTime();
        String hash = contentHashService.generateContentHash(url);
        long endTime = System.nanoTime();
        
        long durationMs = (endTime - startTime) / 1_000_000;

        assertThat(hash).isNotNull();
        assertThat(durationMs)
                .as("Hash generation took %dms in repeated test", durationMs)
                .isLessThan(50L);
    }

    @Test
    @DisplayName("Should handle concurrent hash generation efficiently")
    void shouldHandleConcurrentHashGenerationEfficiently() throws InterruptedException {
        List<String> urls = generateTestUrls(50);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        long startTime = System.nanoTime();
        
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String url : urls) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> 
                contentHashService.generateContentHash(url), executor);
            futures.add(future);
        }
        
        List<String> hashes = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            hashes.add(future.join());
        }
        
        long endTime = System.nanoTime();
        long totalDurationMs = (endTime - startTime) / 1_000_000;
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(hashes).hasSameSizeAs(urls);
        assertThat(totalDurationMs)
                .as("Concurrent hash generation took %dms for %d URLs", totalDurationMs, urls.size())
                .isLessThan(2500L);
    }

    @Test
    @DisplayName("Should demonstrate cache performance benefits")
    void shouldDemonstrateCachePerformanceBenefits() {
        String url = "https://example.com/recipe?utm_source=test&param=value";
        
        // First call - no cache (measure in nanoseconds for better precision)
        long startTime1 = System.nanoTime();
        String hash1 = contentHashService.generateContentHash(url);
        long endTime1 = System.nanoTime();
        long firstCallDuration = endTime1 - startTime1;
        
        // Second call - with cache
        long startTime2 = System.nanoTime();
        String hash2 = contentHashService.generateContentHash(url);
        long endTime2 = System.nanoTime();
        long secondCallDuration = endTime2 - startTime2;

        assertThat(hash1).isEqualTo(hash2);
        assertThat(firstCallDuration)
                .as("First call took %dms", firstCallDuration / 1_000_000)
                .isLessThan(50_000_000L);
        assertThat(secondCallDuration)
                .as("Cache should not be slower than first call: first=%dns, second=%dns", firstCallDuration, secondCallDuration)
                .isLessThanOrEqualTo(firstCallDuration);
    }

    @Test
    @DisplayName("Should handle large URLs efficiently")
    void shouldHandleLargeUrlsEfficiently() {
        StringBuilder largeUrl = new StringBuilder("https://example.com/recipe?");
        
        // Create a URL with many parameters
        for (int i = 0; i < 100; i++) {
            if (i > 0) largeUrl.append("&");
            largeUrl.append("param").append(i).append("=value").append(i);
        }
        
        // Add some tracking parameters
        largeUrl.append("&utm_source=test&utm_medium=cpc&fbclid=123456789");
        
        long startTime = System.nanoTime();
        String hash = contentHashService.generateContentHash(largeUrl.toString());
        long endTime = System.nanoTime();
        
        long durationMs = (endTime - startTime) / 1_000_000;

        assertThat(hash).isNotNull();
        assertThat(durationMs)
                .as("Large URL hash generation took %dms", durationMs)
                .isLessThan(50L);
    }

    @Test
    @DisplayName("Should maintain performance with various URL patterns")
    void shouldMaintainPerformanceWithVariousUrlPatterns() {
        List<String> urlPatterns = List.of(
            "https://example.com/recipe",
            "https://example.com/recipe?utm_source=google",
            "https://example.com/recipe/very-long-recipe-name-with-many-words",
            "https://subdomain.example.com/recipe?category=dessert&difficulty=easy",
            "https://example.com:8080/recipe#section1",
            "https://example.com/recipe?param1=value1&param2=value2&utm_medium=cpc",
            "https://example.com/recipe/path/with/many/segments?query=test"
        );
        
        for (String urlPattern : urlPatterns) {
            long startTime = System.nanoTime();
            String hash = contentHashService.generateContentHash(urlPattern);
            long endTime = System.nanoTime();
            
            long durationMs = (endTime - startTime) / 1_000_000;

            assertThat(hash).isNotNull();
            assertThat(durationMs)
                    .as("URL pattern '%s' took %dms", urlPattern, durationMs)
                    .isLessThan(50L);
        }
    }

    private List<String> generateTestUrls(int count) {
        List<String> urls = new ArrayList<>();
        String[] domains = {"example.com", "test.com", "recipes.com", "food.com"};
        String[] paths = {"recipe", "food", "cooking", "meal"};
        String[] trackingParams = {"utm_source=google", "fbclid=123", "gclid=456", "ref=twitter"};
        
        for (int i = 0; i < count; i++) {
            String domain = domains[random.nextInt(domains.length)];
            String path = paths[random.nextInt(paths.length)];
            String trackingParam = trackingParams[random.nextInt(trackingParams.length)];
            
            String url = "https://" + domain + "/" + path + "/" + i + "?" + trackingParam + "&param=value" + i;
            urls.add(url);
        }
        
        return urls;
    }
}