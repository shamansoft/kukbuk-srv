# Recipe SDK

A Java library for parsing and serializing recipe YAML files according to the recipe schema specification.

## Features

- **Model classes**: Java 21 records representing recipe structure
- **YAML parsing**: Parse YAML files/strings into Recipe objects
- **YAML serialization**: Serialize Recipe objects to YAML format
- **Validation**: Bean Validation (Jakarta) annotations for data integrity
- **GraalVM Native Image**: Pre-configured reflection metadata for native compilation

## Usage

### Parsing YAML to Recipe

```java
import net.shamansoft.recipe.parser.YamlRecipeParser;
import net.shamansoft.recipe.model.Recipe;

YamlRecipeParser parser = new YamlRecipeParser();

// From String
Recipe recipe = parser.parse(yamlString);

// From File
Recipe recipe = parser.parse(new File("recipe.yaml"));
Recipe recipe = parser.parse(Path.of("recipe.yaml"));

// From InputStream
Recipe recipe = parser.parse(inputStream);
```

### Serializing Recipe to YAML

```java
import net.shamansoft.recipe.parser.RecipeSerializer;

RecipeSerializer serializer = new RecipeSerializer();

// To String
String yaml = serializer.serialize(recipe);

// To File
serializer.serialize(recipe, new File("output.yaml"));
serializer.serialize(recipe, Path.of("output.yaml"));

// To OutputStream or Writer
serializer.serialize(recipe, outputStream);
serializer.serialize(recipe, writer);
```

### Building Recipe Objects

```java
import net.shamansoft.recipe.model.*;
import java.time.LocalDate;
import java.util.List;

Recipe recipe = new Recipe(
    "1.0.0",  // schemaVersion
    "1.0.0",  // recipeVersion
    new RecipeMetadata(
        "Chocolate Chip Cookies",
        "https://example.com/recipe",
        "John Doe",
        "en",
        LocalDate.now(),
        List.of("dessert"),
        List.of("cookies", "chocolate"),
        12,  // servings
        "15m",
        "12m",
        "27m",
        "easy",
        null
    ),
    "Delicious homemade chocolate chip cookies",
    List.of(
        new Ingredient("flour", 2.0, "cups", null, false, null, "main"),
        new Ingredient("chocolate chips", 1.0, "cup", null, false, null, "main")
    ),
    List.of("mixing bowl", "baking sheet"),
    List.of(
        new Instruction(1, "Mix dry ingredients", null, null, null),
        new Instruction(2, "Bake at 350°F", "12m", "350°F", null)
    ),
    null,  // nutrition
    "Store in airtight container",
    null   // storage
);
```

## GraalVM Native Image Support

The library includes pre-configured reflection metadata in `META-INF/native-image/` for GraalVM native image compilation.

### For Spring Boot Applications

If you're using this library in a Spring Boot application, create a runtime hints class:

```java
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;

@ImportRuntimeHints(RecipeHints.class)
@Configuration
public class RecipeConfiguration {
    // Your configuration
}

class RecipeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(
            net.shamansoft.recipe.model.Recipe.class,
            builder -> builder.withMembers(
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS
            )
        );
        // Register other model classes as needed
    }
}
```

## Dependencies

- Jackson YAML (2.18.2)
- Jakarta Bean Validation API (3.1.0)
- Java 21+

## Model Classes

- `Recipe` - Main recipe container
- `RecipeMetadata` - Title, author, source, servings, timing, etc.
- `Ingredient` - Ingredient with optional substitutions and component grouping
- `Instruction` - Step-by-step instructions with optional media
- `Media` - Sealed interface for images and videos
- `Nutrition` - Nutritional information
- `Storage` - Storage instructions
