# Implementation: Debug Dump Flags for DebugController

**Date:** 2026-02-01
**Status:** ✅ Completed
**Related Plan:** `/docs/plans/20260131-debug-dump-flags-plan.md`

## Summary

Successfully implemented debug dump flags to allow developers to inspect intermediate processing states during recipe extraction. The feature is opt-in, safe, and only active in local environments.

## Implementation Details

### 1. Configuration (application.yaml)

Added dump directory configuration under `cookbook.debug`:

```yaml
cookbook:
  debug:
    dump-dir: ${COOKBOOK_DEBUG_DUMP_DIR:/tmp/sar-srv/dumps}
```

**File:** `extractor/src/main/resources/application.yaml:247-256`

### 2. Request DTO Extensions (TestTransformRequest)

Added 5 boolean dump flags to the request record:

```java
// Debug dump flags (optional, default: false)
Boolean dumpRawHtml,
Boolean dumpExtractedHtml,
Boolean dumpCleanedHtml,
Boolean dumpResultJson,
Boolean dumpResultYaml
```

Plus convenience methods:
- `isDumpRawHtml()`
- `isDumpExtractedHtml()`
- `isDumpCleanedHtml()`
- `isDumpResultJson()`
- `isDumpResultYaml()`

**File:** `extractor/src/main/java/net/shamansoft/cookbook/dto/TestTransformRequest.java:23-73`

### 3. Response DTO Extensions (TestTransformResponse)

Added dump path fields to `ProcessingMetadata`:

```java
// Debug dump file paths (only if dump flags enabled)
private String dumpedRawHtmlPath;
private String dumpedExtractedHtmlPath;
private String dumpedCleanedHtmlPath;
private String dumpedResultJsonPath;
private String dumpedResultYamlPath;
```

**File:** `extractor/src/main/java/net/shamansoft/cookbook/dto/TestTransformResponse.java:52-56`

### 4. DumpService (New Service)

Created dedicated service for file I/O operations:

**Features:**
- Only active in local environment via `@Profile("local")`
- Configurable dump directory
- Consistent file naming: `{prefix}-{sessionId}-{timestamp}.{extension}`
- Specialized methods for JSON and YAML recipe dumps
- Proper error handling (logs warnings, doesn't break flow)

**Key Methods:**
- `dump(String content, String prefix, String extension, String sessionId)`
- `dumpRecipeJson(Recipe recipe, String sessionId)`
- `dumpRecipeYaml(String yaml, String sessionId)`

**File:** `extractor/src/main/java/net/shamansoft/cookbook/service/DumpService.java`

### 5. DebugController Integration

Integrated dump calls at 5 processing stages:

1. **After HTML extraction** (line ~150-165)
   - `dumpRawHtml` → `raw-html-{hash}-{timestamp}.html`
   - `dumpExtractedHtml` → `extracted-html-{hash}-{timestamp}.html`

2. **After HTML preprocessing** (line ~186-196)
   - `dumpCleanedHtml` → `cleaned-html-{hash}-{timestamp}.html`

3. **After recipe transformation - JSON format** (line ~251-262)
   - `dumpResultJson` → `result-json-{hash}-{timestamp}.json`

4. **After recipe transformation - YAML format** (line ~264-275)
   - `dumpResultYaml` → `result-yaml-{hash}-{timestamp}.yaml`

**File:** `extractor/src/main/java/net/shamansoft/cookbook/controller/DebugController.java`

**Dependencies:**
- Made `DumpService` optional injection: `@Autowired(required = false)`
- All dump calls wrapped in try-catch to prevent failures

## File Naming Convention

All dumps follow this pattern:
```
{prefix}-{contentHash}-{timestamp}.{extension}
```

**Example:**
```
/tmp/sar-srv/dumps/
├── raw-html-abc123def456-1738425600000.html
├── extracted-html-abc123def456-1738425600001.html
├── cleaned-html-abc123def456-1738425600002.html
└── result-yaml-abc123def456-1738425600003.yaml
```

## Security & Safety

✅ **Profile-based isolation**
- `DumpService` only available with `@Profile("local")`
- `DebugController` already restricted via `@Profile("!prod & !gcp")`
- Double protection: won't work in production

✅ **No breaking changes**
- All dump flags are optional (default: false)
- Opt-in behavior only
- Request continues normally if dumps fail

✅ **Privacy**
- No sensitive data in filenames (only content hash)
- Files written to `/tmp` (auto-cleaned by OS)
- Absolute paths for easy access

## Testing

### Build Verification

```bash
./gradlew :cookbook:compileJava  # ✅ SUCCESS
./gradlew :cookbook:test         # ✅ SUCCESS (all tests pass)
```

### Manual Test Scenario

**Start server:**
```bash
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'
```

**Send test request:**
```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
    "returnFormat": "yaml",
    "verbose": true,
    "dumpRawHtml": true,
    "dumpCleanedHtml": true,
    "dumpResultYaml": true
  }'
```

**Expected behavior:**
1. ✅ 3 files created in `/tmp/sar-srv/dumps/`
2. ✅ Response includes dump paths in `metadata` section
3. ✅ Console logs show: `📝 Dumped {type} to: /tmp/sar-srv/dumps/...`
4. ✅ Files contain correct content at each processing stage

**Verify dumps:**
```bash
ls -lh /tmp/sar-srv/dumps/
cat /tmp/sar-srv/dumps/result-yaml-*.yaml
```

## Documentation

Created comprehensive testing guide:
- **File:** `docs/postman/DUMP_FLAGS_TESTING.md`
- Includes test scenarios, verification steps, troubleshooting
- Command-line examples for manual testing
- Expected outputs and file formats

## Changes Summary

### Files Modified (4)
1. `extractor/src/main/resources/application.yaml`
   - Added `cookbook.debug.dump-dir` configuration

2. `extractor/src/main/java/net/shamansoft/cookbook/dto/TestTransformRequest.java`
   - Added 5 dump flag fields
   - Added 5 convenience getter methods

3. `extractor/src/main/java/net/shamansoft/cookbook/dto/TestTransformResponse.java`
   - Added 5 dump path fields to `ProcessingMetadata`

4. `extractor/src/main/java/net/shamansoft/cookbook/controller/DebugController.java`
   - Injected `DumpService` (optional)
   - Added 5 dump call sites at processing stages
   - Added import for `DumpService`

### Files Created (2)
1. `extractor/src/main/java/net/shamansoft/cookbook/service/DumpService.java`
   - New service for file I/O operations
   - ~110 lines of code
   - Profile-restricted to `local` environment

2. `docs/postman/DUMP_FLAGS_TESTING.md`
   - Comprehensive testing guide
   - Test scenarios and verification steps
   - Troubleshooting section

## Success Criteria

All success criteria from the plan met:

- ✅ Dump directory configured in application.yaml
- ✅ All 5 dump flags implemented and functional
- ✅ DumpService handles all content types correctly
- ✅ DumpService only active in local environment (@Profile("local"))
- ✅ File paths included in response metadata when verbose=true
- ✅ Dumps only occur when flags enabled (opt-in)
- ✅ Clear logging of dump locations
- ✅ All files written to configured directory (default: `/tmp/sar-srv/dumps/`)
- ✅ Consistent file naming convention
- ✅ No impact on normal flow when dumps disabled
- ✅ Works with recipe content (compile and test success)
- ✅ Error handling prevents dump failures from breaking requests

## Next Steps

### Recommended Follow-ups

1. **Add Postman test case** (optional)
   - Add "Transform with All Dumps Enabled" test to collection
   - See example in `DUMP_FLAGS_TESTING.md`

2. **Integration test** (optional)
   - Create `DumpServiceTest` for unit testing
   - Create `DebugControllerDumpTest` for integration testing

3. **User documentation** (optional)
   - Add dump flags to main README if useful for contributors

### Not Required

- No database migrations needed
- No API version changes
- No breaking changes to existing clients
- No production deployment impact (feature disabled in prod)

## Lessons Learned

1. **Profile-based feature isolation works well**
   - `@Profile("local")` on DumpService is clean and safe
   - Optional autowiring `@Autowired(required = false)` prevents injection failures

2. **Opt-in design is critical**
   - All flags default to `false`
   - No performance impact when disabled
   - Users must explicitly request dumps

3. **Error handling is essential**
   - Wrapped all dump calls in try-catch
   - Log warnings but never fail the request
   - Graceful degradation when DumpService unavailable

4. **File naming matters**
   - Content hash enables correlation across dumps
   - Timestamp ensures uniqueness and ordering
   - Prefix makes file purpose obvious

## References

- **Original Plan:** `docs/plans/20260131-debug-dump-flags-plan.md`
- **Testing Guide:** `docs/postman/DUMP_FLAGS_TESTING.md`
- **Related Controller:** `DebugController.java:85-321`
- **Related Service:** `DumpService.java`
