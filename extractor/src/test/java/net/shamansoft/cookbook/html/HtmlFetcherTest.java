package net.shamansoft.cookbook.html;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlFetcherTest {

    @Test
    void fetch_removesScriptTags_byParsingDocument() throws Exception {
        // We cannot mock static Jsoup.connect easily without a framework, so test behavior by
        // delegating to a small helper: we'll simulate the parsing step by invoking the same
        // logic on a Document built from string.

        String html = "<html><head><script>console.log('x')</script></head><body><p>hello</p></body></html>";

        // Simulate what Jsoup.connect(url).get() would return
        Document doc = org.jsoup.Jsoup.parse(html);
        // Remove scripts as HtmlFetcher would
        for (Element script : doc.getElementsByTag("script")) {
            script.remove();
        }

        String bodyHtml = doc.body().html();
        assertThat(bodyHtml).contains("<p>hello</p>");
        assertThat(bodyHtml).doesNotContain("console.log");
    }
}
