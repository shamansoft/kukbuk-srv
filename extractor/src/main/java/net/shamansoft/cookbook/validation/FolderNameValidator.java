package net.shamansoft.cookbook.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator for {@link ValidFolderName} annotation.
 * <p>
 * Implements Google Drive folder name validation rules:
 * <ul>
 *   <li>null or empty is valid (will use default folder name from config)</li>
 *   <li>Whitespace-only strings are invalid</li>
 *   <li>Maximum length is 255 characters (Google Drive limit)</li>
 *   <li>Reserved names "." and ".." are invalid</li>
 *   <li>All other characters including Unicode and special chars are valid</li>
 * </ul>
 */
public class FolderNameValidator implements ConstraintValidator<ValidFolderName, String> {

    private static final int MAX_LENGTH = 255;
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._-]+(?<!\\.)$");

    @Override
    public boolean isValid(String folderName, ConstraintValidatorContext context) {
        // null or empty is valid - will use default folder name
        if (folderName == null || folderName.isBlank()) {
            return true;
        }

        String trimmed = folderName.trim();

        // After trimming, must have content
        if (trimmed.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Folder name cannot contain only whitespace"
            ).addConstraintViolation();
            return false;
        }

        // Length check (Google Drive limit)
        if (trimmed.length() > MAX_LENGTH) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Folder name must not exceed " + MAX_LENGTH + " characters"
            ).addConstraintViolation();
            return false;
        }

        // Reserved names check
        if (".".equals(trimmed) || "..".equals(trimmed)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Folder name cannot be '.' or '..'"
            ).addConstraintViolation();
            return false;
        }

        if (!PATTERN.matcher(trimmed).matches()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Folder name contains invalid characters"
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
