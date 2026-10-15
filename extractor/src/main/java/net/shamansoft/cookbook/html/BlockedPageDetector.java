package net.shamansoft.cookbook.html;

import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects pages that were delivered but carry no recipe, so they can be dropped before the LLM:
 * <ul>
 *   <li>{@link Kind#NOT_FOUND}: the site's "page not found" page served with HTTP 200 (soft 404),
 *       or rendered by markdown.new for a missing URL.</li>
 *   <li>{@link Kind#BLOCKED}: bot-protection challenge pages (Cloudflare, Imperva, PerimeterX,
 *       Akamai, ...) and JavaScript-rendered shells.</li>
 * </ul>
 *
 * <p>Challenge titles only count on short pages, so a real recipe titled
 * "Just a Moment Mug Cake" is not flagged.
 */
final class BlockedPageDetector {

    enum Kind {
        NOT_FOUND,
        BLOCKED
    }

    record Finding(Kind kind, String reason) {
    }

    /** Below this much visible text the page cannot hold a recipe. */
    static final int MIN_TEXT_LENGTH = 500;

    /** Challenge pages are short; a page with more text than this is real content. */
    static final int MAX_CHALLENGE_TEXT_LENGTH = 2_000;

    private static final List<String> CHALLENGE_TITLES = List.of(
            "just a moment",
            "attention required",
            "access denied",
            "are you a robot",
            "pardon our interruption",
            "verify you are human",
            "security check");

    /** "Page not found - Preppy Kitchen", "404 Not Found", "Error 404", "Nothing found for ...". */
    private static final Pattern NOT_FOUND_TITLE = Pattern.compile(
            "(?i)\\b(page|post|content|article|recipe) not found\\b"
                    + "|\\b404\\b.*\\b(not found|error|page)\\b"
                    + "|\\b(not found|error)\\b.*\\b404\\b"
                    + "|^nothing found\\b"
                    + "|\\bpage (does not|doesn't|can't|cannot) (exist|be found)\\b");

    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\n(.*?)\\n---\\n", Pattern.DOTALL);
    private static final Pattern FRONT_MATTER_TITLE = Pattern.compile("(?m)^title:\\s*(.+)$");

    private BlockedPageDetector() {
    }

    static Optional<Finding> detect(Document doc) {
        return detect(doc.title(), doc.body().text());
    }

    /** Markdown from markdown.new: optional YAML front matter with {@code title:}, then the body. */
    static Optional<Finding> detectMarkdown(String markdown) {
        String title = "";
        String body = markdown;
        Matcher frontMatter = FRONT_MATTER.matcher(markdown);
        if (frontMatter.find()) {
            Matcher titleMatcher = FRONT_MATTER_TITLE.matcher(frontMatter.group(1));
            if (titleMatcher.find()) {
                title = unquote(titleMatcher.group(1).trim());
            }
            body = markdown.substring(frontMatter.end());
        }
        return detect(title, body.strip());
    }

    static Optional<Finding> detect(String title, String text) {
        if (NOT_FOUND_TITLE.matcher(title).find()) {
            return Optional.of(new Finding(Kind.NOT_FOUND, "not-found page (title: \"" + title + "\")"));
        }
        int textLength = text.length();
        if (textLength < MIN_TEXT_LENGTH) {
            return Optional.of(new Finding(Kind.BLOCKED, "page has almost no text (" + textLength + " chars)"));
        }
        if (textLength <= MAX_CHALLENGE_TEXT_LENGTH) {
            String lowerTitle = title.toLowerCase(Locale.ROOT);
            for (String challengeTitle : CHALLENGE_TITLES) {
                if (lowerTitle.contains(challengeTitle)) {
                    return Optional.of(new Finding(Kind.BLOCKED,
                            "bot-protection challenge page (title: \"" + title + "\")"));
                }
            }
        }
        return Optional.empty();
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
