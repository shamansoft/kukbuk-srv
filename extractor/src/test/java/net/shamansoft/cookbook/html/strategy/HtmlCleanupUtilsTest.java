package net.shamansoft.cookbook.html.strategy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"ad", "AD", "ads", "ad-banner", "top-ad", "google_ads", "sidebar-ad-1", "ad2",
            "adsbygoogle", "advert", "advertisement", "adsense-block", "content ad"})
    void cleanElement_removesAdMarkers(String marker) {
        assertThat(cleaned("<div class=\"" + marker + "\">x</div><p>keep</p>")).doesNotContain("x");
        assertThat(cleaned("<div id=\"" + marker + "\">x</div><p>keep</p>")).doesNotContain("x");
    }

    @ParameterizedTest
    @ValueSource(strings = {"padding_l", "shadow", "heading", "thread", "gradient", "download", "add-to-cart",
            "loading", "bread", "instructions-read-more"})
    void cleanElement_keepsWordsContainingAd(String name) {
        assertThat(cleaned("<div class=\"" + name + "\">x</div>")).contains("x");
        assertThat(cleaned("<div id=\"" + name + "\">x</div>")).contains("x");
    }

    @Test
    void cleanElement_keepsBodyMarkedAsAdFree() {
        Document doc = Jsoup.parse("<html><body class=\"no-ads\"><p>keep</p></body></html>");

        HtmlCleanupUtils.cleanElement(doc.body());

        assertThat(doc.body().text()).isEqualTo("keep");
    }

    private static String cleaned(String html) {
        Element body = Jsoup.parse(html).body();
        HtmlCleanupUtils.cleanElement(body);
        return body.text();
    }
}
