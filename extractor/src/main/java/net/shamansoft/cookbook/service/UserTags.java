package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.exception.InvalidRecipeUpdateException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Normalizes and checks the tags a user sets on a recipe.
 */
public final class UserTags {

    public static final int MAX_TAGS = 30;
    public static final int MAX_TAG_LENGTH = 40;

    private UserTags() {
    }

    /**
     * Trims and lowercases each tag, drops empty ones and duplicates (first occurrence wins).
     * {@code null} means "no tags".
     *
     * @throws InvalidRecipeUpdateException if a tag is longer than {@value #MAX_TAG_LENGTH} characters
     *                                      or there are more than {@value #MAX_TAGS} tags
     */
    public static List<String> normalize(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        tags.stream()
                .filter(Objects::nonNull)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .forEach(tag -> {
                    if (tag.length() > MAX_TAG_LENGTH) {
                        throw new InvalidRecipeUpdateException(
                                "userTags: each tag must be at most " + MAX_TAG_LENGTH + " characters");
                    }
                    result.add(tag);
                });
        if (result.size() > MAX_TAGS) {
            throw new InvalidRecipeUpdateException("userTags: at most " + MAX_TAGS + " tags allowed");
        }
        return List.copyOf(result);
    }
}
