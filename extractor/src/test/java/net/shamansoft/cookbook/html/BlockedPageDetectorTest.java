package net.shamansoft.cookbook.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedPageDetectorTest {

    private static final String SHORT_TEXT = "Checking your browser before accessing the site. ".repeat(12);
    private static final String RECIPE_TEXT = "Mix the flour, sugar and butter until smooth. ".repeat(60);

    @Test
    void acceptsRealContent() {
        Document doc = Jsoup.parse("<html><head><title>Peanut Butter Cookies</title>"
                + "<script src=\"/cdn-cgi/challenge-platform/scripts/jsd/main.js\"></script></head>"
                + "<body><article>" + RECIPE_TEXT + "</article></body></html>");

        assertThat(BlockedPageDetector.detect(doc)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Just a moment...", "Attention Required! | Cloudflare", "Access Denied", "Pardon Our Interruption"})
    void flagsChallengeTitlesOnShortPages(String title) {
        Document doc = Jsoup.parse("<html><head><title>" + title + "</title></head>"
                + "<body><p>" + SHORT_TEXT + "</p></body></html>");

        assertThat(BlockedPageDetector.detect(doc)).map(BlockedPageDetector.Finding::reason).hasValueSatisfying(reason ->
                assertThat(reason).contains("challenge"));
    }

    @Test
    void ignoresChallengeLikeTitleOnLongPage() {
        Document doc = Jsoup.parse("<html><head><title>Just a Moment Microwave Mug Cake</title></head>"
                + "<body><article>" + RECIPE_TEXT + "</article></body></html>");

        assertThat(BlockedPageDetector.detect(doc)).isEmpty();
    }

    @Test
    void flagsJavaScriptShell() {
        Document doc = Jsoup.parse("<html><head><title>Recipes</title></head>"
                + "<body><div id=\"root\"></div><noscript>Enable JavaScript</noscript></body></html>");

        assertThat(BlockedPageDetector.detect(doc)).map(BlockedPageDetector.Finding::reason).hasValueSatisfying(reason ->
                assertThat(reason).contains("almost no text"));
    }

    @Test
    void ignoresTextInScripts() {
        Document doc = Jsoup.parse("<html><head><title>Recipes</title></head>"
                + "<body><script>window.__DATA__ = \"" + RECIPE_TEXT + "\"</script></body></html>");

        assertThat(BlockedPageDetector.detect(doc)).isPresent();
    }

    @Test
    void markdown_flagsChallengeFromFrontMatterTitle() {
        String markdown = "---\ntitle: Just a moment...\ndescription: x\n---\n\n" + SHORT_TEXT;

        assertThat(BlockedPageDetector.detectMarkdown(markdown)).map(BlockedPageDetector.Finding::reason).hasValueSatisfying(reason ->
                assertThat(reason).contains("Just a moment..."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Page not found - Preppy Kitchen", "404 Not Found", "Error 404 | Site",
            "Nothing found for no-such-page", "Sorry, this page doesn't exist"})
    void flagsNotFoundTitlesRegardlessOfLength(String title) {
        Document doc = Jsoup.parse("<html><head><title>" + title + "</title></head>"
                + "<body><article>" + RECIPE_TEXT + "</article></body></html>");

        assertThat(BlockedPageDetector.detect(doc)).map(BlockedPageDetector.Finding::kind)
                .contains(BlockedPageDetector.Kind.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Lost and Found Soup", "404 Calorie Salad", "Peanut Butter Cookies"})
    void doesNotFlagRecipeTitlesAsNotFound(String title) {
        Document doc = Jsoup.parse("<html><head><title>" + title + "</title></head>"
                + "<body><article>" + RECIPE_TEXT + "</article></body></html>");

        assertThat(BlockedPageDetector.detect(doc)).isEmpty();
    }

    @Test
    void markdown_flagsNotFoundFromQuotedFrontMatterTitle() {
        String markdown = "---\ntitle: \"Page not found - Preppy Kitchen\"\nmeta:\n  \"og:title\": \"Page Not Found\"\n---\n\n"
                + RECIPE_TEXT;

        assertThat(BlockedPageDetector.detectMarkdown(markdown)).hasValueSatisfying(finding -> {
            assertThat(finding.kind()).isEqualTo(BlockedPageDetector.Kind.NOT_FOUND);
            assertThat(finding.reason()).contains("\"Page not found - Preppy Kitchen\"");
        });
    }

    @Test
    void markdown_flagsNearlyEmptyBody() {
        assertThat(BlockedPageDetector.detectMarkdown("---\ntitle: Cookies\n---\n\n# Cookies")).isPresent();
    }

    @Test
    void markdown_acceptsRecipeWithoutFrontMatter() {
        assertThat(BlockedPageDetector.detectMarkdown("# Cookies\n\n" + RECIPE_TEXT)).isEmpty();
    }
}
