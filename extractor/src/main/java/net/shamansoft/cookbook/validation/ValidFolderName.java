package net.shamansoft.cookbook.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation annotation for Google Drive folder names.
 * <p>
 * Validates that folder names meet Google Drive requirements:
 * <ul>
 *   <li>null or empty string is VALID (triggers default folder behavior)</li>
 *   <li>Trimmed length must be between 1 and 255 characters</li>
 *   <li>Cannot be only whitespace</li>
 *   <li>Cannot be exactly "." or ".."</li>
 *   <li>Supports Unicode and special characters</li>
 * </ul>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FolderNameValidator.class)
public @interface ValidFolderName {
    String message() default "Invalid folder name";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
