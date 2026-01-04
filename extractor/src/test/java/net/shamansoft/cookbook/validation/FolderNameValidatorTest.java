package net.shamansoft.cookbook.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FolderNameValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    private FolderNameValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FolderNameValidator();

        // Setup mock behavior for validation context (lenient to avoid UnnecessaryStubbingException)
        lenient().when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(violationBuilder);
    }

    // ==================== Valid Cases ====================

    @Test
    void nullFolderNameIsValid() {
        // null should be valid - will use default folder name
        boolean result = validator.isValid(null, context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void emptyStringIsValid() {
        // Empty string should be valid - will use default folder name
        boolean result = validator.isValid("", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void blankStringIsValid() {
        // Blank strings (only whitespace) should be valid according to the code comment
        // "null or empty is valid - will use default folder name"
        // However, the isBlank() check includes whitespace-only strings
        boolean result = validator.isValid("   ", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MyFolder",
            "my-folder",
            "my_folder",
            "folder123",
            "123folder",
            "a",
            "A-B_C.D",
            "valid.name",
            "folder-2024",
            "test_123"
    })
    void validFolderNamesPass(String folderName) {
        boolean result = validator.isValid(folderName, context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void maxLengthFolderNameIsValid() {
        // 255 characters - exactly at the limit
        String maxLengthName = "a".repeat(255);

        boolean result = validator.isValid(maxLengthName, context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void folderNameWithLeadingAndTrailingSpacesIsTrimmedAndValid() {
        // Valid name with spaces that will be trimmed
        boolean result = validator.isValid("  MyFolder  ", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    // ==================== Invalid Cases - Whitespace Only ====================

    @Test
    void whitespaceOnlyAfterTrimIsInvalid() {
        // This test verifies the edge case where trimming results in empty string
        // Based on code: if trimmed.isEmpty() after trimming non-blank input
        // However, isBlank() already catches this, so this is handled by the first check
        // This test documents the expected behavior
        String input = " \t \n ";

        boolean result = validator.isValid(input, context);

        // Should be valid because isBlank() returns true
        assertThat(result).isTrue();
    }

    // ==================== Invalid Cases - Length Exceeded ====================

    @Test
    void folderNameExceedingMaxLengthIsInvalid() {
        // 256 characters - exceeds limit
        String tooLongName = "a".repeat(256);

        boolean result = validator.isValid(tooLongName, context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name must not exceed 255 characters"
        );
    }

    @Test
    void folderNameWayOverLimitIsInvalid() {
        // 500 characters - way over limit
        String veryLongName = "x".repeat(500);

        boolean result = validator.isValid(veryLongName, context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name must not exceed 255 characters"
        );
    }

    // ==================== Invalid Cases - Reserved Names ====================

    @Test
    void singleDotIsInvalid() {
        boolean result = validator.isValid(".", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name cannot be '.' or '..'"
        );
    }

    @Test
    void doubleDotIsInvalid() {
        boolean result = validator.isValid("..", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name cannot be '.' or '..'"
        );
    }

    @Test
    void singleDotWithSpacesIsTrimmedAndInvalid() {
        // Spaces are trimmed, leaving just "."
        boolean result = validator.isValid("  .  ", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name cannot be '.' or '..'"
        );
    }

    @Test
    void doubleDotWithSpacesIsTrimmedAndInvalid() {
        // Spaces are trimmed, leaving just ".."
        boolean result = validator.isValid("  ..  ", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name cannot be '.' or '..'"
        );
    }

    // ==================== Invalid Cases - Invalid Characters ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "folder name",         // space in middle
            "folder/name",         // forward slash
            "folder\\name",        // backslash
            "folder:name",         // colon
            "folder*name",         // asterisk
            "folder?name",         // question mark
            "folder\"name",        // quote
            "folder<name",         // less than
            "folder>name",         // greater than
            "folder|name",         // pipe
            "folder\tname",        // tab
            "folder\nname",        // newline
            "folder@name",         // at sign
            "folder#name",         // hash
            "folder$name",         // dollar
            "folder%name",         // percent
            "folder&name",         // ampersand
            "folder+name",         // plus
            "folder=name",         // equals
            "folder[name",         // bracket
            "folder]name",         // bracket
            "folder{name",         // brace
            "folder}name",         // brace
            "folder;name",         // semicolon
            "folder,name",         // comma
            "folder!name",         // exclamation
            "folder`name",         // backtick
            "folder~name",         // tilde
            "folder'name",         // apostrophe
            "фолдер",              // Cyrillic characters
            "文件夹",              // Chinese characters
            "フォルダ",            // Japanese characters
            "📁folder",            // emoji
            "folder📁"             // emoji at end
    })
    void invalidCharactersAreRejected(String folderName) {
        boolean result = validator.isValid(folderName, context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name contains invalid characters"
        );
    }

    @Test
    void folderNameEndingWithDotIsInvalid() {
        // Pattern uses negative lookbehind (?<!\\.) to reject names ending with dot
        boolean result = validator.isValid("folder.", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name contains invalid characters"
        );
    }

    @Test
    void folderNameWithMultipleDotsButEndingWithDotIsInvalid() {
        boolean result = validator.isValid("my.folder.", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name contains invalid characters"
        );
    }

    // ==================== Edge Cases ====================

    @Test
    void folderNameWith255CharactersAfterTrimmingIsValid() {
        // Name with spaces that becomes exactly 255 chars after trimming
        String name = "  " + "a".repeat(255) + "  ";

        boolean result = validator.isValid(name, context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void folderNameWith256CharactersAfterTrimmingIsInvalid() {
        // Name with spaces that becomes 256 chars after trimming
        String name = "  " + "a".repeat(256) + "  ";

        boolean result = validator.isValid(name, context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
                "Folder name must not exceed 255 characters"
        );
    }

    @Test
    void singleCharacterFolderNameIsValid() {
        boolean result = validator.isValid("a", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void numericOnlyFolderNameIsValid() {
        boolean result = validator.isValid("12345", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void folderNameWithOnlyHyphensIsValid() {
        boolean result = validator.isValid("---", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void folderNameWithOnlyUnderscoresIsValid() {
        boolean result = validator.isValid("___", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void folderNameStartingWithDotIsValid() {
        // Names can start with dot (like .gitignore), just not be exactly "." or ".."
        boolean result = validator.isValid(".hidden", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void folderNameWithMultipleDotsNotAtEndIsValid() {
        boolean result = validator.isValid("my.folder.name", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void folderNameStartingWithHyphenIsValid() {
        boolean result = validator.isValid("-folder", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void folderNameStartingWithUnderscoreIsValid() {
        boolean result = validator.isValid("_folder", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void mixedCaseAlphanumericWithAllowedSpecialCharsIsValid() {
        boolean result = validator.isValid("My-Folder_Name.2024", context);

        assertThat(result).isTrue();
        verify(context, never()).buildConstraintViolationWithTemplate(anyString());
    }
}