package net.shamansoft.cookbook.config;

import net.shamansoft.cookbook.service.RecipePostProcessor;
import net.shamansoft.cookbook.service.RecipeValidationService;
import net.shamansoft.cookbook.service.ValidatingTransformerService;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.cookbook.service.youtube.YouTubeTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for transformer beans.
 * Provides two ValidatingTransformerService instances:
 * - htmlValidatingTransformer (primary): for HTML recipe extraction
 * - youtubeValidatingTransformer: for YouTube video recipe extraction
 */
@Configuration
public class TransformerConfiguration {

    @Bean
    @Primary
    @Qualifier("htmlValidatingTransformer")
    public ValidatingTransformerService htmlValidatingTransformer(
            @Qualifier("geminiTransformer") GeminiRestTransformer geminiTransformer,
            RecipeValidationService validationService,
            RecipePostProcessor postProcessor) {
        return new ValidatingTransformerService(geminiTransformer, validationService, postProcessor);
    }

    @Bean
    @Qualifier("youtubeValidatingTransformer")
    public ValidatingTransformerService youtubeValidatingTransformer(
            @Qualifier("youtubeTransformer") YouTubeTransformer youtubeTransformer,
            RecipeValidationService validationService,
            RecipePostProcessor postProcessor) {
        return new ValidatingTransformerService(youtubeTransformer, validationService, postProcessor);
    }
}
