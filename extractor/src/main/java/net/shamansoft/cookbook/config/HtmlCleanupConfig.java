package net.shamansoft.cookbook.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "cookbook.html-cleanup")
public class HtmlCleanupConfig {

    private boolean enabled = true;
    private StructuredData structuredData = new StructuredData();
    private SectionBased sectionBased = new SectionBased();
    private ContentFilter contentFilter = new ContentFilter();
    private Fallback fallback = new Fallback();

    @Data
    public static class StructuredData {
        private boolean enabled;
        private int minCompleteness;
    }

    @Data
    public static class SectionBased {
        private boolean enabled = true;
        private int minConfidence;
        private List<String> keywords;
    }

    @Data
    public static class ContentFilter {
        private int minOutputSize;
    }

    @Data
    public static class Fallback {
        private int minSafeSize;
    }
}
