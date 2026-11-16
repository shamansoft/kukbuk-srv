package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.exception.InvalidRecipeUpdateException;

/**
 * Normalizes and checks the note a user sets on a recipe.
 */
public final class UserNotes {

    public static final int MAX_LENGTH = 5000;

    private UserNotes() {
    }

    /**
     * Strips trailing whitespace and keeps the inner text as is. {@code null} and blank text mean "no note"
     * and give {@code ""}.
     *
     * @throws InvalidRecipeUpdateException if the note is longer than {@value #MAX_LENGTH} characters
     */
    public static String normalize(String notes) {
        if (notes == null) {
            return "";
        }
        String result = notes.stripTrailing();
        if (result.length() > MAX_LENGTH) {
            throw new InvalidRecipeUpdateException("userNotes: at most " + MAX_LENGTH + " characters allowed");
        }
        return result;
    }
}
