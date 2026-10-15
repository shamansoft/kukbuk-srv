package net.shamansoft.cookbook.html;

/**
 * Page content ready for LLM extraction, tagged with its format so downstream
 * processing can decide whether HTML cleaning applies.
 */
public record PageContent(String content, Format format) {

    public enum Format {
        HTML,
        MARKDOWN
    }

    public static PageContent html(String content) {
        return new PageContent(content, Format.HTML);
    }

    public static PageContent markdown(String content) {
        return new PageContent(content, Format.MARKDOWN);
    }

    public boolean isMarkdown() {
        return format == Format.MARKDOWN;
    }
}
