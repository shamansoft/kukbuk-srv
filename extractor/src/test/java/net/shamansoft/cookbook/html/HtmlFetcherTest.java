package net.shamansoft.cookbook.html;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.shamansoft.cookbook.config.HtmlCleanupConfig;
import net.shamansoft.cookbook.exception.BlockedPageException;
import net.shamansoft.cookbook.exception.UrlFetchException;
import net.shamansoft.cookbook.html.strategy.StructuredDataStrategy;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        StructuredDataStrategy strategy = new StructuredDataStrategy(config, new ObjectMapper(), new SimpleMeterRegistry());
        String html = "<html><head><script type=\"application/ld+json\">" + RECIPE_JSON_LD + "</script></head>"
                + "<body><p>hello</p></body></html>";

        String fetched = HtmlFetcher.toBodyHtml(Jsoup.parse(html));

        assertThat(strategy.clean(fetched)).hasValueSatisfying(json -> assertThat(json).contains("Cake"));
    }

    // ---- the URL after redirects, for the Jev URL check ---------------------

    @Test
    void blockedStatusReportsTheUrlARedirectLedTo() throws Exception {
        HttpServer server = redirectingServer(403, "denied");
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            assertThatThrownBy(() -> new HtmlFetcher().fetchUsable(base + "/short"))
                    .isInstanceOf(UrlFetchException.class)
                    .extracting(e -> ((UrlFetchException) e).getFinalUrl())
                    .isEqualTo(base + "/recipes/pumpkin-soup");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void challengePageReportsTheUrlARedirectLedTo() throws Exception {
        HttpServer server = redirectingServer(200,
                "<html><head><title>Just a moment...</title></head><body>Checking your browser</body></html>");
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            assertThatThrownBy(() -> new HtmlFetcher().fetchUsable(base + "/short"))
                    .isInstanceOf(BlockedPageException.class)
                    .extracting(e -> ((UrlFetchException) e).getFinalUrl())
                    .isEqualTo(base + "/recipes/pumpkin-soup");
        } finally {
            server.stop(0);
        }
    }

    /** {@code /short} redirects to {@code /recipes/pumpkin-soup}, which answers {@code status} with {@code body}. */
    private static HttpServer redirectingServer(int status, String body) throws Exception {
        HttpServer server =
                HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/short", exchange -> {
            exchange.getResponseHeaders().add("Location", "/recipes/pumpkin-soup");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/recipes/pumpkin-soup", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
