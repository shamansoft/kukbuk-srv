# Debug Dump Flags - Testing Guide

## Overview

The DebugController now supports dumping intermediate processing states to disk for debugging purposes. This feature is **only active in the local environment** when running with `--spring.profiles.active=local`.

## Configuration

### Dump Directory

Default location: `/tmp/sar-srv/dumps/`

Override via environment variable:
```bash
export COOKBOOK_DEBUG_DUMP_DIR=/custom/path/to/dumps
```

Or in `application.yaml`:
```yaml
cookbook:
  debug:
    dump-dir: /custom/path/to/dumps
```

## Available Dump Flags

| Flag | Description | File Prefix | Extension |
|------|-------------|-------------|-----------|
| `dumpRawHtml` | HTML after extraction (before preprocessing) | `raw-html` | `.html` |
| `dumpExtractedHtml` | Same as raw (for clarity) | `extracted-html` | `.html` |
| `dumpCleanedHtml` | HTML after preprocessing/cleaning | `cleaned-html` | `.html` |
| `dumpLLMResponse` | Raw JSON response from Gemini API | `llm-response` | `.json` |
| `dumpResultJson` | Recipe result in JSON format | `result-json` | `.json` |
| `dumpResultYaml` | Recipe result in YAML format | `result-yaml` | `.yaml` |

## File Naming Convention

All dump files follow this pattern:
```
{prefix}-{contentHash}-{timestamp}.{extension}
```

Example:
```
raw-html-abc123def456-1738425600000.html
cleaned-html-abc123def456-1738425601000.html
llm-response-abc123def456-1738425601500.json
result-yaml-abc123def456-1738425602000.yaml
```

## Test Scenarios

### Scenario 1: All Dumps Enabled with Verbose Output

**Request:**
```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
    "returnFormat": "yaml",
    "verbose": true,
    "dumpRawHtml": true,
    "dumpExtractedHtml": true,
    "dumpCleanedHtml": true,
    "dumpLLMResponse": true,
    "dumpResultJson": false,
    "dumpResultYaml": true
  }'
```

**Expected:**
- 5 files created in `/tmp/sar-srv/dumps/`:
  - `raw-html-{hash}-{timestamp}.html`
  - `extracted-html-{hash}-{timestamp}.html`
  - `cleaned-html-{hash}-{timestamp}.html`
  - `llm-response-{hash}-{timestamp}.json`
  - `result-yaml-{hash}-{timestamp}.yaml`
- Response includes `metadata` section with dump file paths:
  ```json
  {
    "isRecipe": true,
    "recipeYaml": "...",
    "metadata": {
      "dumpedRawHtmlPath": "/tmp/sar-srv/dumps/raw-html-...",
      "dumpedExtractedHtmlPath": "/tmp/sar-srv/dumps/extracted-html-...",
      "dumpedCleanedHtmlPath": "/tmp/sar-srv/dumps/cleaned-html-...",
      "dumpedLLMResponsePath": "/tmp/sar-srv/dumps/llm-response-...",
      "dumpedResultYamlPath": "/tmp/sar-srv/dumps/result-yaml-..."
    }
  }
  ```
- Console logs show dump locations:
  ```
  📝 Dumped raw HTML to: /tmp/sar-srv/dumps/raw-html-...
  📝 Dumped extracted HTML to: /tmp/sar-srv/dumps/extracted-html-...
  📝 Dumped cleaned HTML to: /tmp/sar-srv/dumps/cleaned-html-...
  📝 Dumped LLM response to: /tmp/sar-srv/dumps/llm-response-...
  📝 Dumped result YAML to: /tmp/sar-srv/dumps/result-yaml-...
  ```

### Scenario 2: JSON Format with JSON Dump

**Request:**
```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
    "returnFormat": "json",
    "verbose": true,
    "dumpResultJson": true
  }'
```

**Expected:**
- 1 file created: `result-json-{hash}-{timestamp}.json`
- Response includes:
  ```json
  {
    "isRecipe": true,
    "recipeJson": { ... },
    "metadata": {
      "dumpedResultJsonPath": "/tmp/sar-srv/dumps/result-json-..."
    }
  }
  ```

### Scenario 3: No Dumps (Default Behavior)

**Request:**
```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
    "returnFormat": "yaml"
  }'
```

**Expected:**
- No files created
- Normal response without dump paths
- No dump-related log messages

### Scenario 4: HTML Preprocessing Comparison

Enable dumps to compare HTML at different processing stages:

**Request:**
```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
    "cleanHtml": "auto",
    "verbose": true,
    "dumpRawHtml": true,
    "dumpCleanedHtml": true
  }'
```

**Manual Verification:**
```bash
# List all dumps
ls -lh /tmp/sar-srv/dumps/

# View raw HTML (large, includes ads, scripts, etc.)
cat /tmp/sar-srv/dumps/raw-html-*.html | wc -c

# View cleaned HTML (smaller, recipe content only)
cat /tmp/sar-srv/dumps/cleaned-html-*.html | wc -c

# Compare sizes
diff <(cat /tmp/sar-srv/dumps/raw-html-*.html) \
     <(cat /tmp/sar-srv/dumps/cleaned-html-*.html) | head -50
```

## Verification Steps

### 1. Start Server with Local Profile

```bash
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'
```

**Verify in logs:**
```
DumpService initialized - Dump directory: /tmp/sar-srv/dumps
```

### 2. Send Test Request

Use any of the scenarios above.

### 3. Check Dump Directory

```bash
ls -lh /tmp/sar-srv/dumps/
```

Expected output:
```
total 512K
-rw-r--r-- 1 user user 150K Feb  1 08:00 cleaned-html-abc123-1738425600000.html
-rw-r--r-- 1 user user 350K Feb  1 08:00 raw-html-abc123-1738425600000.html
-rw-r--r-- 1 user user   5K Feb  1 08:00 result-yaml-abc123-1738425602000.yaml
```

### 4. Inspect Dump Files

```bash
# View YAML result
cat /tmp/sar-srv/dumps/result-yaml-*.yaml

# View JSON result (pretty-printed)
cat /tmp/sar-srv/dumps/result-json-*.json | jq .

# View cleaned HTML snippet
head -50 /tmp/sar-srv/dumps/cleaned-html-*.html
```

### 5. Verify File Paths in Response

With `verbose: true`, the response should include dump paths:

```json
{
  "metadata": {
    "dumpedRawHtmlPath": "/tmp/sar-srv/dumps/raw-html-abc123def456-1738425600000.html",
    "dumpedCleanedHtmlPath": "/tmp/sar-srv/dumps/cleaned-html-abc123def456-1738425601000.html",
    "dumpedResultYamlPath": "/tmp/sar-srv/dumps/result-yaml-abc123def456-1738425602000.yaml"
  }
}
```

## Cleanup

Remove old dumps:
```bash
rm -rf /tmp/sar-srv/dumps/*
```

## Error Handling

If dump fails (e.g., permission issues, disk full):
- Request continues normally
- Warning logged: `Failed to dump {type}: {error message}`
- No dump path in metadata
- No impact on recipe extraction

## Security Notes

- **DumpService only active in local environment** via `@Profile("local")`
- DebugController already restricted via `@Profile("!prod & !gcp")`
- Dumps written to `/tmp` (auto-cleaned by OS)
- No sensitive data in filenames (only content hash)
- File paths are absolute for easy access

## Performance

- File I/O is fast for typical recipe sizes:
  - HTML: 10-500KB → ~1-5ms write time
  - JSON/YAML: 1-10KB → <1ms write time
- Dumps are opt-in, no impact when disabled
- No background processing needed

## Postman Collection Update

Add new test case to `Cookbook-API-Test.postman_collection.json`:

```json
{
  "name": "Transform with All Dumps Enabled",
  "request": {
    "method": "POST",
    "header": [
      {
        "key": "Content-Type",
        "value": "application/json"
      }
    ],
    "body": {
      "mode": "raw",
      "raw": "{\n  \"url\": \"https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/\",\n  \"returnFormat\": \"yaml\",\n  \"verbose\": true,\n  \"dumpRawHtml\": true,\n  \"dumpExtractedHtml\": true,\n  \"dumpCleanedHtml\": true,\n  \"dumpResultYaml\": true\n}"
    },
    "url": {
      "raw": "{{baseUrl}}/debug/v1/recipes",
      "host": ["{{baseUrl}}"],
      "path": ["debug", "v1", "recipes"]
    }
  }
}
```

## Troubleshooting

### Issue: DumpService not initialized
**Symptom:** No dumps created, no log message "DumpService initialized"
**Solution:** Ensure server started with `--spring.profiles.active=local`

### Issue: Permission denied writing to dump directory
**Symptom:** Warning in logs: `Failed to dump content`
**Solution:** Check directory permissions or change `COOKBOOK_DEBUG_DUMP_DIR` to writable location

### Issue: Dump paths not in response
**Symptom:** Files created but no `dumpedXxxPath` in metadata
**Solution:** Ensure `verbose: true` in request body

### Issue: No files created even with flags enabled
**Symptom:** Flags enabled, but no dumps in directory
**Solution:** Check if DumpService bean is available (only in local profile)
