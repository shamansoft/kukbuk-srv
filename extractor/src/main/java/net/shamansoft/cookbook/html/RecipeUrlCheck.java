package net.shamansoft.cookbook.html;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.JevClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Before spending a markdown.new call (limited per day) on a page the site would not serve us, asks Jev (TypeSafe)
 * whether the URL alone looks like a page with one recipe. Only used on that path: once the page itself is in
 * hand, {@code RecipePageScreen} judges its content, which says far more than the URL.
 *
 * <p>The threshold is low on purpose. Skipping a real recipe loses a save the user wanted; fetching a category page
 * costs one markdown.new call. Recipe URLs scored 0.90-0.99 and home, category, gallery, search, shop and article
 * URLs 0.01-0.03 when this was tested (2026-11-14); URLs that reveal nothing ({@code ?p=1234}) land in between.
 * Any Jev failure counts as "fetch it", so the check never blocks a save.
 */
@Service
@Slf4j
public class RecipeUrlCheck {

    static final String QUESTION_ID = "recipe_url";

    private static final Map<String, Object> QUESTIONS = Map.of(QUESTION_ID, Map.of(
            "type", "noul",
            "instructions", "Judging only from `url`, is it the address of a page with the recipe for one dish?",
            "criteria", Map.of(
                    "true", "The path names a dish or is a recipe entry, for example /recipe/16354/easy-meatloaf"
                            + " or /pumpkin-soup/, in any language.",
                    "false", "A home page, category, collection, gallery, search or tag page, a list of recipes,"
                            + " a news story or review, a product or shop page, an about page, or a page not"
                            + " about food.")));

    private final JevClient jev;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final double threshold;

    public RecipeUrlCheck(JevClient jev, MeterRegistry meterRegistry,
                          @Value("${cookbook.typesafe.url-check.enabled:true}") boolean enabled,
                          @Value("${cookbook.typesafe.api-key:}") String apiKey,
                          @Value("${cookbook.typesafe.url-check.threshold:0.1}") double threshold) {
        this.jev = jev;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.threshold = threshold;
        log.info("Recipe URL check before markdown.new (Jev): {}",
                this.enabled ? "enabled, threshold " + threshold : "disabled");
    }

    /** {@code false} only when Jev is confident the URL is not a recipe page. */
    public boolean worthFetching(String url) {
        if (!enabled) {
            return true;
        }
        String outcome;
        boolean fetch;
        try {
            double recipe = jev.ask(Map.of("url", url), QUESTIONS).noul(QUESTION_ID);
            fetch = recipe >= threshold;
            outcome = fetch ? "fetch" : "skip";
            log.info("Jev URL check - URL: {}, recipe page: {}, {}", url, String.format("%.2f", recipe),
                    fetch ? "fetching via markdown.new" : "not fetching");
        } catch (JevClient.JevException e) {
            log.warn("Jev URL check failed, fetching anyway - URL: {}: {}", url, e.getMessage());
            fetch = true;
            outcome = "error";
        }
        meterRegistry.counter("recipe.url_check", "outcome", outcome).increment();
        return fetch;
    }
}
