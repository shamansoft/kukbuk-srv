package net.shamansoft.cookbook.html.strategy;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackStrategyTest {

    @Test
    void clean_returnsOriginalOrEmpty() {
        FallbackStrategy s = new FallbackStrategy();

        Optional<String> present = s.clean("<html></html>");
        assertThat(present).isPresent();
        assertThat(present.get()).contains("<html>");

        Optional<String> empty = s.clean(null);
        assertThat(empty).isEmpty();
    }

    @Test
    void getStrategy_returnsFallback() {
        FallbackStrategy s = new FallbackStrategy();
        assertThat(s.getStrategy()).isEqualTo(Strategy.FALLBACK);
    }
}
