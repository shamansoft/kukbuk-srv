# Debug Flags — As-Built Spec

Opt-in flags on the `POST /debug/v1/recipes` endpoint for inspecting intermediate processing states during recipe extraction. Local environment only.

---

## Safety Constraints

- `DumpService` is `@Profile("local")` — unavailable in GCP/prod
- `DebugController` is `@Profile("!prod & !gcp")` — double protection
- All flags default to `false` — opt-in only
- Dump failures are caught and logged; the request continues normally

---

## Request Flags

Add to the `POST /debug/v1/recipes` JSON body:

| Flag | Type | Description |
|---|---|---|
| `dumpRawHtml` | boolean | HTML as fetched from the URL (before any cleaning) |
| `dumpExtractedHtml` | boolean | HTML after jsoup extraction |
| `dumpCleanedHtml` | boolean | HTML after aggressive cleaning/compression |
| `dumpLLMResponse` | boolean | Raw JSON from Gemini before parsing |
| `dumpResultJson` | boolean | Parsed recipe as JSON |
| `dumpResultYaml` | boolean | Final recipe as YAML |

---

## Generation Parameter Overrides (tuning)

Optional fields on the same `POST /debug/v1/recipes` body let you vary Gemini generation
parameters per request, without restarting the app to change `application-local.yaml`:

| Field | Type | Range / allow-list |
|---|---|---|
| `temperature` | float | 0.0 – 2.0 |
| `topP` | double | 0.0 – 1.0 |
| `maxOutputTokens` | int | 1 – 65536 |
| `thinkingBudget` | int | -1 (unlimited) or ≥ 0 |
| `model` | string | one of: `gemini-2.5-flash-lite`, `gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.0-flash`, `gemini-1.5-pro` |

Any field you omit falls back to the value configured in `application*.yaml`. An invalid value
(out of range, or a `model` not in the allow-list) returns `400` before any Gemini call is made.

```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/24074/...",
    "returnFormat": "json",
    "temperature": 0.2,
    "model": "gemini-2.5-pro"
  }'
```

**Design notes / safety constraints:**

- `safetyThreshold` is **not** overridable — safety filtering always comes from configuration,
  regardless of request input. This is deliberate: tuning is about extraction quality, not safety
  filtering, and the allow-list on `model` plus the fixed safety threshold keep the debug endpoint
  from becoming a way to bypass content-safety controls.
- When any override is set, the request **bypasses the cache entirely** — no read, no write. A
  result produced with non-default parameters must never be served back to (or contaminate) real
  `/v1/recipes` traffic reading from the same cache.
- When any override is set, the request calls `GeminiRestTransformer` directly, **bypassing**
  `AdaptiveCleaningTransformerService`/`ValidatingTransformerService` (the retry-chain wrapper
  used by production traffic). This isolates the tuning signal to one raw Gemini call per
  request/parameter combination, and means none of those two classes were touched to add this
  feature — zero behavior change for production requests.
- Every change is an additive overload (`buildRequest(html, overrides)`,
  `request(req, class, modelOverride)`, etc.) — the existing no-overrides methods used by
  production code paths (`RecipeService`, `POST /v1/recipes`, `POST /v1/recipes/custom`) are
  unchanged and still call into the same code as before.

---

## File Naming

```
{prefix}-{contentHash}-{timestamp}.{extension}
```

Example output in `/tmp/sar-srv/dumps/`:
```
raw-html-abc123def456-1738425600000.html
extracted-html-abc123def456-1738425600001.html
cleaned-html-abc123def456-1738425600002.html
llm-response-abc123def456-1738425600003.json
result-json-abc123def456-1738425600004.json
result-yaml-abc123def456-1738425600005.yaml
```

The content hash enables correlation across all dumps from the same request.

---

## Configuration

```yaml
cookbook:
  debug:
    dump-dir: ${COOKBOOK_DEBUG_DUMP_DIR:/tmp/sar-srv/dumps}
```

---

## Response Metadata

When `verbose: true`, the response `metadata` object includes the dump file paths:

```json
{
  "metadata": {
    "dumpedRawHtmlPath": "/tmp/sar-srv/dumps/raw-html-abc123-...",
    "dumpedExtractedHtmlPath": "...",
    "dumpedCleanedHtmlPath": "...",
    "dumpedLLMResponsePath": "...",
    "dumpedResultJsonPath": "...",
    "dumpedResultYamlPath": "..."
  }
}
```

---

## Internal Architecture

- `DumpService` (`service/DumpService.java`) — file I/O, `@Profile("local")`
- Injected into `DebugController` as `@Autowired(required = false)` — null in non-local profiles
- `GeminiResponse<T>` carries `rawResponse: String` field — raw JSON from Gemini passed through `Transformer.Response.rawLlmResponse` to the controller

---

## Example Usage

```bash
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'

curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.allrecipes.com/recipe/24074/...",
    "returnFormat": "yaml",
    "verbose": true,
    "dumpCleanedHtml": true,
    "dumpLLMResponse": true,
    "dumpResultYaml": true
  }'

# Inspect output
ls -lh /tmp/sar-srv/dumps/
cat /tmp/sar-srv/dumps/llm-response-*.json | jq .

# Compare LLM output vs parsed result
diff <(cat /tmp/sar-srv/dumps/llm-response-*.json | jq -S .) \
     <(cat /tmp/sar-srv/dumps/result-json-*.json | jq -S .)
```

**Related:** [Runbook: Prompt Evaluation Loop](../runbooks/prompt-evaluation.md) uses these dump flags
to score extraction quality. The Postman collection at `extractor/cookbook-api.postman_collection.json`
includes the debug endpoint.
