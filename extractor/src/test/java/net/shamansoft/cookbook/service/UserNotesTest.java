package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.exception.InvalidRecipeUpdateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserNotesTest {

    @Test
    void keepsInnerTextAndStripsTrailingWhitespaceOnly() {
        assertThat(UserNotes.normalize("  Less sugar.\n\n  Bake 5 min more.  \n\n")).isEqualTo("  Less sugar.\n\n  Bake 5 min more.");
    }

    @Test
    void nullOrBlankMeansNoNote() {
        assertThat(UserNotes.normalize(null)).isEmpty();
        assertThat(UserNotes.normalize("")).isEmpty();
        assertThat(UserNotes.normalize(" \n\t ")).isEmpty();
    }

    @Test
    void acceptsLimitAndRejectsOneOver() {
        assertThat(UserNotes.normalize("x".repeat(UserNotes.MAX_LENGTH))).hasSize(UserNotes.MAX_LENGTH);
        assertThatThrownBy(() -> UserNotes.normalize("x".repeat(UserNotes.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidRecipeUpdateException.class);
    }
}
