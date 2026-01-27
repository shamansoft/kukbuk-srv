package net.shamansoft.cookbook.html;

import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import net.shamansoft.cookbook.html.strategy.Strategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static net.shamansoft.cookbook.html.strategy.Strategy.CONTENT_FILTER;
import static net.shamansoft.cookbook.html.strategy.Strategy.FALLBACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HtmlCleanerTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private DistributionSummary summary;

    private HtmlCleanupConfig config;
    private ObjectMapper objectMapper;
    private HtmlCleaner htmlCleaner;

    @BeforeEach
    void setUp() {
        config = new HtmlCleanupConfig();

        // Configure test properties manually (matching application-test.yaml)
        config.setEnabled(true);

        // Structured data settings
        config.getStructuredData().setEnabled(true);
        config.getStructuredData().setMinCompleteness(70);

        // Section-based settings
        config.getSectionBased().setEnabled(true);
        config.getSectionBased().setMinConfidence(70);
        config.getSectionBased().setKeywords(java.util.List.of(
                "ingredients", "instructions", "directions", "preparation",
                "method", "recipe", "steps", "cook", "bake"
        ));

        // Content filter settings
        config.getContentFilter().setMinOutputSize(100);

        // Fallback settings
        config.getFallback().setMinSafeSize(300);

        objectMapper = new ObjectMapper();

        // Mock meter registry responses
        when(meterRegistry.counter(anyString(), any(), any())).thenReturn(counter);
        when(meterRegistry.gauge(anyString(), any(Double.class))).thenReturn(0.0);
        when(meterRegistry.summary(anyString())).thenReturn(summary);

        // Build strategy chain in preferred order
        var strategies = java.util.List.of(
                new net.shamansoft.cookbook.html.strategy.StructuredDataStrategy(config, objectMapper),
                new net.shamansoft.cookbook.html.strategy.SectionBasedStrategy(config),
                new net.shamansoft.cookbook.html.strategy.ContentFilterStrategy(config)
        );

        htmlCleaner = new HtmlCleaner(config, meterRegistry, strategies);
    }

    @Test
    void shouldExtractStructuredDataWhenPresent() {
        String html = """
                <html>
                <head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Recipe",
                  "name": "Chocolate Cake",
                  "description": "A delicious chocolate cake recipe",
                  "recipeIngredient": ["flour", "sugar", "cocoa"],
                  "recipeInstructions": "Mix ingredients and bake at 350°F for 30 minutes",
                  "totalTime": "PT45M"
                }
                </script>
                </head>
                <body>
                    <nav>Navigation menu</nav>
                    <article>
                        <h1>Chocolate Cake Recipe</h1>
                        <p>Lots of content here that will be ignored...</p>
                    </article>
                    <footer>Footer content</footer>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.STRUCTURED_DATA);
        assertThat(result.reductionRatio()).isGreaterThan(0.5); // At least 50% reduction
        assertThat(result.cleanedHtml()).contains("Chocolate Cake");
        assertThat(result.cleanedHtml()).contains("recipeIngredient");
        assertThat(result.originalSize()).isEqualTo(html.length());
        assertThat(result.cleanedSize()).isLessThan(html.length());
    }

    @Test
    void shouldHandleGraphArrayInJsonLd() {
        String html = """
                <html>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@graph": [
                    {
                      "@type": "WebSite",
                      "name": "My Site"
                    },
                    {
                      "@type": "Recipe",
                      "name": "Pasta Recipe",
                      "description": "Quick and easy pasta",
                      "recipeIngredient": ["pasta", "sauce"],
                      "recipeInstructions": "Cook pasta, add sauce",
                      "totalTime": "PT20M"
                    }
                  ]
                }
                </script>
                <body>Content</body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.STRUCTURED_DATA);
        assertThat(result.cleanedHtml()).contains("Pasta Recipe");
    }

    @Test
    void shouldUseSectionBasedExtractionWhenNoStructuredData() {
        String html = """
                <html>
                <body>
                    <nav>Skip this navigation</nav>
                    <article class="recipe-content">
                        <h2>Amazing Cookie Recipe</h2>
                        <h3>Ingredients</h3>
                        <ul>
                            <li>2 cups flour</li>
                            <li>1 cup sugar</li>
                            <li>1 cup butter</li>
                            <li>2 cups chocolate chips</li>
                        </ul>
                        <h3>Preparation Steps</h3>
                        <ol>
                            <li>Mix ingredients together thoroughly</li>
                            <li>Cook the dough into small balls</li>
                            <li>Bake at 350°F for 12 minutes until golden</li>
                            <li>Cool and serve your delicious cookies</li>
                        </ol>
                        <p>This is a delicious recipe for chocolate chip cookies that everyone will love!
                        Follow these directions carefully for the best results. The preparation method is simple.</p>
                    </article>
                    <aside class="sidebar">Advertisements and other content</aside>
                    <footer>Footer content</footer>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.SECTION_BASED);
        assertThat(result.reductionRatio()).isGreaterThan(0.3); // At least 30% reduction
        assertThat(result.cleanedHtml()).contains("Ingredients");
        assertThat(result.cleanedHtml()).contains("Preparation Steps");
        assertThat(result.cleanedHtml()).doesNotContain("navigation");
        assertThat(result.cleanedHtml()).doesNotContain("Footer");
    }

    @Test
    void shouldUseContentFilterWhenSectionScoreTooLow() {
        String html = """
                <html>
                <head>
                    <script>console.log('test');</script>
                    <style>body { margin: 0; }</style>
                </head>
                <body>
                    <nav class="main-nav">Navigation</nav>
                    <div>
                        <p>Some content without recipe keywords, but enough text to pass minimum size check.
                        This paragraph contains sufficient content to ensure we meet the minimum output size
                        requirement for the content filter strategy. We need at least 500 characters total,
                        so this paragraph will help us reach that threshold. Adding more text here to make
                        sure we have enough content. More words, more sentences, more characters to fill
                        the space and ensure the test passes correctly. This is important for validating
                        the content filter strategy works as expected when there are no recipe sections.</p>
                    </div>
                    <footer>Footer content</footer>
                    <div class="ads">Advertisement</div>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(CONTENT_FILTER);
        assertThat(result.cleanedHtml()).doesNotContain("<script>");
        assertThat(result.cleanedHtml()).doesNotContain("<style>");
        assertThat(result.cleanedHtml()).doesNotContain("<nav>");
        assertThat(result.cleanedHtml()).doesNotContain("<footer>");
        assertThat(result.cleanedHtml()).doesNotContain("Advertisement");
        assertThat(result.cleanedHtml()).contains("Some content");
    }

    @Test
    void shouldFallbackToRawHtmlWhenOutputTooSmall() {
        String html = "<html><body><p>tiny</p></body></html>";

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.FALLBACK);
        assertThat(result.reductionRatio()).isEqualTo(0.0);
        assertThat(result.cleanedHtml()).isEqualTo(html);
    }

    @Test
    void shouldReturnDisabledWhenPreprocessingDisabled() {
        config.setEnabled(false);
        String html = "<html><body>Content</body></html>";

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.DISABLED);
        assertThat(result.cleanedHtml()).isEqualTo(html);
        assertThat(result.reductionRatio()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleEmptyHtml() {
        HtmlCleaner.Results result = htmlCleaner.process("", "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.FALLBACK);
        assertThat(result.cleanedHtml()).isEmpty();
        assertThat(result.originalSize()).isEqualTo(0);
        assertThat(result.cleanedSize()).isEqualTo(0);
    }

    @Test
    void shouldHandleNullHtml() {
        HtmlCleaner.Results result = htmlCleaner.process(null, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.FALLBACK);
        assertThat(result.cleanedHtml()).isEmpty();
        assertThat(result.originalSize()).isEqualTo(0);
    }

    @Test
    void shouldScoreSectionWithMultipleRecipeKeywords() {
        String html = """
                <html>
                <body>
                    <article>
                        <h2>Amazing Recipe Title</h2>
                        <h3>Ingredients List</h3>
                        <ul>
                            <li>First ingredient for this recipe</li>
                            <li>Second ingredient needed</li>
                            <li>Third ingredient</li>
                        </ul>
                        <h3>Preparation Steps and Instructions</h3>
                        <ol>
                            <li>First step of the method - prepare ingredients</li>
                            <li>Cook for 20 minutes on medium heat</li>
                            <li>Bake until golden brown at 350°F</li>
                            <li>Follow these directions for serving</li>
                        </ol>
                        <p>This recipe is easy to follow. The preparation method is straightforward.
                        Simply cook the ingredients according to the steps above and bake as directed.</p>
                    </article>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        // Should use SECTION_BASED due to high keyword density
        assertThat(result.strategyUsed()).isEqualTo(Strategy.SECTION_BASED);
        assertThat(result.reductionRatio()).isGreaterThan(0.0);
    }

    @Test
    void shouldNotFailOnMalformedJsonLd() {
        String html = """
                <html>
                <script type="application/ld+json">
                {invalid json here}
                </script>
                <body>
                    <p>Content that should still be processed even with invalid JSON-LD.
                    Adding enough text here to ensure we meet minimum size requirements
                    for the content filter strategy to work properly. This text needs to
                    be substantial enough to pass the size checks and allow the test to
                    validate that malformed JSON-LD is handled gracefully without causing
                    failures in the preprocessing pipeline.</p>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        // Should not throw exception, should fall back to content filter or fallback
        assertThat(result).isNotNull();
        assertThat(result.cleanedHtml()).isNotEmpty();
        assertThat(result.strategyUsed()).isIn(
                CONTENT_FILTER,
                Strategy.FALLBACK
        );
    }

    @Test
    void shouldRemoveHiddenElements() {
        String html = """
                <html>
                <body>
                    <div style="display:none">Hidden content</div>
                    <div style="visibility:hidden">Also hidden</div>
                    <article>
                        <h2>Recipe for Delicious Cookies</h2>
                        <p>Visible content that should remain in the output. This is a wonderful
                        recipe for making delicious homemade cookies. The ingredients are simple
                        and the preparation is straightforward.</p>
                        <h3>Ingredients</h3>
                        <ul>
                            <li>2 cups flour</li>
                            <li>1 cup sugar</li>
                            <li>1 cup butter</li>
                            <li>2 eggs</li>
                        </ul>
                        <h3>Instructions</h3>
                        <ol>
                            <li>Mix all ingredients together in a large bowl</li>
                            <li>Form into small balls and place on baking sheet</li>
                            <li>Bake at 350°F for 12 minutes</li>
                            <li>Cool and enjoy your delicious cookies</li>
                        </ol>
                    </article>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.cleanedHtml()).contains("Visible content");
        assertThat(result.cleanedHtml()).doesNotContain("Hidden content");
        assertThat(result.cleanedHtml()).doesNotContain("Also hidden");
    }

    @Test
    void shouldCleanDataAttributes() {
        String html = """
                <html>
                <body>
                    <div data-tracking="123" data-analytics="456">
                        <article>
                            <h2>Amazing Recipe with Tracking</h2>
                            <p>Content with tracking attributes that should be removed.
                            This recipe contains delicious ingredients and simple preparation steps.</p>
                            <h3>Ingredients</h3>
                            <ul>
                                <li data-id="1">2 cups flour</li>
                                <li data-id="2">1 cup sugar</li>
                                <li data-id="3">1 cup butter</li>
                            </ul>
                            <h3>Instructions</h3>
                            <ol>
                                <li data-step="1">Mix ingredients thoroughly</li>
                                <li data-step="2">Cook and bake as directed</li>
                                <li data-step="3">Follow preparation method</li>
                            </ol>
                        </article>
                    </div>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");
        assertThat(result.strategyUsed()).isNotEqualTo(FALLBACK);
        assertThat(result.cleanedHtml()).doesNotContain("data-tracking");
        assertThat(result.cleanedHtml()).doesNotContain("data-analytics");
        assertThat(result.cleanedHtml()).contains("Content with tracking");
    }

    @Test
    void shouldPreferStructuredDataOverSections() {
        String html = """
                <html>
                <script type="application/ld+json">
                {
                  "@type": "Recipe",
                  "name": "Quick Recipe",
                  "description": "A quick and easy recipe",
                  "recipeIngredient": ["ingredient1"],
                  "recipeInstructions": "Do something",
                  "totalTime": "PT15M"
                }
                </script>
                <body>
                    <article>
                        <h2>Recipe with ingredients and instructions</h2>
                        <ul><li>Item 1</li><li>Item 2</li></ul>
                        <ol><li>Step 1</li><li>Step 2</li></ol>
                    </article>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        // Should prefer structured data even when good sections exist
        assertThat(result.strategyUsed()).isEqualTo(Strategy.STRUCTURED_DATA);
    }

    @Test
    void shouldCalculateReductionRatioCorrectly() {
        String html = "<html><body>" + "x".repeat(1000) + "</body></html>";

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        double expectedRatio = (double) (result.originalSize() - result.cleanedSize()) / result.originalSize();
        assertThat(result.reductionRatio()).isEqualTo(expectedRatio);
        assertThat(result.originalSize()).isEqualTo(html.length());
    }

    @Test
    void shouldEmitMetricsOnSuccess() {
        String html = "<html><body><p>" + "test content ".repeat(100) + "</p></body></html>";

        htmlCleaner.process(html, "test-url");

        verify(meterRegistry).counter(eq("html.preprocessing.strategy"), anyString(), anyString());
        verify(meterRegistry).summary("html.preprocessing.original_size");
        verify(meterRegistry).summary("html.preprocessing.cleaned_size");
    }

    @Test
    void shouldHandleRecipeTypeAsArray() {
        String html = """
                <html>
                <script type="application/ld+json">
                {
                  "@type": ["Recipe", "Article"],
                  "name": "Multi-type Recipe",
                  "description": "A multi-type recipe article",
                  "recipeIngredient": ["ingredient1"],
                  "recipeInstructions": "Instructions here",
                  "totalTime": "PT30M"
                }
                </script>
                <body>Content</body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.strategyUsed()).isEqualTo(Strategy.STRUCTURED_DATA);
    }

    @Test
    void shouldSkipIncompleteStructuredData() {
        // Set minimum completeness to 70
        config.getStructuredData().setMinCompleteness(70);

        String html = """
                <html>
                <script type="application/ld+json">
                {
                  "@type": "Recipe",
                  "name": "Incomplete Recipe"
                }
                </script>
                <body>
                    <article>
                        <h2>Recipe with ingredients keywords here</h2>
                        <p>Some recipe instructions and preparation steps for cooking.
                        Adding more content to ensure section-based extraction will work.
                        This text contains multiple recipe-related keywords to boost the
                        section score above the confidence threshold.</p>
                    </article>
                </body>
                </html>
                """;

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        // Should fall back to section-based or content filter, not structured data
        assertThat(result.strategyUsed()).isNotEqualTo(Strategy.STRUCTURED_DATA);
    }

    @Test
    void shouldHandleVeryLargeHtml() {
        // Create a large HTML document (100KB)
        String largeContent = "<p>" + "word ".repeat(20000) + "</p>";
        String html = "<html><body>" + largeContent + "</body></html>";

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result).isNotNull();
        assertThat(result.originalSize()).isGreaterThan(100000);
        assertThat(result.cleanedSize()).isLessThanOrEqualTo(result.originalSize());
    }

    @Test
    void shouldGenerateCorrectMetricsMessage() {
        String html = "<html><body>test</body></html>";

        HtmlCleaner.Results result = htmlCleaner.process(html, "test-url");

        assertThat(result.metricsMessage()).contains("Strategy:");
        assertThat(result.metricsMessage()).contains("→");
        assertThat(result.metricsMessage()).contains("chars");
        assertThat(result.metricsMessage()).contains("reduction");
    }
}
