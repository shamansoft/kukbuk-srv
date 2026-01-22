package net.shamansoft.cookbook.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import net.shamansoft.recipe.parser.RecipeSerializer;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for validating Recipe objects and converting them to YAML.
 * Uses Jakarta Bean Validation to validate Recipe objects against their constraints.
 */
@Service
@Slf4j
public class RecipeValidationService {

    private final RecipeSerializer serializer;
    private final Validator validator;

    public RecipeValidationService() {
        this.serializer = new RecipeSerializer();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    /**
     * Validates a Recipe object using Bean Validation.
     *
     * @param recipe the Recipe object to validate
     * @return ValidationResult containing the validated recipe or error details
     */
    public ValidationResult validate(Recipe recipe) {
        if (recipe == null) {
            log.warn("Recipe validation called with null recipe");
            return ValidationResult.failure("Recipe object is null");
        }

        log.debug("Validating recipe - Title: '{}', Has ingredients: {}, Has instructions: {}",
                recipe.metadata() != null ? recipe.metadata().title() : "N/A",
                recipe.ingredients() != null && !recipe.ingredients().isEmpty(),
                recipe.instructions() != null && !recipe.instructions().isEmpty());

        // Validate using Bean Validation annotations
        Set<ConstraintViolation<Recipe>> violations = validator.validate(recipe);
        if (!violations.isEmpty()) {
            String validationErrors = formatValidationErrors(violations);
            log.warn("Recipe validation failed with {} constraint violations: {}",
                    violations.size(),
                    validationErrors);
            return ValidationResult.failure(validationErrors);
        }

        log.debug("Recipe validation successful");
        return ValidationResult.success(recipe);
    }

    /**
     * Converts a Recipe object to YAML format.
     *
     * @param recipe the Recipe object to serialize
     * @return the YAML string representation
     * @throws RecipeSerializeException if serialization fails
     */
    public String toYaml(Recipe recipe) throws RecipeSerializeException {
        if (recipe == null) {
            throw new IllegalArgumentException("Cannot serialize null recipe to YAML");
        }
        return serializer.serialize(recipe);
    }

    /**
     * Formats validation errors into a structured message suitable for feedback to Gemini.
     * Each error is formatted as "path: message" for clarity.
     *
     * @param violations the set of constraint violations
     * @return formatted error message
     */
    private String formatValidationErrors(Set<ConstraintViolation<Recipe>> violations) {
        return violations.stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    String message = v.getMessage();
                    Object invalidValue = v.getInvalidValue();

                    // Include the invalid value if it's present and not null
                    if (invalidValue != null) {
                        return String.format("Field '%s': %s (current value: %s)", path, message, invalidValue);
                    } else {
                        return String.format("Field '%s': %s", path, message);
                    }
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Result of recipe validation containing either the validated Recipe or error details.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final Recipe recipe;
        private final String errorMessage;

        private ValidationResult(boolean valid, Recipe recipe, String errorMessage) {
            this.valid = valid;
            this.recipe = recipe;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success(Recipe recipe) {
            if (recipe == null) {
                throw new IllegalArgumentException("Recipe cannot be null for a successful validation result");
            }
            return new ValidationResult(true, recipe, null);
        }

        public static ValidationResult failure(String errorMessage) {
            if (errorMessage == null || errorMessage.isBlank()) {
                throw new IllegalArgumentException("Error message cannot be null or blank for a failed validation result");
            }
            return new ValidationResult(false, null, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public Recipe getRecipe() {
            return recipe;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}