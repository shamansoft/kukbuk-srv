package net.shamansoft.cookbook.service;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Normalizes recipe language codes to two-letter, lowercase ISO 639-1 ("en", "pl").
 * Region and script are dropped: "en-US", "en/us" and "EN" all become "en".
 */
public final class LanguageCodes {

    private static final Set<String> ISO_639_1 = Set.of(Locale.getISOLanguages());

    private LanguageCodes() {
    }

    /**
     * @param code language code as extracted or stored, e.g. "en", "EN", "en-US", "en/us"
     * @return the ISO 639-1 code, or empty if the value is blank or not a known two-letter code
     * (e.g. "english", "xx")
     */
    public static Optional<String> normalize(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String primary = code.trim().split("[-_/]", 2)[0].toLowerCase(Locale.ROOT);
        return ISO_639_1.contains(primary) ? Optional.of(primary) : Optional.empty();
    }
}
