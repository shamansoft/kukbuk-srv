package net.shamansoft.cookbook.html.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fallback strategy that returns the original HTML unchanged.
 * This strategy should be executed last.
 */
@Component
@Slf4j
@Order(Integer.MAX_VALUE)
public class FallbackStrategy implements CleanupStrategy {

    @Override
    public Optional<String> clean(String html) {
        log.debug("FallbackStrategy: returning original HTML");
        return Optional.ofNullable(html);
    }

    @Override
    public Strategy getStrategy() {
        return Strategy.FALLBACK;
    }
}
