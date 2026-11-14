package net.shamansoft.cookbook.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.Set;

/**
 * The visible text of a cleaner's output, one block per line and list items prefixed with "- ", so a reader
 * can still tell an ingredient list from a paragraph. JSON (the JSON-LD strategy's output) passes through as text.
 */
public final class PageText {

    /** Elements that start a new line even when jsoup does not know them as blocks. */
    private static final Set<String> LINE_TAGS = Set.of("br", "li", "tr", "dt", "dd");

    private PageText() {
    }

    public static String of(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode text) {
                    out.append(text.text());
                } else if (node instanceof Element element && startsLine(element)) {
                    lineBreak(out);
                    if (element.normalName().equals("li")) {
                        out.append("- ");
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && startsLine(element)) {
                    lineBreak(out);
                }
            }
        }, Jsoup.parse(content).body());
        return tidy(out);
    }

    /** Starts a new line, unless one was just started (possibly with a list bullet). */
    private static void lineBreak(StringBuilder out) {
        int length = out.length();
        if (length == 0 || out.charAt(length - 1) == '\n') {
            return;
        }
        boolean bulletOnly = length >= 2 && out.charAt(length - 2) == '-' && out.charAt(length - 1) == ' '
                && (length == 2 || out.charAt(length - 3) == '\n');
        if (!bulletOnly) {
            out.append('\n');
        }
    }

    private static boolean startsLine(Element element) {
        return element.isBlock() || LINE_TAGS.contains(element.normalName());
    }

    /** Trims each line, collapses runs of spaces and drops empty lines. */
    private static String tidy(CharSequence text) {
        StringBuilder result = new StringBuilder(text.length());
        for (String line : text.toString().split("\n")) {
            // an empty <li> leaves its bullet in front of the next item's
            String trimmed = line.replaceAll("[ \\t\\u00A0]+", " ").strip().replaceFirst("^(- )+", "- ");
            if (trimmed.isEmpty() || trimmed.equals("-")) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(trimmed);
        }
        return result.toString();
    }
}
