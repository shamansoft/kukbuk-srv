package net.shamansoft.cookbook.html;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.shamansoft.cookbook.client.JevClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeUrlCheckTest {

    private static final String URL = "https://www.allrecipes.com/recipes/95/pasta-and-noodles/";

    @Mock
    private JevClient jev;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    private RecipeUrlCheck check(boolean enabled, String apiKey) {
        return new RecipeUrlCheck(jev, meterRegistry, enabled, apiKey, 0.1);
    }

    private void jevSays(double recipe) {
        when(jev.ask(eq(Map.of("url", URL)), anyMap())).thenReturn(new JevClient.Answers("jev-1.14.0",
                Map.of(RecipeUrlCheck.QUESTION_ID, Map.of("type", "noul", "noul", recipe)), 40));
    }

    @Test
    void skipsBelowThreshold() {
        jevSays(0.03);

        assertThat(check(true, "key").worthFetching(URL)).isFalse();
        assertThat(meterRegistry.counter("recipe.url_check", "outcome", "skip").count()).isEqualTo(1.0);
    }

    @Test
    void fetchesAtThreshold() {
        jevSays(0.1);

        assertThat(check(true, "key").worthFetching(URL)).isTrue();
        assertThat(meterRegistry.counter("recipe.url_check", "outcome", "fetch").count()).isEqualTo(1.0);
    }

    @Test
    void fetchesWhenJevFails() {
        when(jev.ask(anyMap(), anyMap())).thenThrow(new JevClient.JevException("timeout", null));

        assertThat(check(true, "key").worthFetching(URL)).isTrue();
        assertThat(meterRegistry.counter("recipe.url_check", "outcome", "error").count()).isEqualTo(1.0);
    }

    @Test
    void offWithoutKeyOrWhenDisabled() {
        assertThat(check(true, "").worthFetching(URL)).isTrue();
        assertThat(check(false, "key").worthFetching(URL)).isTrue();
        verifyNoInteractions(jev);
    }
}
