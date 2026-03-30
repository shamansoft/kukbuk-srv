# Implementation: dumpLLMResponse Flag

**Date:** 2026-02-01
**Status:** ✅ Completed
**Related:** Debug Dump Flags Feature

## Summary

Added `dumpLLMResponse` flag to dump the raw JSON response from the Gemini API before it's parsed into a Recipe object. This helps debug LLM responses, validation failures, and unexpected outputs.

## Implementation Details

### 1. Extended GeminiResponse Record

Added `rawResponse` field to capture the raw JSON text from Gemini:

```java
public record GeminiResponse<T>(Code code, T data, String errorMessage, String rawResponse) {
    public static <T> GeminiResponse<T> success(T data, String rawResponse) {
        return new GeminiResponse<>(Code.SUCCESS, data, null, rawResponse);
    }
}
```

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiResponse.java`

### 2. Updated GeminiClient

Modified to capture and pass the raw JSON content:

```java
String jsonContent = jsonBuilder.toString();
// ...
return GeminiResponse.success(objectMapper.readValue(jsonContent, clazz), jsonContent);
```

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiClient.java:113`

### 3. Extended Transformer.Response

Added `rawLlmResponse` field to carry the raw response through the transformation pipeline:

```java
record Response(boolean isRecipe, Recipe recipe, String rawLlmResponse) {
    public static Response withRawResponse(boolean isRecipe, Recipe recipe, String rawLlmResponse) {
        return new Response(isRecipe, recipe, rawLlmResponse);
    }
}
```

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/Transformer.java:22-43`

### 4. Updated GeminiRestTransformer

Modified to use the new factory method and pass the raw response:

```java
return Transformer.Response.withRawResponse(
    geminiResponse.data().isRecipe(),
    geminiResponse.data(),
    geminiResponse.rawResponse()
);
```

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformer.java:45-50`

### 5. Added Dump Flag to TestTransformRequest

```java
Boolean dumpLLMResponse,

public boolean isDumpLLMResponse() {
    return dumpLLMResponse != null && dumpLLMResponse;
}
```

**File:** `extractor/src/main/java/net/shamansoft/cookbook/dto/TestTransformRequest.java:27,64-66`

### 6. Added Dump Path to ProcessingMetadata

```java
private String dumpedLLMResponsePath;
```

**File:** `extractor/src/main/java/net/shamansoft/cookbook/dto/TestTransformResponse.java:55`

### 7. Integrated Dump Call in DebugController

Added dump logic after Gemini transformation:

```java
// Dump raw LLM response if flag enabled
if (request.isDumpLLMResponse() && dumpService != null && transformResponse.rawLlmResponse() != null) {
    try {
        String path = dumpService.dump(transformResponse.rawLlmResponse(), "llm-response", "json", contentHash);
        if (metadataBuilder != null && path != null) {
            metadataBuilder.dumpedLLMResponsePath(path);
        }
        log.info("📝 Dumped LLM response to: {}", path);
    } catch (Exception e) {
        log.warn("Failed to dump LLM response: {}", e.getMessage());
    }
}
```

**File:** `extractor/src/main/java/net/shamansoft/cookbook/controller/DebugController.java:218-228`

### 8. Fixed Test Suite

Updated all test mocks to include the new `rawResponse` parameter:

```java
.thenReturn(GeminiResponse.success(expectedRecipe, "{\"is_recipe\": true}"));
```

**File:** `extractor/src/test/java/net/shamansoft/cookbook/service/gemini/GeminiRestTransformerTest.java`

## Usage

### Request Example

```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
    "returnFormat": "yaml",
    "verbose": true,
    "dumpLLMResponse": true
  }'
```

### Expected Output

**File created:**
```
/tmp/sar-srv/dumps/llm-response-abc123def456-1738425601500.json
```

**File contents (example):**
```json
{
  "is_recipe": true,
  "schema_version": "1.0.0",
  "api_version": "1.0.0",
  "metadata": {
    "title": "Alysia's Basic Meat Lasagna",
    "source_url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
    ...
  },
  "ingredients": [...],
  "instructions": [...]
}
```

**Response metadata:**
```json
{
  "isRecipe": true,
  "recipeYaml": "...",
  "metadata": {
    "dumpedLLMResponsePath": "/tmp/sar-srv/dumps/llm-response-abc123def456-1738425601500.json",
    ...
  }
}
```

## Use Cases

### 1. Debug Validation Failures

When recipe validation fails, inspect the raw LLM response to see what the LLM actually returned:

```bash
# Enable both LLM response dump and verbose mode
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "...",
    "verbose": true,
    "dumpLLMResponse": true
  }'

# Check the raw response
cat /tmp/sar-srv/dumps/llm-response-*.json | jq .
```

### 2. Compare LLM Output vs Parsed Result

Dump both the raw LLM response and the final result:

```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "...",
    "dumpLLMResponse": true,
    "dumpResultJson": true
  }'

# Compare
diff <(cat /tmp/sar-srv/dumps/llm-response-*.json | jq -S .) \
     <(cat /tmp/sar-srv/dumps/result-json-*.json | jq -S .)
```

### 3. Analyze LLM Behavior

Study how the LLM interprets different HTML structures:

```bash
# Test with different cleaning strategies
for strategy in auto structured section content raw; do
  curl -X POST http://localhost:8080/debug/v1/recipes \
    -H "Content-Type: application/json" \
    -d "{
      \"url\": \"...\",
      \"cleanHtml\": \"$strategy\",
      \"dumpCleanedHtml\": true,
      \"dumpLLMResponse\": true
    }"
done

# Compare LLM responses for each strategy
ls -lh /tmp/sar-srv/dumps/llm-response-*
```

## Changes Summary

### Files Modified (7)

1. **GeminiResponse.java** - Added `rawResponse` field
2. **GeminiClient.java** - Captures and passes raw JSON
3. **Transformer.java** - Extended Response record with `rawLlmResponse`
4. **GeminiRestTransformer.java** - Uses new factory method
5. **TestTransformRequest.java** - Added `dumpLLMResponse` flag
6. **TestTransformResponse.java** - Added `dumpedLLMResponsePath` field
7. **DebugController.java** - Integrated dump call

### Files Modified (Tests)

1. **GeminiRestTransformerTest.java** - Updated 4 test mocks to include `rawResponse`

### Files Modified (Documentation)

1. **DUMP_FLAGS_TESTING.md** - Updated with `dumpLLMResponse` examples

## Build Verification

```bash
✅ ./gradlew :cookbook:compileJava     # SUCCESS
✅ ./gradlew :cookbook:test            # SUCCESS (all tests pass)
```

## Key Features

✅ **Non-breaking change**: All existing code continues to work
✅ **Opt-in design**: Only dumps when flag is explicitly enabled
✅ **Raw response preservation**: No parsing/transformation of LLM output
✅ **Error handling**: Failures don't break recipe extraction
✅ **Test coverage**: All existing tests updated and passing

## Benefits

1. **Better debugging**: See exactly what the LLM returned
2. **Validation troubleshooting**: Understand why validation fails
3. **LLM behavior analysis**: Study how different inputs affect output
4. **Schema compliance**: Verify LLM follows JSON schema
5. **Regression detection**: Compare LLM outputs across versions

## Related Documentation

- **Testing Guide:** `docs/postman/DUMP_FLAGS_TESTING.md`
- **Original Feature:** `docs/plans/20260201-debug-dump-flags-implementation.md`
- **Transformer Interface:** `service/Transformer.java`
- **Gemini Client:** `service/gemini/GeminiClient.java`

## Future Enhancements (Optional)

1. **Timing metrics**: Add LLM response time to metadata
2. **Token usage**: Capture and log token counts from Gemini
3. **Multi-part responses**: Better handling of chunked responses
4. **Diff tool**: Compare LLM responses across runs
5. **Auto-analysis**: Detect common LLM failure patterns
