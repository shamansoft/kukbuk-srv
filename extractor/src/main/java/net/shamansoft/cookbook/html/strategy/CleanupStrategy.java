package net.shamansoft.cookbook.html.strategy;

import java.util.Optional;

public interface CleanupStrategy {
    Optional<String> clean(String html);

    Strategy getStrategy();
}
