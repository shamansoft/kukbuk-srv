package net.shamansoft.cookbook.html.strategy;

import org.jsoup.nodes.Element;

import java.util.regex.Pattern;

public class HtmlCleanupUtils {

    /**
     * An ad marker as a whole word of a class or id ("ad-banner", "google_ads", "adsbygoogle"). A plain substring
     * match on "ad" would also hit "padding", "shadow", "heading" or "thread", which can hold the recipe itself.
     */
    private static final Pattern AD_MARKER = Pattern.compile(
            "(?i)(?:^|[\\s_-])(?:ads?|adv|advert\\w*|adsbygoogle|adsense|adslot|adunit|adbox|adbanner)\\d*(?:$|[\\s_-])");

    // Package-private helper visible to strategies in this package only
    public static void cleanupAttributes(Element el) {
        el.removeAttr("style");
        el.removeAttr("class");
        el.removeAttr("id");
        var unwanted = el.attributes().asList().stream()
                .map(attr -> attr.getKey())
                .filter(key -> key.startsWith("data-") || key.startsWith("on"))
                .toList();
        unwanted.forEach(el::removeAttr);
    }

    public static void cleanElement(Element element) {
        element.select("script, style, noscript").remove();
        element.select("nav, header, footer").remove();
        element.select("iframe, embed, object").remove();
        removeAds(element);
        element.select("[class*=social], [class*=share]").remove();
        element.select("[class*=comment], [id*=comment]").remove();
        element.select("[style*=display:none], [style*=visibility:hidden]").remove();
        // clean attributes recursively
        for (Element child : element.getAllElements()) {
            cleanupAttributes(child);
        }
    }

    private static void removeAds(Element root) {
        root.getAllElements().stream()
                .filter(el -> !"body".equals(el.normalName()) && !"html".equals(el.normalName()))
                .filter(HtmlCleanupUtils::isAd)
                .forEach(Element::remove);
    }

    private static boolean isAd(Element el) {
        return AD_MARKER.matcher(el.className()).find() || AD_MARKER.matcher(el.id()).find();
    }
}
