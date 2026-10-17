package net.shamansoft.cookbook.html;

import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import net.shamansoft.cookbook.html.strategy.StructuredDataStrategy;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlFetcherTest {

    private static final String RECIPE_JSON_LD =
            "{\"@type\":\"Recipe\",\"name\":\"Cake\",\"recipeIngredient\":[\"flour\"],\"recipeInstructions\":\"bake\"}";

    @Test
    void toBodyHtml_removesExecutableScripts() {
        String html = "<html><head><script>console.log('head')</script></head>"
                + "<body><p>hello</p><script type=\"text/javascript\">track()</script>"
                + "<script type=\"module\">import x from 'y'</script></body></html>";

        String out = HtmlFetcher.toBodyHtml(Jsoup.parse(html));

        assertThat(out).contains("<p>hello</p>");
        assertThat(out).doesNotContain("console.log", "track()", "import x");
    }

    @Test
    void toBodyHtml_keepsHeadJsonLd() {
        String html = "<html><head><script type=\"application/ld+json\">" + RECIPE_JSON_LD + "</script>"
                + "<script>console.log('x')</script></head><body><p>hello</p></body></html>";

        String out = HtmlFetcher.toBodyHtml(Jsoup.parse(html));

        assertThat(out).contains("<p>hello</p>", "application/ld+json", "\"name\":\"Cake\"");
        assertThat(out).doesNotContain("console.log");
    }

    @Test
    void toBodyHtml_keepsBodyJsonLd() {
        String html = "<html><head></head><body><p>hello</p>"
                + "<script type=\"application/ld+json\">" + RECIPE_JSON_LD + "</script></body></html>";

        String out = HtmlFetcher.toBodyHtml(Jsoup.parse(html));

        assertThat(out).contains("application/ld+json", "\"name\":\"Cake\"");
    }

    @Test
    void toBodyHtml_withoutJsonLd_returnsBodyOnly() {
        String html = "<html><head><title>t</title></head><body><p>hello</p></body></html>";

        String out = HtmlFetcher.toBodyHtml(Jsoup.parse(html));

        assertThat(out).isEqualTo("<p>hello</p>");
    }

    @Test
    void toBodyHtml_headJsonLdIsFoundByStructuredDataStrategy() {
        HtmlCleanupConfig config = new HtmlCleanupConfig();
        config.setEnabled(true);
        config.getStructuredData().setEnabled(true);
        config.getStructuredData().setMinCompleteness(50);
        StructuredDataStrategy strategy = new StructuredDataStrategy(config, new ObjectMapper());
        String html = "<html><head><script type=\"application/ld+json\">" + RECIPE_JSON_LD + "</script></head>"
                + "<body><p>hello</p></body></html>";

        String fetched = HtmlFetcher.toBodyHtml(Jsoup.parse(html));

        assertThat(strategy.clean(fetched)).hasValueSatisfying(json -> assertThat(json).contains("Cake"));
    }
}
