You can configure Gemini models to generate responses that adhere to a provided JSON Schema. This ensures predictable, type-safe results and simplifies extracting structured data from unstructured text.

Using structured outputs is ideal for:

Data extraction: Pull specific information like names and dates from text.
Structured classification: Classify text into predefined categories.

```
curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent" \
    -H "x-goog-api-key: $GEMINI_API_KEY" \
    -H 'Content-Type: application/json' \
    -X POST \
    -d '{
      "contents": [{
        "parts":[
          { "text": "Please extract the recipe from the following text.\nThe user wants to make delicious chocolate chip cookies.\nThey need 2 and 1/4 cups of all-purpose flour, 1 teaspoon of baking soda,\n1 teaspoon of salt, 1 cup of unsalted butter (softened), 3/4 cup of granulated sugar,\n3/4 cup of packed brown sugar, 1 teaspoon of vanilla extract, and 2 large eggs.\nFor the best part, they will need 2 cups of semisweet chocolate chips.\nFirst, preheat the oven to 375°F (190°C). Then, in a small bowl, whisk together the flour,\nbaking soda, and salt. In a large bowl, cream together the butter, granulated sugar, and brown sugar\nuntil light and fluffy. Beat in the vanilla and eggs, one at a time. Gradually beat in the dry\ningredients until just combined. Finally, stir in the chocolate chips. Drop by rounded tablespoons\nonto ungreased baking sheets and bake for 9 to 11 minutes." }
        ]
      }],
      "generationConfig": {
        "responseMimeType": "application/json",
        "responseJsonSchema": {
          "type": "object",
          "properties": {
            "recipe_name": {
              "type": "string",
              "description": "The name of the recipe."
            },
            "prep_time_minutes": {
                "type": "integer",
                "description": "Optional time in minutes to prepare the recipe."
            },
            "ingredients": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "name": { "type": "string", "description": "Name of the ingredient."},
                  "quantity": { "type": "string", "description": "Quantity of the ingredient, including units."}
                },
                "required": ["name", "quantity"]
              }
            },
            "instructions": {
              "type": "array",
              "items": { "type": "string" }
            }
          },
          "required": ["recipe_name", "ingredients", "instructions"]
        }
      }
    }'
  ```

Example response
```
{
  "recipe_name": "Delicious Chocolate Chip Cookies",
  "ingredients": [
    {
      "name": "all-purpose flour",
      "quantity": "2 and 1/4 cups"
    },
    {
      "name": "baking soda",
      "quantity": "1 teaspoon"
    },
    {
      "name": "salt",
      "quantity": "1 teaspoon"
    },
    {
      "name": "unsalted butter (softened)",
      "quantity": "1 cup"
    },
    {
      "name": "granulated sugar",
      "quantity": "3/4 cup"
    },
    {
      "name": "packed brown sugar",
      "quantity": "3/4 cup"
    },
    {
      "name": "vanilla extract",
      "quantity": "1 teaspoon"
    },
    {
      "name": "large eggs",
      "quantity": "2"
    },
    {
      "name": "semisweet chocolate chips",
      "quantity": "2 cups"
    }
  ],
  "instructions": [
    "Preheat the oven to 375°F (190°C).",
    "In a small bowl, whisk together the flour, baking soda, and salt.",
    "In a large bowl, cream together the butter, granulated sugar, and brown sugar until light and fluffy.",
    "Beat in the vanilla and eggs, one at a time.",
    "Gradually beat in the dry ingredients until just combined.",
    "Stir in the chocolate chips.",
    "Drop by rounded tablespoons onto ungreased baking sheets and bake for 9 to 11 minutes."
  ]
}
```

# JSON schema support
To generate a JSON object, set the response_mime_type in the generation configuration to application/json and provide a response_json_schema. The schema must be a valid JSON Schema that describes the desired output format.

The model will then generate a response that is a syntactically valid JSON string matching the provided schema. When using structured outputs, the model will produce outputs in the same order as the keys in the schema.

Gemini's structured output mode supports a subset of the JSON Schema specification.

The following values of type are supported:

string: For text.
number: For floating-point numbers.
integer: For whole numbers.
boolean: For true/false values.
object: For structured data with key-value pairs.
## array: For lists of items.
null: To allow a property to be null, include "null" in the type array (e.g., {"type": ["string", "null"]}).
These descriptive properties help guide the model:

title: A short description of a property.
description: A longer and more detailed description of a property.
## Type-specific properties
### For object values:

properties: An object where each key is a property name and each value is a schema for that property.
required: An array of strings, listing which properties are mandatory.
additionalProperties: Controls whether properties not listed in properties are allowed. Can be a boolean or a schema.
For string values:

enum: Lists a specific set of possible strings for classification tasks.
format: Specifies a syntax for the string, such as date-time, date, time.
For number and integer values:

enum: Lists a specific set of possible numeric values.
minimum: The minimum inclusive value.
maximum: The maximum inclusive value.

### For array values:
items: Defines the schema for all items in the array.
prefixItems: Defines a list of schemas for the first N items, allowing for tuple-like structures.
minItems: The minimum number of items in the array.
maxItems: The maximum number of items in the array.

# Best practices
Clear descriptions: Use the description field in your schema to provide clear instructions to the model about what each property represents. This is crucial for guiding the model's output.
Strong typing: Use specific types (integer, string, enum) whenever possible. If a parameter has a limited set of valid values, use an enum.
Prompt engineering: Clearly state in your prompt what you want the model to do. For example, "Extract the following information from the text..." or "Classify this feedback according to the provided schema...".
Validation: While structured output guarantees syntactically correct JSON, it does not guarantee the values are semantically correct. Always validate the final output in your application code before using it.
Error handling: Implement robust error handling in your application to gracefully manage cases where the model's output, while schema-compliant, may not meet your business logic requirements.

# Limitations
Schema subset: Not all features of the JSON Schema specification are supported. The model ignores unsupported properties.
Schema complexity: The API may reject very large or deeply nested schemas. If you encounter errors, try simplifying your schema by shortening property names, reducing nesting, or limiting the number of constraints.
