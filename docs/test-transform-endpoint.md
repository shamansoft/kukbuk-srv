# Test Transform Endpoint

⚠️ **TEMPORARY ENDPOINT - Remove before production deployment!**

## Overview

This endpoint allows testing the Gemini AI recipe transformation directly without authentication or storage.

**Endpoint:** `POST /v1/recipes/test-transform`

**Authentication:** None required (for testing only!)

## Request Format

```json
{
  "url": "https://example.com/recipe",  // Optional if text is provided
  "text": "<html>...</html>"            // Optional if url is provided
}
```

**Either `url` OR `text` must be provided (not both).**

## Response

- **Success:** Plain text YAML representation of the recipe
- **Headers:**
  - `X-Is-Recipe: true/false` - Whether Gemini identified it as a recipe
  - `X-Recipe-Title: <title>` - Extracted recipe title (if is_recipe=true)

## Usage Examples

### Example 1: Transform from URL

```bash
curl -X POST http://localhost:8080/v1/recipes/test-transform \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/10813/best-chocolate-chip-cookies/"
  }'
```

### Example 2: Transform from HTML text

```bash
curl -X POST http://localhost:8080/v1/recipes/test-transform \
  -H "Content-Type: application/json" \
  -d '{
    "text": "<html><body><h1>Chocolate Chip Cookies</h1><div class=\"ingredients\"><ul><li>2 cups flour</li><li>1 cup sugar</li></ul></div></body></html>"
  }'
```

### Example 3: Using with a local recipe file

```bash
# Save HTML to file
cat > recipe.html <<EOF
<html>
  <body>
    <h1>My Test Recipe</h1>
    <div class="ingredients">
      <ul>
        <li>100g flour</li>
        <li>50g sugar</li>
      </ul>
    </div>
    <div class="instructions">
      <ol>
        <li>Mix ingredients</li>
        <li>Bake at 180°C for 15 minutes</li>
      </ol>
    </div>
  </body>
</html>
EOF

# Transform it
curl -X POST http://localhost:8080/v1/recipes/test-transform \
  -H "Content-Type: application/json" \
  -d "{\"text\": $(cat recipe.html | jq -Rs .)}"
```

### Example 4: Pretty print the YAML output

```bash
curl -X POST http://localhost:8080/v1/recipes/test-transform \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.seriouseats.com/recipes/2013/12/the-best-chocolate-chip-cookies-recipe.html"
  }' | tee recipe-output.yaml
```

## Response Examples

### Success (Recipe Found)

```yaml
is_recipe: true
schema_version: "1.0.0"
recipe_version: "1.0.0"
metadata:
  title: "Best Chocolate Chip Cookies"
  source: "https://www.allrecipes.com/recipe/10813/..."
  author: "Jane Doe"
  language: "en"
  date_created: "2026-01-19"
  servings: 24
  prep_time: "15m"
  cook_time: "10m"
  total_time: "25m"
  difficulty: "easy"
description: "These chocolate chip cookies are crispy on the outside..."
ingredients:
  - item: "flour"
    amount: "2.25"
    unit: "cups"
  - item: "chocolate chips"
    amount: "2"
    unit: "cups"
instructions:
  - step: 1
    description: "Preheat oven to 375°F (190°C)"
  - step: 2
    description: "Mix flour and baking soda in a bowl"
notes: "Store in an airtight container for up to 1 week"
```

### Non-Recipe Content

```yaml
is_recipe: false
# Gemini determined this content is not a cooking recipe
```

## Error Responses

### Missing both url and text

```
HTTP 400 Bad Request
Error: Either 'url' or 'text' field is required
```

### Network Error (invalid URL)

```
HTTP 500 Internal Server Error
(Exception details in logs)
```

## Testing with Different Sources

### Good test URLs:
- https://www.allrecipes.com/recipe/10813/best-chocolate-chip-cookies/
- https://www.seriouseats.com/recipes/
- https://www.bonappetit.com/recipe/
- https://cooking.nytimes.com/recipes/

### Non-recipe URLs (should return is_recipe: false):
- https://www.wikipedia.org/
- https://www.github.com/
- https://www.google.com/

## Environment Requirements

- `COOKBOOK_GEMINI_API_KEY` must be set
- Gemini API must be accessible

## Logs

The endpoint logs detailed information:
```
⚠️  TEST ENDPOINT CALLED - /test-transform - REMOVE BEFORE PRODUCTION!
Fetching HTML from URL: https://example.com/recipe
Fetched HTML - Length: 12345 chars
Transforming HTML with Gemini...
Recipe extracted - Title: 'Test Recipe', Ingredients: 5, Instructions: 3
✅ Transformation successful - YAML length: 890 chars
```

## Cleanup

**Before deploying to production:**

1. Delete `TestTransformRequest.java`
2. Remove the `/test-transform` endpoint from `RecipeController.java`
3. Remove this documentation file

Or use:
```bash
git grep -l "test-transform\|TestTransformRequest" | xargs rm
```
