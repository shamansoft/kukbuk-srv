package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.exception.InvalidRecipeUpdateException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTagsTest {

    @Test
    void trimsLowercasesDropsEmptyAndDedupesKeepingFirstOrder() {
        assertThat(UserTags.normalize(Arrays.asList(" Weeknight ", "KIDS", null, "", "   ", "weeknight", "Kids", "Été")))
                .containsExactly("weeknight", "kids", "été");
    }

    @Test
    void nullOrEmptyMeansNoTags() {
        assertThat(UserTags.normalize(null)).isEmpty();
        assertThat(UserTags.normalize(List.of())).isEmpty();
    }

    @Test
    void acceptsLimits() {
        String[] tags = new String[UserTags.MAX_TAGS];
        Arrays.setAll(tags, i -> ("t" + i + "x".repeat(UserTags.MAX_TAG_LENGTH)).substring(0, UserTags.MAX_TAG_LENGTH));

        assertThat(UserTags.normalize(List.of(tags))).hasSize(UserTags.MAX_TAGS);
    }

    @Test
    void lengthIsCheckedAfterTrim() {
        assertThat(UserTags.normalize(List.of("  " + "x".repeat(UserTags.MAX_TAG_LENGTH) + "  "))).hasSize(1);
    }

    @Test
    void rejectsTooLongTag() {
        assertThatThrownBy(() -> UserTags.normalize(List.of("x".repeat(UserTags.MAX_TAG_LENGTH + 1))))
                .isInstanceOf(InvalidRecipeUpdateException.class)
                .hasMessageContaining("40");
    }

    @Test
    void rejectsTooManyTags() {
        String[] tags = new String[UserTags.MAX_TAGS + 1];
        Arrays.setAll(tags, i -> "tag" + i);

        assertThatThrownBy(() -> UserTags.normalize(List.of(tags)))
                .isInstanceOf(InvalidRecipeUpdateException.class)
                .hasMessageContaining("30");
    }

    @Test
    void duplicatesDoNotCountTowardsTheLimit() {
        String[] tags = new String[UserTags.MAX_TAGS + 5];
        Arrays.setAll(tags, i -> "tag" + (i % UserTags.MAX_TAGS));

        assertThat(UserTags.normalize(List.of(tags))).hasSize(UserTags.MAX_TAGS);
    }
}
