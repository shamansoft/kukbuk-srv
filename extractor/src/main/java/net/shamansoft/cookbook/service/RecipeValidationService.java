package net.shamansoft.cookbook.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.RecipeParseException;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import net.shamansoft.recipe.parser.RecipeSerializer;
import net.shamansoft.recipe.parser.YamlRecipeParser;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for validating and normalizing recipe YAML.
 * Parses YAML using recipe-sdk and re-serializes to ensure consistency.
 */
@Service
@Slf4j
public class RecipeValidationService {

    private final YamlRecipeParser parser;
    private final RecipeSerializer serializer;
    private final Validator validator;

    public RecipeValidationService() {
        this.parser = new YamlRecipeParser();
        this.serializer = new RecipeSerializer();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    /**
     * Validates and normalizes recipe YAML.
     * Parses the YAML and re-serializes it to ensure it conforms to the recipe schema.
     *
     * @param yaml the YAML string to validate
     * @return ValidationResult containing the normalized YAML or error details
     */
    public ValidationResult validate(String yaml) {
        try {
            // Parse YAML to Recipe object - this validates the structure
            Recipe recipe = parser.parse(yaml);
            log.debug("Successfully parsed recipe: {}", recipe.metadata().title());

            // Validate using Bean Validation annotations
            Set<ConstraintViolation<Recipe>> violations = validator.validate(recipe);
            if (!violations.isEmpty()) {
                String validationErrors = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining(", "));
                log.warn("Recipe validation failed: {}", validationErrors);
                return ValidationResult.failure("Validation failed: " + validationErrors);
            }

            // Serialize back to YAML - this normalizes the format
            String normalizedYaml = serializer.serialize(recipe);
            log.debug("Successfully normalized recipe YAML");

            return ValidationResult.success(normalizedYaml);
        } catch (RecipeParseException e) {
            log.warn("Recipe validation failed during parsing: {}", e.getMessage());
            return ValidationResult.failure(buildParseErrorMessage(e));
        } catch (RecipeSerializeException e) {
            log.error("Recipe validation failed during serialization: {}", e.getMessage(), e);
            return ValidationResult.failure(buildSerializeErrorMessage(e));
        }
    }

    private String buildParseErrorMessage(RecipeParseException e) {
        StringBuilder message = new StringBuilder("YAML parsing failed. ");

        // Extract root cause for better feedback
        Throwable rootCause = e.getCause();
        if (rootCause != null) {
            message.append("Error: ").append(rootCause.getMessage());

            // Add more specific guidance based on error type
            String errorMsg = rootCause.getMessage();
            if (errorMsg != null) {
                if (errorMsg.contains("Unrecognized field")) {
                    message.append("\n\nThe YAML contains fields that are not part of the recipe schema.");
                } else if (errorMsg.contains("missing") || errorMsg.contains("required")) {
                    message.append("\n\nSome required fields are missing from the recipe.");
                } else if (errorMsg.contains("Cannot deserialize")) {
                    message.append("\n\nThe value type for a field is incorrect.");
                }
            }
        } else {
            message.append(e.getMessage());
        }

        return message.toString();
    }

    private String buildSerializeErrorMessage(RecipeSerializeException e) {
        return "Failed to serialize the parsed recipe back to YAML: " + e.getMessage();
    }

    /**
     * Result of recipe validation containing either the normalized YAML or error details.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String normalizedYaml;
        private final String errorMessage;

        private ValidationResult(boolean valid, String normalizedYaml, String errorMessage) {
            this.valid = valid;
            this.normalizedYaml = normalizedYaml;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success(String normalizedYaml) {
            return new ValidationResult(true, normalizedYaml, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, null, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getNormalizedYaml() {
            return normalizedYaml;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
