package net.shamansoft.cookbook.html.strategy;

import org.jsoup.nodes.Element;

public class HtmlCleanupUtils {

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
        element.select("[class*=ad], [id*=ad]").remove();
        element.select("[class*=social], [class*=share]").remove();
        element.select("[class*=comment], [id*=comment]").remove();
        element.select("[style*=display:none], [style*=visibility:hidden]").remove();
        // clean attributes recursively
        for (Element child : element.getAllElements()) {
            cleanupAttributes(child);
        }
    }
}
