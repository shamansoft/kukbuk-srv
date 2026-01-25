package net.shamansoft.cookbook.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlCleanupConfigTest {

    @Test
    void defaults_and_nested_properties() {
        HtmlCleanupConfig cfg = new HtmlCleanupConfig();

        assertThat(cfg.isEnabled()).isTrue();

        // StructuredData defaults
        assertThat(cfg.getStructuredData()).isNotNull();
        assertThat(cfg.getStructuredData().isEnabled()).isFalse();
        assertThat(cfg.getStructuredData().getMinCompleteness()).isEqualTo(0);

        // SectionBased defaults
        assertThat(cfg.getSectionBased()).isNotNull();
        assertThat(cfg.getSectionBased().isEnabled()).isTrue();
        assertThat(cfg.getSectionBased().getKeywords()).isNullOrEmpty();

        // ContentFilter defaults
        assertThat(cfg.getContentFilter()).isNotNull();
        assertThat(cfg.getContentFilter().getMinOutputSize()).isEqualTo(0);

        // Fallback defaults
        assertThat(cfg.getFallback()).isNotNull();
        assertThat(cfg.getFallback().getMinSafeSize()).isEqualTo(0);
    }
}
