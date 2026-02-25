package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.model.RecipeMetadata;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Normalizes structurally inconsistent recipe fields before validation runs.
 * Reduces validation retry churn and produces cleaner data.
 */
@Service
@Slf4j
public class RecipeAdjustmentService {

    private static final Set<String> VALID_DIFFICULTIES = Set.of("easy", "medium", "hard");

    private static final Pattern TIME_VALID = Pattern.compile(
            "^(\\d+d\\s*)?(\\d+h\\s*)?(\\d+m)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TIME_DAYS = Pattern.compile(
            "(\\d+)\\s*days?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_HOURS = Pattern.compile(
            "(\\d+)\\s*hours?\\b|(\\d+)\\s*hrs?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_MINUTES = Pattern.compile(
            "(\\d+)\\s*mins?(?:utes?)?\\b", Pattern.CASE_INSENSITIVE);

    private static final Map<Character, String> UNICODE_FRACTIONS = Map.of(
            '½', "0.5",
            '¼', "0.25",
            '¾', "0.75",
            '⅓', "0.333",
            '⅔', "0.667",
            '⅛', "0.125",
            '⅜', "0.375",
            '⅝', "0.625",
            '⅞', "0.875"
    );

    // Matches "whole unicodeFraction" or just "unicodeFraction"
    private static final Pattern MIXED_UNICODE_FRACTION = Pattern.compile(
            "^(\\d+)\\s*([½¼¾⅓⅔⅛⅜⅝⅞])$|^([½¼¾⅓⅔⅛⅜⅝⅞])$");

    // Matches "a/b" or "whole a/b"
    private static final Pattern ASCII_FRACTION = Pattern.compile(
            "^(\\d+)\\s+(\\d+)/(\\d+)$|^(\\d+)/(\\d+)$");

    public Recipe adjust(Recipe recipe) {
        if (recipe == null) return null;

        log.debug("Adjusting recipe: {}", recipe.metadata() != null ? recipe.metadata().title() : "<no title>");

        List<Instruction> adjustedInstructions = adjustInstructions(recipe.instructions());
        List<Ingredient> adjustedIngredients = adjustIngredients(recipe.ingredients());
        RecipeMetadata adjustedMetadata = adjustMetadata(recipe.metadata());

        return new Recipe(
                recipe.isRecipe(),
                recipe.schemaVersion(),
                recipe.recipeVersion(),
                adjustedMetadata,
                recipe.description(),
                adjustedIngredients,
                recipe.equipment(),
                adjustedInstructions,
                recipe.nutrition(),
                recipe.notes(),
                recipe.storage()
        );
    }

    // -------------------------------------------------------------------------
    // Instructions
    // -------------------------------------------------------------------------

    private List<Instruction> adjustInstructions(List<Instruction> instructions) {
        if (instructions == null || instructions.isEmpty()) return instructions;
        List<Instruction> withNormalizedTimes = instructions.stream()
                .map(i -> new Instruction(i.step(), i.description(), normalizeTime(i.time()), i.temperature(), i.media()))
                .toList();
        return renumberSteps(withNormalizedTimes);
    }

    private List<Instruction> renumberSteps(List<Instruction> instructions) {
        if (!needsRenumber(instructions)) return instructions;
        log.debug("Renumbering {} instruction steps", instructions.size());
        return IntStream.range(0, instructions.size())
                .mapToObj(i -> new Instruction(
                        i + 1,
                        instructions.get(i).description(),
                        instructions.get(i).time(),
                        instructions.get(i).temperature(),
                        instructions.get(i).media()
                ))
                .toList();
    }

    private boolean needsRenumber(List<Instruction> instructions) {
        for (int i = 0; i < instructions.size(); i++) {
            Integer step = instructions.get(i).step();
            if (step == null || step != i + 1) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Time normalization
    // -------------------------------------------------------------------------

    String normalizeTime(String time) {
        if (time == null || time.isBlank()) return time;
        String trimmed = time.trim();
        if (TIME_VALID.matcher(trimmed).matches()) return trimmed;

        String result = trimmed;
        // Replace days
        Matcher daysMatcher = TIME_DAYS.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (daysMatcher.find()) {
            daysMatcher.appendReplacement(sb, daysMatcher.group(1) + "d");
        }
        daysMatcher.appendTail(sb);
        result = sb.toString().trim();

        // Replace hours
        Matcher hoursMatcher = TIME_HOURS.matcher(result);
        sb = new StringBuilder();
        while (hoursMatcher.find()) {
            String digits = hoursMatcher.group(1) != null ? hoursMatcher.group(1) : hoursMatcher.group(2);
            hoursMatcher.appendReplacement(sb, digits + "h");
        }
        hoursMatcher.appendTail(sb);
        result = sb.toString().trim();

        // Replace minutes
        Matcher minsMatcher = TIME_MINUTES.matcher(result);
        sb = new StringBuilder();
        while (minsMatcher.find()) {
            minsMatcher.appendReplacement(sb, minsMatcher.group(1) + "m");
        }
        minsMatcher.appendTail(sb);
        result = sb.toString().trim();

        // Validate result
        if (TIME_VALID.matcher(result).matches()) {
            log.debug("Normalized time '{}' → '{}'", time, result);
            return result;
        }

        log.debug("Could not normalize time '{}', keeping original", time);
        return trimmed;
    }

    // -------------------------------------------------------------------------
    // Ingredients
    // -------------------------------------------------------------------------

    private List<Ingredient> adjustIngredients(List<Ingredient> ingredients) {
        if (ingredients == null) return null;
        return ingredients.stream().map(this::adjustIngredient).toList();
    }

    private Ingredient adjustIngredient(Ingredient ingredient) {
        String amount = normalizeAmount(ingredient.amount());
        String unit = normalizeUnit(ingredient.unit());
        String component = normalizeComponent(ingredient.component());
        return new Ingredient(
                ingredient.item(),
                amount,
                unit,
                ingredient.notes(),
                ingredient.optional(),
                ingredient.substitutions(),
                component
        );
    }

    String normalizeAmount(String amount) {
        if (amount == null) return null;
        String trimmed = amount.trim();
        if (trimmed.isEmpty()) return amount;

        // Try unicode fraction (with optional whole number prefix)
        Matcher unicodeMatcher = MIXED_UNICODE_FRACTION.matcher(trimmed);
        if (unicodeMatcher.matches()) {
            String whole = unicodeMatcher.group(1);
            char fractionChar = unicodeMatcher.group(2) != null
                    ? unicodeMatcher.group(2).charAt(0)
                    : unicodeMatcher.group(3).charAt(0);
            String fracValue = UNICODE_FRACTIONS.get(fractionChar);
            if (fracValue != null) {
                if (whole != null) {
                    double result = Integer.parseInt(whole) + Double.parseDouble(fracValue);
                    return formatDecimal(result);
                }
                return fracValue;
            }
        }

        // Try ASCII fraction: "1 1/2" or "1/2"
        Matcher asciiFraction = ASCII_FRACTION.matcher(trimmed);
        if (asciiFraction.matches()) {
            if (asciiFraction.group(1) != null) {
                // "whole num/denom"
                int whole = Integer.parseInt(asciiFraction.group(1));
                int num = Integer.parseInt(asciiFraction.group(2));
                int denom = Integer.parseInt(asciiFraction.group(3));
                if (denom != 0) {
                    double result = whole + (double) num / denom;
                    return formatDecimal(result);
                }
            } else {
                // "num/denom"
                int num = Integer.parseInt(asciiFraction.group(4));
                int denom = Integer.parseInt(asciiFraction.group(5));
                if (denom != 0) {
                    double result = (double) num / denom;
                    return formatDecimal(result);
                }
            }
        }

        return amount;
    }

    private String formatDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        // Remove trailing zeros
        String s = String.format("%.3f", value);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    String normalizeUnit(String unit) {
        if (unit == null) return null;
        String trimmed = unit.trim();
        if (trimmed.isEmpty()) return unit;
        return trimmed.toLowerCase();
    }

    String normalizeComponent(String component) {
        if (component == null || component.isBlank()) return "main";
        return component;
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    private RecipeMetadata adjustMetadata(RecipeMetadata metadata) {
        if (metadata == null) return null;
        return new RecipeMetadata(
                metadata.title(),
                metadata.source(),
                metadata.author(),
                metadata.language(),
                metadata.dateCreated(),
                normalizeTags(metadata.category()),
                normalizeTags(metadata.tags()),
                metadata.servings(),
                normalizeTime(metadata.prepTime()),
                normalizeTime(metadata.cookTime()),
                normalizeTime(metadata.totalTime()),
                normalizeDifficulty(metadata.difficulty()),
                metadata.coverImage()
        );
    }

    String normalizeDifficulty(String difficulty) {
        if (difficulty == null) return null;
        String normalized = difficulty.trim().toLowerCase();
        return VALID_DIFFICULTIES.contains(normalized) ? normalized : null;
    }

    List<String> normalizeTags(List<String> tags) {
        if (tags == null) return null;
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase())
                .toList();
    }
}
