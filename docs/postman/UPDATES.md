# Postman Collection Updates

**Date**: 2024
**Purpose**: Update Postman collection to match new TestController implementation

## Summary of Changes

The Postman collection has been completely updated to reflect the new `/debug/v1/recipes` endpoint with enhanced features and configuration options.

## Endpoint Changes

### Old Endpoint
- **Path**: `/v1/recipes/test-transform`
- **Features**: Basic URL/HTML input, plain text YAML output
- **Requests**: 5 basic test scenarios

### New Endpoint
- **Path**: `/debug/v1/recipes`
- **Features**: Full extraction pipeline with caching, preprocessing, metadata tracking
- **Requests**: 13 comprehensive test scenarios organized in 4 groups

## Request Body Updates

### Old Format
```json
{
  "url": "https://example.com/recipe",
  "text": "<html>...</html>"
}
```

### New Format
```json
{
  "url": "https://example.com/recipe",
  "text": "<html>...</html>",
  "returnFormat": "yaml",
  "cleanHtml": "auto",
  "skipCache": false,
  "verbose": false,
  "compression": "gzip"
}
```

**New Parameters**:
- `returnFormat`: Output format ("yaml" or "json")
- `cleanHtml`: HTML preprocessing strategy (auto, structured, section, content, raw, disabled)
- `skipCache`: Bypass recipe caching
- `verbose`: Include detailed processing metadata
- `compression`: Compression type for URL extraction

## Response Format Updates

### Plain Text YAML (Legacy)
```yaml
is_recipe: true
metadata:
  title: "Recipe Title"
ingredients:
  - item: "ingredient"
    amount: "amount"
instructions:
  - step: "instruction"
```

**When Used**: `returnFormat=yaml` and `verbose=false` (default)

### JSON Format (New)
```json
{
  "isRecipe": true,
  "recipeJson": {
    "is_recipe": true,
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

**When Used**: `returnFormat=json` or `verbose=true`

## Test Organization

### Group 1: Debug Transform - Basic (3 requests)
1. Transform from URL (YAML, Plain Text)
2. Transform from HTML Text (YAML, Plain Text)
3. Transform Non-Recipe Content

### Group 2: Debug Transform - JSON Format (2 requests)
1. Transform with JSON Response
2. Complex Recipe with JSON

### Group 3: Debug Transform - Advanced Options (3 requests)
1. Verbose Mode with Processing Metadata
2. Skip Cache - Force Fresh Transformation
3. Different HTML Cleaning Strategies

### Group 4: Error Handling (2 requests)
1. Error - Missing Both URL and Text
2. Error with Verbose Mode

## Key Features Added

### 1. Verbose Mode
- Enable with `verbose: true`
- Always returns JSON format
- Includes detailed processing metrics:
  - Total processing time
  - Cache hit/miss status
  - HTML preprocessing strategy used
  - Original vs cleaned HTML sizes
  - Reduction ratio
  - Gemini transformation time
  - Validation status

### 2. HTML Cleaning Strategies
- Auto (default cascade)
- Structured data extraction
- Section-based extraction
- Content filtering
- Raw (no preprocessing)
- Disabled (same as raw)

### 3. Caching Control
- `skipCache: false` (default) - Use cache if available
- `skipCache: true` - Force fresh transformation

### 4. Flexible Output Format
- `returnFormat: "yaml"` - Plain text YAML (default)
- `returnFormat: "json"` - Structured JSON response

### 5. Enhanced Response Headers
- `X-Is-Recipe`: Indicates if content is a recipe
- `X-Recipe-Title`: Recipe title (when isRecipe=true)
- `Content-Type`: Varies based on output format

## Test Scripts Updated

Each request now includes comprehensive automated tests:

### Basic Tests
- Status code validation
- Content-Type validation
- Response format validation
- Header presence checks

### Advanced Tests
- JSON structure validation
- Array presence checks
- Metadata field validation
- HTML metrics validation

### Error Tests
- 400/500 status code checks
- Error message validation
- Metadata in error responses

## Migration Guide

### For Existing Postman Workflows

**Old Request**:
```json
{
  "url": "https://example.com/recipe"
}
```

**New Request** (equivalent):
```json
{
  "url": "https://example.com/recipe",
  "returnFormat": "yaml",
  "cleanHtml": "auto",
  "skipCache": false,
  "verbose": false
}
```

All parameters except `url`/`text` are optional with sensible defaults.

### To Get New Features

**For Metadata**:
```json
{
  "url": "https://example.com/recipe",
  "verbose": true
}
```

**For JSON Output**:
```json
{
  "url": "https://example.com/recipe",
  "returnFormat": "json"
}
```

**To Test HTML Strategy**:
```json
{
  "text": "<html>...</html>",
  "cleanHtml": "structured",
  "verbose": true
}
```

## Environment Variables

Collection uses environment variable: `baseUrl` (default: http://localhost:8080)

**Example**: To test on different server:
1. Create new Postman environment
2. Set `baseUrl` variable to your server URL
3. Select environment before running requests

## Backward Compatibility

The new endpoint maintains backward compatibility:

- Default behavior (YAML output, auto cleaning, with cache) works same as before
- Legacy clients can ignore new parameters
- Response headers remain unchanged
- Non-recipe detection works identically

## Files Updated

1. **Cookbook-API-Test.postman_collection.json**
   - Updated endpoint path: `/v1/recipes/test-transform` → `/debug/v1/recipes`
   - Added 8 new test scenarios (total: 13)
   - Updated request parameters
   - Enhanced test scripts
   - Better documentation

2. **Local-Development.postman_environment.json**
   - No changes to core variables
   - `baseUrl` still defaults to http://localhost:8080

3. **README.md**
   - Completely rewritten
   - New endpoint documentation
   - Parameter descriptions
   - Response format examples
   - Troubleshooting guide
   - 13 detailed test scenario descriptions

## Testing the Updates

### Quick Test
```bash
# 1. Start server
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'

# 2. Import collection in Postman
# File → Import → Cookbook-API-Test.postman_collection.json

# 3. Run "Transform from URL (YAML, Plain Text)" request
# Should return 200 OK with YAML response
```

### Test Verbose Mode
```bash
# Same as above, but run "Verbose Mode with Processing Metadata" request
# Response should include metadata object with timing and strategy info
```

### Test JSON Format
```bash
# Run any of the JSON Format group requests
# Response should be application/json with structured recipe object
```

## Known Differences from Old Implementation

1. **Endpoint Path**: Changed from `/v1/recipes/test-transform` to `/debug/v1/recipes`
2. **Response Format**: Can now return JSON in addition to YAML
3. **Caching**: New requests are cached by default (can be disabled)
4. **Metadata**: Optional detailed processing information available
5. **HTML Cleaning**: Configurable preprocessing strategies
6. **Content Compression**: Support for compressed URL content

## Future Enhancements

Potential improvements for next iteration:

1. Add pre-request script to set timestamp
2. Add test runner collection for batch execution
3. Add Newman CLI examples for CI/CD integration
4. Add environment for staging server
5. Add contract tests for response schema
6. Add performance baseline assertions

## Questions or Issues

Refer to:
- Main README: `docs/postman/README.md`
- Project guidelines: `CLAUDE.md`
- Refactoring summary: `docs/REFACTORING_SUMMARY.md`
