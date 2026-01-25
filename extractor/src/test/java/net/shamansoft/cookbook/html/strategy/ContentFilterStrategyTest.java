package net.shamansoft.cookbook.html.strategy;

import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContentFilterStrategyTest {

    private HtmlCleanupConfig config;
    private ContentFilterStrategy strategy;

    @BeforeEach
    void setUp() {
        config = new HtmlCleanupConfig();
        config.getContentFilter().setMinOutputSize(10);
        strategy = new ContentFilterStrategy(config);
    }

    @Test
    void shouldRemoveScriptsAndAdsAndCleanAttributes() {
        String html = "<html><body>"
                + "<div class=\"ad-banner\">Buy now</div>"
                + "<script>console.log('x')</script>"
                + "<div id=\"content\" data-test=\"1\" style=\"color:red\">Hello <span onClick=\"evil()\">Click</span></div>"
                + "</body></html>";

        Optional<String> out = strategy.clean(html);
        assertThat(out).isPresent();
        String cleaned = out.get();
        assertThat(cleaned).doesNotContain("script");
        assertThat(cleaned).doesNotContain("ad-banner");
        assertThat(cleaned).doesNotContain("data-test");
        assertThat(cleaned).doesNotContain("onClick");
        assertThat(cleaned).contains("Hello");
    }

    @Test
    void shouldReturnEmptyWhenTooSmall() {
        // small body after cleaning
        String html = "<html><body><div style=\"display:none\">hidden</div></body></html>";
        Optional<String> out = strategy.clean(html);
        assertThat(out).isEmpty();
    }
}
