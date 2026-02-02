# Quick Reference - Postman Collection

## 🚀 30 Second Setup

```bash
# 1. Start server
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'

# 2. Import in Postman
# File → Import → docs/postman/Cookbook-API-Test.postman_collection.json
# File → Import → docs/postman/Local-Development.postman_environment.json
```

## 📍 Endpoint

```
POST http://localhost:8080/debug/v1/recipes
```

## 📤 Basic Request

```json
{
  "url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
  "returnFormat": "yaml",
  "cleanHtml": "auto",
  "skipCache": false,
  "verbose": false
}
```

OR

```json
{
  "text": "<html><h1>Recipe</h1>...</html>",
  "returnFormat": "yaml"
}
```

## 📥 Response Types

### Plain Text YAML (Default)
```
Content-Type: text/plain
Body: YAML formatted recipe
Headers: X-Is-Recipe, X-Recipe-Title
```

### JSON with Recipe Object
```
Content-Type: application/json
Body: {"isRecipe": true, "recipeJson": {...}}
Headers: X-Is-Recipe, X-Recipe-Title
```

### JSON with Metadata
```
Content-Type: application/json
Body: {"isRecipe": true, "recipeJson": {...}, "metadata": {...}}
(When verbose=true)
```

## 🎛️ Quick Parameters

| Parameter | Values | Default | Use Case |
|-----------|--------|---------|----------|
| `url` | string | - | Fetch recipe from website |
| `text` | string | - | Use raw HTML directly |
| `returnFormat` | yaml, json | yaml | Output format |
| `verbose` | true, false | false | Get processing metadata |
| `skipCache` | true, false | false | Force fresh extraction |
| `cleanHtml` | auto, raw, structured, section, content, disabled | auto | HTML preprocessing |

**Required**: Either `url` OR `text`

## 📊 Common Requests

### 1. Simple YAML from URL
```json
{"url": "https://example.com/recipe"}
```

### 2. JSON with Metadata
```json
{
  "url": "https://example.com/recipe",
  "verbose": true
}
```

### 3. Test HTML Cleaning
```json
{
  "text": "<html>...</html>",
  "cleanHtml": "structured",
  "verbose": true
}
```

### 4. Force Fresh (Skip Cache)
```json
{
  "url": "https://example.com/recipe",
  "skipCache": true
}
```

### 5. Get JSON Instead of YAML
```json
{
  "url": "https://example.com/recipe",
  "returnFormat": "json"
}
```

## 🔍 Understanding Verbose Metadata

```json
"metadata": {
  "totalProcessingTimeMs": 2500,
  "cacheHit": false,
  "htmlCleanupStrategy": "AUTO",
  "originalHtmlSize": 125000,
  "cleanedHtmlSize": 35000,
  "reductionRatio": 0.72
}
```

| Field | What It Means |
|-------|---------------|
| `totalProcessingTimeMs` | Total time for request |
| `cacheHit` | Was result from cache? |
| `htmlCleanupStrategy` | Which strategy was used |
| `originalHtmlSize` | HTML size before cleaning |
| `cleanedHtmlSize` | HTML size after cleaning |
| `reductionRatio` | Compression ratio (0-1) |

## ❌ Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| 400 Bad Request | Missing url/text | Add either url or text field |
| 404 Not Found | Wrong endpoint | Use `/debug/v1/recipes` |
| 404 Not Found | Production profile | Run with `--spring.profiles.active=local` |
| 500 Internal Error | Gemini API issue | Check COOKBOOK_GEMINI_API_KEY env var |
| Connection refused | Server not running | Run: `./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'` |

## 📋 Test Organization

```
Cookbook API - Debug Endpoints
├── Debug Transform - Basic
│   ├── Transform from URL (YAML)
│   ├── Transform from HTML Text
│   └── Transform Non-Recipe Content
├── Debug Transform - JSON Format
│   ├── Transform with JSON Response
│   └── Complex Recipe with JSON
├── Debug Transform - Advanced Options
│   ├── Verbose Mode with Metadata
│   ├── Skip Cache
│   └── Different HTML Strategies
└── Error Handling
    ├── Missing URL/Text Error
    └── Error with Verbose Mode
```

## 🎯 Key Testing Patterns

### Pattern 1: Test New URL
1. Copy URL
2. Use "Transform from URL" request
3. Change URL in request body
4. Send

### Pattern 2: Debug Issue
1. Use same request
2. Set `verbose: true`
3. Check metadata for where time is spent
4. Adjust `cleanHtml` strategy if needed

### Pattern 3: Compare Strategies
1. Use "Different HTML Strategies" request
2. Change `cleanHtml` value
3. Send multiple times
4. Compare `htmlCleanupStrategy` in metadata

### Pattern 4: Check Cache
1. Send request with `skipCache: false`
2. Note `cacheHit: false`
3. Send exact same request again
4. Note `cacheHit: true` (if verbose)

## 💡 Pro Tips

- **Slow Response?** Check `verbose: true` metadata for bottleneck
- **Want JSON?** Set `returnFormat: "json"` OR `verbose: true`
- **Keep Fresh Results?** Use `skipCache: true` each time
- **Test Strategy?** Use `cleanHtml: "structured"` etc with `verbose: true`
- **Save Response?** Right-click request → "Save as cURL" or use Postman save feature

## 🔗 More Help

- **Full Docs**: `README.md`
- **What's New**: `UPDATES.md`
- **Architecture**: `../../docs/REFACTORING_SUMMARY.md`
- **Controller Code**: `../../extractor/src/main/java/net/shamansoft/cookbook/controller/TestController.java`

## ✅ Checklist - First Time Setup

- [ ] Server running with `--spring.profiles.active=local`
- [ ] Collection imported in Postman
- [ ] Environment imported in Postman
- [ ] Environment selected (top-right dropdown)
- [ ] COOKBOOK_GEMINI_API_KEY env var set
- [ ] Run "Transform from URL" request
- [ ] See 200 OK response with recipe

## 🆘 Still Stuck?

1. Check server logs: `./gradlew :cookbook:bootRun`
2. Try curl: `curl -X POST http://localhost:8080/debug/v1/recipes -H "Content-Type: application/json" -d '{"url":"..."}'`
3. Check if profile is active: look for `TestController` in logs
4. Verify COOKBOOK_GEMINI_API_KEY is set
5. Try with `verbose: true` to see detailed error metadata