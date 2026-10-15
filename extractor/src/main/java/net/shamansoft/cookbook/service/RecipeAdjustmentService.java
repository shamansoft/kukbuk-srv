package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.recipe.model.Ingredient;
import net.shamansoft.recipe.model.Instruction;
import net.shamansoft.recipe.model.Nutrition;
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

    /** Model output meaning "no value": a JSON null written as text, or an empty placeholder. */
    private static final Set<String> NULL_WORDS = Set.of("null", "none", "n/a", "na", "-");

    /** Plurals, abbreviations and spellings folded to the prompt's unit whitelist. */
    private static final Map<String, String> UNIT_SYNONYMS = Map.ofEntries(
            Map.entry("tsp", "teaspoon"), Map.entry("tsp.", "teaspoon"), Map.entry("tsps", "teaspoon"),
            Map.entry("teaspoons", "teaspoon"),
            Map.entry("tbsp", "tablespoon"), Map.entry("tbsp.", "tablespoon"), Map.entry("tbsps", "tablespoon"),
            Map.entry("tbs", "tablespoon"), Map.entry("tablespoons", "tablespoon"),
            Map.entry("cups", "cup"), Map.entry("c.", "cup"),
            Map.entry("gram", "g"), Map.entry("grams", "g"), Map.entry("gr", "g"),
            Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"),
            Map.entry("milligram", "mg"), Map.entry("milligrams", "mg"),
            Map.entry("milliliter", "ml"), Map.entry("milliliters", "ml"),
            Map.entry("millilitre", "ml"), Map.entry("millilitres", "ml"),
            Map.entry("liter", "l"), Map.entry("liters", "l"), Map.entry("litre", "l"), Map.entry("litres", "l"),
            Map.entry("ounce", "oz"), Map.entry("ounces", "oz"), Map.entry("oz.", "oz"),
            Map.entry("pounds", "lb"), Map.entry("lbs", "lb"), Map.entry("lb.", "lb"), Map.entry("lbs.", "lb"),
            Map.entry("cloves", "clove"), Map.entry("slices", "slice"), Map.entry("sprigs", "sprig"),
            Map.entry("pieces", "piece"), Map.entry("pcs", "piece"),
            Map.entry("bottles", "bottle"), Map.entry("jars", "jar"), Map.entry("cans", "can"),
            Map.entry("bars", "bar"), Map.entry("packages", "package"), Map.entry("boxes", "box"),
            Map.entry("bags", "bag"), Map.entry("pints", "pint"), Map.entry("quarts", "quart"),
            Map.entry("gallons", "gallon")
    );

    /** Bare-count placeholders: a count with no unit is stored as unit null. */
    private static final Set<String> COUNT_PLACEHOLDERS = Set.of("each", "whole", "ea", "unit", "units");

    /**
     * Words that describe an unmeasured quantity. Used as a unit they become amount null, unit null,
     * with the word kept in notes.
     */
    private static final Set<String> UNQUANTIFIED = Set.of(
            "pinch", "pinches", "dash", "dashes", "handful", "handfuls", "splash", "drizzle",
            "to taste", "as needed", "as required", "some");

    /** A unit that starts with a number, fraction or range: "⅓ cup", "1 1/2 cups", "2-3 tbsp". */
    private static final Pattern AMOUNT_IN_UNIT = Pattern.compile(
            "^((?:\\d+(?:\\.\\d+)?\\s*-\\s*\\d+(?:\\.\\d+)?)|(?:\\d+\\s+)?\\d+/\\d+|\\d*\\s*[½¼¾⅓⅔⅛⅜⅝⅞]|\\d+(?:\\.\\d+)?)\\s*([^\\d\\s].*)$");
    private static final Pattern RANGE = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)$");

    private static final double[] THIRDS_AND_SIXTHS = {1.0 / 6, 1.0 / 3, 2.0 / 3, 5.0 / 6};
    // Same slack as the app's AmountFormatter uses to show a decimal as a fraction
    private static final double FRACTION_TOLERANCE = 0.01;

    /** Sizes that models put in the unit field for bare counts. */
    private static final Set<String> SIZE_WORDS = Set.of("small", "medium", "large", "extra large", "extra-large", "big");

    /** Splits "cup, frozen" / "cup (80 ml)" into the unit and the trailing text. */
    private static final Pattern UNIT_WITH_TRAILING_TEXT = Pattern.compile("^([^,(]+?)\\s*[,(]\\s*(.*?)\\)?\\s*$");

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
                adjustNutrition(recipe.nutrition()),
                recipe.notes(),
                recipe.storage()
        );
    }

    /** A nutrition object with no values (the model must emit the key) is the same as none. */
    Nutrition adjustNutrition(Nutrition n) {
        if (n == null) return null;
        boolean empty = n.calories() == null && n.protein() == null && n.carbohydrates() == null
                && n.fat() == null && n.fiber() == null && n.sugar() == null && n.sodium() == null;
        return empty ? null : n;
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

    Ingredient adjustIngredient(Ingredient ingredient) {
        String amount = snapToThirds(normalizeAmount(nullIfPlaceholder(ingredient.amount())));
        String unit = normalizeUnit(nullIfPlaceholder(ingredient.unit()));
        String notes = ingredient.notes();

        // Number written into the unit ("⅓ cup", "2-3 tbsp"): move it to amount, a range as its midpoint
        if (amount == null && unit != null) {
            Matcher m = AMOUNT_IN_UNIT.matcher(unit.trim());
            if (m.matches()) {
                String number = m.group(1).trim();
                Matcher range = RANGE.matcher(number);
                if (range.matches()) {
                    amount = formatDecimal((Double.parseDouble(range.group(1)) + Double.parseDouble(range.group(2))) / 2);
                    notes = appendNote(notes, unit.trim());
                } else {
                    amount = snapToThirds(normalizeAmount(number));
                }
                unit = m.group(2);
            }
        }

        // "cup, frozen, optional" / "clove (minced)" -> unit "cup"/"clove", the rest to notes
        if (unit != null && !isKnownUnit(unit)) {
            Matcher m = UNIT_WITH_TRAILING_TEXT.matcher(unit);
            if (m.matches() && isKnownUnit(canonicalUnit(m.group(1)))) {
                unit = canonicalUnit(m.group(1));
                notes = appendNote(notes, m.group(2));
            }
        }
        unit = canonicalUnit(unit);

        // "pinch", "to taste" used as a unit: the source gave no measurable quantity
        if (unit != null && UNQUANTIFIED.contains(unit)) {
            String wording = isZero(amount) || amount == null || "1".equals(amount) ? unit : amount + " " + unit;
            notes = appendNote(notes, wording);
            amount = null;
            unit = null;
        }

        // Amount 0 is how some models write "no amount"; a unit without an amount means nothing
        if (isZero(amount)) {
            amount = null;
        }
        if (amount == null) {
            unit = null;
        }
        if (unit != null && COUNT_PLACEHOLDERS.contains(unit)) {
            unit = null;
        }
        // "2 large eggs" written as unit "large": a bare count, the size belongs in notes
        if (unit != null && SIZE_WORDS.contains(unit)) {
            notes = appendNote(notes, unit);
            unit = null;
        }

        return new Ingredient(
                ingredient.item(),
                amount,
                unit,
                notes,
                ingredient.optional(),
                ingredient.substitutions(),
                normalizeComponent(ingredient.component())
        );
    }

    /** Units the whitelist allows; a unit with trailing text is only split when its head is one of these. */
    private static final Set<String> KNOWN_UNITS = Set.of(
            "g", "kg", "mg", "oz", "lb", "pound",
            "ml", "l", "cup", "tablespoon", "teaspoon", "fl oz", "pint", "quart", "gallon",
            "clove", "slice", "sprig", "piece", "stick",
            "bottle", "jar", "can", "bar", "package", "box", "bag",
            "°c", "°f", "dl", "cl");

    private boolean isKnownUnit(String unit) {
        return unit != null && (KNOWN_UNITS.contains(unit) || COUNT_PLACEHOLDERS.contains(unit)
                || UNQUANTIFIED.contains(unit) || SIZE_WORDS.contains(unit));
    }

    private String canonicalUnit(String unit) {
        if (unit == null) return null;
        String u = unit.trim().toLowerCase();
        return UNIT_SYNONYMS.getOrDefault(u, u);
    }

    private static String nullIfPlaceholder(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() || NULL_WORDS.contains(t.toLowerCase()) ? null : value;
    }

    /**
     * Models truncate thirds and sixths (⅔ → 0.66, 1⅔ → 1.66). Store them with three decimals so that
     * scaling in the app lands on whole numbers (0.667 × 3 = 2.001, not 1.98).
     */
    String snapToThirds(String amount) {
        if (amount == null) return null;
        double value;
        try {
            value = Double.parseDouble(amount.trim());
        } catch (NumberFormatException e) {
            return amount;
        }
        double whole = Math.floor(value);
        for (double fraction : THIRDS_AND_SIXTHS) {
            if (Math.abs(value - whole - fraction) < FRACTION_TOLERANCE) {
                return formatDecimal(whole + fraction);
            }
        }
        return amount;
    }

    private static boolean isZero(String amount) {
        if (amount == null) return false;
        try {
            return Double.parseDouble(amount.trim()) == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String appendNote(String notes, String addition) {
        if (addition == null || addition.isBlank()) return notes;
        String add = addition.trim();
        if (notes == null || notes.isBlank()) return add;
        if (notes.toLowerCase().contains(add.toLowerCase())) return notes;
        return notes + ", " + add;
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
