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

See `docs/postman/DUMP_FLAGS_TESTING.md` for full Postman test scenarios.
