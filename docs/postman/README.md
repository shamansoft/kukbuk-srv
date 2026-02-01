# Postman Collection - Cookbook API Debug Endpoints

This directory contains Postman collections for testing the Cookbook API in local/development environments.

## 📋 Files

### Collections

#### `Cookbook-API-Test.postman_collection.json`

Comprehensive collection for the `/debug/v1/recipes` endpoint that allows unauthenticated testing of the full recipe
extraction pipeline.

**⚠️ Important**: These endpoints are **ONLY available in local/dev environments**, NOT in production.

### Environments

#### `Local-Development.postman_environment.json`

Pre-configured environment for local development with useful variables:

- `baseUrl` - Local server URL (http://localhost:8080)
- `apiVersion` - API version (v1)
- `profile` - Active Spring profile (local)
- `sampleRecipeUrl` - Example recipe URLs for testing

## 🚀 Quick Start

### 1. Start the Local Server

Run the Spring Boot application with the `local` profile:

```bash
# From the project root (/Users/alexey/dev/sar/sar-srv/)
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'
```

The server will start on `http://localhost:8080` by default.

### 2. Import Collection and Environment into Postman

**Import Collection:**
1. Open Postman
2. Click **Import** button (top left)
3. Select the file: `Cookbook-API-Test.postman_collection.json`
4. The collection will appear in your workspace

**Import Environment (Recommended):**

1. Click **Import** button again
2. Select the file: `Local-Development.postman_environment.json`
3. Select the environment from the dropdown in the top-right corner (next to eye icon)

### 3. Run Requests

The collection includes 13 test requests organized in 4 groups:

- **Debug Transform - Basic**: Simple transformation scenarios
- **Debug Transform - JSON Format**: JSON response format tests
- **Debug Transform - Advanced Options**: Verbose mode, caching, strategies
- **Error Handling**: Error scenarios and validation

## 📖 Endpoint Documentation

### POST `/debug/v1/recipes`

Full recipe extraction pipeline testing endpoint.

**Profile**: Only available in `local` and `dev` profiles (disabled in `prod` and `gcp`)

#### Request Body

```json
{
  "url": "https://www.example.com/recipe",
  "text": "<html>...</html>",
  "returnFormat": "yaml",
  "cleanHtml": "auto",
  "skipCache": false,
  "verbose": false,
  "compression": "gzip"
}
```

**Fields:**

| Field          | Type    | Default  | Description                          |
|----------------|---------|----------|--------------------------------------|
| `url`          | string  | optional | URL to fetch recipe from             |
| `text`         | string  | optional | Raw HTML text to transform           |
| `returnFormat` | string  | "yaml"   | Output format: "yaml" or "json"      |
| `cleanHtml`    | string  | "auto"   | HTML preprocessing strategy          |
| `skipCache`    | boolean | false    | Skip recipe cache lookup/storage     |
| `verbose`      | boolean | false    | Include detailed processing metadata |
| `compression`  | string  | optional | Compression type for URL extraction  |

**Note**: Either `url` OR `text` must be provided (not both)

#### HTML Cleaning Strategies

The `cleanHtml` parameter controls HTML preprocessing:

| Strategy     | Description                                                |
|--------------|------------------------------------------------------------|
| `auto`       | Default cascade: structured → section → content → fallback |
| `structured` | Extract using structured data (Schema.org, JSON-LD)        |
| `section`    | Extract using semantic HTML sections                       |
| `content`    | Extract main content area                                  |
| `raw`        | No preprocessing, pass HTML as-is                          |
| `disabled`   | Same as `raw`                                              |

#### Response Formats

**Success Response (200 OK):**

Plain text YAML (default):
```yaml
is_recipe: true
schema_version: "1.0.0"
recipe_version: "1.0.0"
metadata:
  title: "Classic Lasagna"
  servings: 8
  source: "https://example.com"
  # ... more fields
ingredients:
  - item: "ground beef"
    amount: "2 lbs"
  # ... more ingredients
instructions:
  - step: "Preheat oven to 375°F"
  # ... more steps
```

JSON format (when `returnFormat=json` or `verbose=true`):

```json
{
  "isRecipe": true,
  "recipeJson": {
    "is_recipe": true,
    "schema_version": "1.0.0",
    "metadata": { ... },
    "ingredients": [ ... ],
    "instructions": [ ... ]
  },
  "metadata": {
    "totalProcessingTimeMs": 2500,
    "cacheHit": false,
    "htmlCleanupStrategy": "AUTO",
    "originalHtmlSize": 125000,
    "cleanedHtmlSize": 35000,
    "reductionRatio": 0.72,
    "transformationTimeMs": 2300,
    "validationPassed": true
  }
}
```

**Non-Recipe Response (200 OK):**

Plain text (YAML format):
```yaml
is_recipe: false
# Gemini determined this content is not a cooking recipe
```

JSON format:
```json
{
  "isRecipe": false,
  "metadata": { ... }
}
```

**Error Response (400 Bad Request):**

```json
{
  "error": "Either 'url' or 'text' field is required"
}
```

#### Response Headers

| Header           | Value              | Condition                   |
|------------------|--------------------|-----------------------------|
| `Content-Type`   | `text/plain`       | YAML output                 |
| `Content-Type`   | `application/json` | JSON output or verbose=true |
| `X-Is-Recipe`    | `true` or `false`  | Always present              |
| `X-Recipe-Title` | Recipe title       | Only when isRecipe=true     |

## 🧪 Test Scenarios

### 1. Basic - Transform from URL (YAML)

Fetch recipe from a real website, return as plain text YAML.

**Use Case**: Simple recipe extraction

### 2. Basic - Transform from HTML Text

Process raw HTML string directly.

**Use Case**: Testing custom HTML, edge cases

### 3. Basic - Transform Non-Recipe Content

Test Gemini's ability to detect non-recipe pages.

**Use Case**: Validate recipe detection logic

### 4. JSON - Transform with JSON Response

Same extraction but return structured JSON.

**Use Case**: Integration with APIs, programmatic processing

### 5. JSON - Complex Recipe

Test with a detailed, multi-section recipe.

**Use Case**: Validate handling of complex structures

### 6. Advanced - Verbose Mode with Metadata

Enable detailed processing information.

**Returns**:

- Processing time breakdown
- Cache hit/miss info
- HTML preprocessing metrics
- HTML size reduction ratio
- Gemini model used
- Validation status

**Use Case**: Debugging, performance analysis, understanding extraction process

### 7. Advanced - Skip Cache

Force fresh transformation, bypass cached results.

**Use Case**: Testing after model updates, re-extracting recipes

### 8. Advanced - HTML Cleaning Strategies

Test different HTML preprocessing approaches.

**Returns**: Which strategy was used and effectiveness metrics

**Use Case**: Optimize extraction for specific website types

### 9. Error - Missing URL and Text

Validation test for required fields.

**Expected**: 400 Bad Request with error message

### 10. Error with Verbose Mode

Error handling with detailed metadata.

**Expected**: 500 error with metadata showing when failure occurred

## 📊 Understanding Verbose Metadata

When `verbose=true`, the response includes detailed processing information:

```json
{
  "metadata": {
    "contentHash": "abc123...",
    "cacheHit": false,
    "htmlCleanupStrategy": "AUTO",
    "originalHtmlSize": 125000,
    "cleanedHtmlSize": 35000,
    "reductionRatio": 0.72,
    "transformationTimeMs": 2300,
    "geminiModel": "gemini-2.5-flash-lite",
    "validationPassed": true,
    "totalProcessingTimeMs": 2500
  }
}
```

**Metrics Explained**:

- `contentHash`: Unique hash of input content (used for caching)
- `cacheHit`: Whether result came from cache vs new extraction
- `htmlCleanupStrategy`: Which preprocessing strategy was selected
- `originalHtmlSize`: HTML size before preprocessing (bytes)
- `cleanedHtmlSize`: HTML size after preprocessing (bytes)
- `reductionRatio`: Compression ratio (0-1, lower is more aggressive)
- `transformationTimeMs`: Time spent calling Gemini API
- `geminiModel`: Which Gemini model was used
- `validationPassed`: Whether recipe passed validation
- `totalProcessingTimeMs`: Total request processing time

## 🔍 Troubleshooting

### Error: Connection Refused

**Problem**: Can't connect to `http://localhost:8080`

**Solution**:
- Verify the server is running: `./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'`
- Check the console output for the actual port
- Update the `baseUrl` variable in Postman if using a different port

### Error: 404 Not Found

**Problem**: Endpoint returns 404

**Solution**:
- Ensure you're running with the correct profile: `--spring.profiles.active=local`
- The `TestController` is disabled in production profiles (`prod`, `gcp`)
- Verify the URL is exactly: `/debug/v1/recipes`

### Error: 500 Internal Server Error

**Problem**: Server error during transformation

**Solution**:
- Check server logs for detailed error messages
- Ensure `COOKBOOK_GEMINI_API_KEY` environment variable is set
- Verify HTML content is valid
- Check if URL is accessible (try in browser first)
- Set `verbose=true` to get error details in response metadata

### Error: Cache Issues

**Problem**: Getting same cached result repeatedly

**Solution**:

- Set `skipCache=true` to force fresh transformation
- Cache is based on content hash, so same URL/text returns cached result
- Use `verbose=true` to see if result came from cache

## 🔐 Environment Variables

The server requires these environment variables:

```bash
export COOKBOOK_GEMINI_API_KEY="your-gemini-api-key"
export COOKBOOK_GOOGLE_OAUTH_ID="your-google-oauth-client-id"  # Optional for debug endpoint
```

## ⚙️ Profile Configuration

The `TestController` is controlled by Spring profiles:

| Profile | Debug Endpoints Available? |
|---------|----------------------------|
| `local` | ✅ Yes                      |
| `dev`   | ✅ Yes                      |
| `prod`  | ❌ No                       |
| `gcp`   | ❌ No                       |

**Profile annotation**: `@Profile("!prod & !gcp")`

This ensures debug endpoints are never exposed in production.

## 💡 Tips

1. **Use Verbose Mode**: Set `verbose=true` to understand what's happening
2. **Save Responses**: Use Postman's "Save Response" to compare different strategies
3. **Environment Variables**: Update `baseUrl` in environment if server uses different port
4. **Test Multiple Strategies**: Try different `cleanHtml` values to find best for website
5. **Check Cache**: Look at `cacheHit` in metadata to understand performance
6. **Compare Formats**: Try both YAML and JSON to see which works better for your use case
7. **Performance Testing**: Use `verbose=true` to analyze processing time breakdown
8. **Batch Testing**: Use Postman's Collection Runner to test multiple recipes at once

## 🐛 Known Issues

- Large HTML pages (>5MB) may timeout - consider chunking or extracting text first
- Some recipe sites use JavaScript rendering - URLs must return server-side HTML
- Rate limiting: Gemini API has rate limits, excessive testing may hit limits
- Cache persists across requests - use `skipCache=true` for clean tests

## 📞 Support

For issues or questions:

- Check the main project documentation in `../../CLAUDE.md`
- Review server logs: `./gradlew :cookbook:bootRun`
- Check `docs/REFACTORING_SUMMARY.md` for architecture details
- Contact: `@khisamutdinov` (code owner)

## 🔗 Related Documentation

- **Main README**: `../../extractor/README.md`
- **API Architecture**: `../../docs/API.md`
- **Project Guidelines**: `../../CLAUDE.md`
- **Refactoring Details**: `../../docs/REFACTORING_SUMMARY.md`
