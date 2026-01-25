package net.shamansoft.cookbook.html.strategy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlCleanupUtilsTest {

    @Test
    void cleanupAttributes_removesUnwanted() {
        String html = "<div id=\"i\" class=\"c\" style=\"color:red\" data-track=\"1\" onload=\"x()\">" +
                "<p data-info=\"p\">text</p></div>";

        Document doc = Jsoup.parse(html);
        Element div = doc.body().child(0);

        HtmlCleanupUtils.cleanupAttributes(div);

        assertThat(div.hasAttr("id")).isFalse();
        assertThat(div.hasAttr("class")).isFalse();
        assertThat(div.hasAttr("style")).isFalse();
        assertThat(div.hasAttr("data-track")).isFalse();

        Element p = div.selectFirst("p");
        // child attributes starting with data- also should be removed when running cleanElement
        HtmlCleanupUtils.cleanElement(doc.body());
        assertThat(p.hasAttr("data-info")).isFalse();
    }

    @Test
    void cleanElement_removesHiddenAndAds() {
        String html = "<div style=\"display:none\">hidden</div>" +
                "<div class=\"ad-banner\">ad</div>" +
                "<nav>nav</nav>" +
                "<article><p>keep</p></article>";

        Document doc = Jsoup.parse(html);
        Element body = doc.body();

        HtmlCleanupUtils.cleanElement(body);

        assertThat(body.html()).doesNotContain("hidden");
        assertThat(body.html()).doesNotContain("ad");
        assertThat(body.html()).doesNotContain("nav");
        assertThat(body.html()).contains("keep");
    }
}
