package net.shamansoft.cookbook.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestFirebaseConfig.class)
@TestPropertySource(properties = {
        "firebase.enabled=false",
        "firestore.enabled=false"
})
class HtmlCleanupConfigTest {

    @Autowired
    private HtmlCleanupConfig config;

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

    @Test
    void htmlCleanupConfig_isAutowired_fromContext() {
        // Verify the config is autowired from Spring context
        assertThat(config).isNotNull();
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void structuredData_canBeConfigured() {
        HtmlCleanupConfig cfg = new HtmlCleanupConfig();
        HtmlCleanupConfig.StructuredData sd = new HtmlCleanupConfig.StructuredData();
        sd.setEnabled(true);
        sd.setMinCompleteness(80);

        assertThat(sd.isEnabled()).isTrue();
        assertThat(sd.getMinCompleteness()).isEqualTo(80);
    }

    @Test
    void sectionBased_canBeConfigured() {
        HtmlCleanupConfig cfg = new HtmlCleanupConfig();
        HtmlCleanupConfig.SectionBased sb = new HtmlCleanupConfig.SectionBased();
        sb.setEnabled(true);
        sb.setMinConfidence(75);
        sb.setKeywords(List.of("ingredients", "instructions"));

        assertThat(sb.isEnabled()).isTrue();
        assertThat(sb.getMinConfidence()).isEqualTo(75);
        assertThat(sb.getKeywords()).contains("ingredients", "instructions");
    }

    @Test
    void contentFilter_canBeConfigured() {
        HtmlCleanupConfig cfg = new HtmlCleanupConfig();
        HtmlCleanupConfig.ContentFilter cf = new HtmlCleanupConfig.ContentFilter();
        cf.setMinOutputSize(500);

        assertThat(cf.getMinOutputSize()).isEqualTo(500);
    }

    @Test
    void fallback_canBeConfigured() {
        HtmlCleanupConfig cfg = new HtmlCleanupConfig();
        HtmlCleanupConfig.Fallback fb = new HtmlCleanupConfig.Fallback();
        fb.setMinSafeSize(1000);

        assertThat(fb.getMinSafeSize()).isEqualTo(1000);
    }

    @Test
    void htmlCleanupConfig_hasComponentAnnotation() {
        // Verify HtmlCleanupConfig has @Component annotation for Spring auto-registration
        assertThat(HtmlCleanupConfig.class.isAnnotationPresent(org.springframework.stereotype.Component.class))
                .isTrue();
    }

    @Test
    void htmlCleanupConfig_hasConfigurationPropertiesAnnotation() {
        // Verify HtmlCleanupConfig has @ConfigurationProperties annotation
        assertThat(HtmlCleanupConfig.class.isAnnotationPresent(org.springframework.boot.context.properties.ConfigurationProperties.class))
                .isTrue();
    }
}
