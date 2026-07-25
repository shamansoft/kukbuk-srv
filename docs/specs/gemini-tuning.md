# Gemini Generation Parameter Tuning — As-Built Spec

How to vary Gemini generation parameters per request against `POST /debug/v1/recipes`
for fast, no-restart tuning — and which parameters are deliberately *not* exposed, and why.
Local environment only (`@Profile("local")`).

See [debug-flags.md](debug-flags.md) for the dump flags this endpoint also supports.

---

## How it works

Add any of the fields below to the `POST /debug/v1/recipes` JSON body. Any field you omit
falls back to the value configured in `application*.yaml`. When **any** override field is set:

- The request bypasses the recipe cache entirely — no read, no write. A result produced with
  non-default parameters must never be served back as (or overwrite) the canonical cached
  recipe used by real `/v1/recipes` traffic.
- The request calls `GeminiRestTransformer` directly, **bypassing**
  `AdaptiveCleaningTransformerService`/`ValidatingTransformerService` (the retry-chain wrapper
  production traffic goes through). This isolates the tuning signal to one raw Gemini call per
  request, uncomplicated by retry logic.
- Invalid values (out of range, or a `model` not on the allow-list) return `400` before any
  Gemini API call is made.

Implementation: `GenerationOverrides` (`extractor/src/main/java/.../service/gemini/GenerationOverrides.java`),
wired through `RequestBuilder.buildRequest(html, overrides)` → `GeminiRequest.GenerationConfig`.

---

## Parameters you CAN tune

| Field | Type | Range / allow-list | What it does |
|---|---|---|---|
| `temperature` | float | 0.0 – 2.0 | Sampling randomness. `0.0` = near-deterministic. |
| `topP` | double | 0.0 – 1.0 | Nucleus sampling — restricts sampling to the smallest token set whose cumulative probability ≥ topP. `1.0` = no restriction. |
| `topK` | int | 1 – 100 | Restricts sampling to the K most likely next tokens. Not set by default config — omitted entirely unless overridden, so Gemini uses the model's own default (e.g. 64 for the 2.5/3.x family). |
| `maxOutputTokens` | int | 1 – 65536 | Hard cap on response length. Must leave enough room for `thinkingBudget` — see note below. |
| `thinkingBudget` | int | -1 (unlimited/dynamic), 0 (disabled), or a positive token count | Reasoning-token budget before the model writes its answer. **Model-dependent — see findings below.** |
| `seed` | int | any int32 | Pins sampling for reproducibility — same seed + same params ≈ same output across calls. Not a quality knob; use it to isolate "did my prompt/param change do this, or just sampling noise." |
| `presencePenalty` | float | -2.0 – 2.0 | Penalizes tokens that have already appeared at all. Positive discourages repetition. |
| `frequencyPenalty` | float | -2.0 – 2.0 | Penalizes tokens proportionally to how often they've already appeared. |
| `stopSequences` | string[] | ≤ 5 entries, ≤ 100 chars each | Strings that halt generation if produced. Low value for schema-constrained JSON output — included for completeness, not expected to matter here. |
| `model` | string | `gemini-2.5-flash-lite`, `gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.0-flash`, `gemini-1.5-pro`, `gemini-3.1-flash-lite`, `gemini-3.5-flash-lite`, `gemini-3.6-flash` | Which model to call. Allow-listed — see "Why some things aren't tunable" below. |

### ⚠️ Finding: `thinkingBudget` is not uniformly supported

Confirmed empirically against the live API (2026-07):

- `gemini-2.5-flash-lite` and `gemini-3.1-flash-lite` accept `thinkingBudget: 0` (thinking
  disabled) — this is our app's configured default.
- `gemini-3.5-flash-lite` and `gemini-3.6-flash` **reject `thinkingBudget: 0` outright**
  (`400 INVALID_ARGUMENT`) — these model generations require thinking enabled (`-1` or a
  positive budget).
- Setting `thinkingBudget: -1` (unlimited/dynamic) on `gemini-3.1-flash-lite` caused it to spend
  most of the shared token budget on internal reasoning, truncating the actual JSON answer
  (`finishReason: MAX_TOKENS`, parse failure). Fix: use a bounded budget (we used `1024`) and a
  generous `maxOutputTokens` (we used `16384`) instead of `-1`.

**Practical rule:** when testing a model you haven't tuned before, don't assume the configured
default `thinkingBudget: 0` works — try `1024` + `maxOutputTokens: 16384` as a safe starting
point, and adjust from there.

### ⚠️ Finding: raising `temperature` did not fix quantity fabrication

We hypothesized that raising `temperature` from `0.0` to `0.3` would reduce the failure mode
where models fabricate a quantity (e.g. `"amount": "0"`) for ingredients described vaguely
("a little bit", "to taste"). It did not — one model was unaffected, another regressed (started
fabricating quantities it had previously, correctly, left as `null` + descriptive `notes`).
**Conclusion:** whether a model writes `null` vs. guesses a number for an unstated quantity is
an instruction-following/semantic decision, not a sampling-randomness one. If you want to fix
this class of error, look at the prompt (`prompt.md` / `description-prompt.md`), not generation
parameters.

---

## Parameters you CANNOT tune (and why)

| Field | Why it's excluded |
|---|---|
| `safetyThreshold` | Safety-critical. Always comes from configuration regardless of request input — this is deliberate so the debug/tuning surface can never be used to loosen content-safety filtering. |
| `responseMimeType`, `responseSchema` | These define our structured-output contract. `GeminiExtractionResult` deserialization depends on the exact configured JSON schema — overriding it would break parsing, not just change quality. |
| The prompt text itself (`systemInstruction` / `prompt.md` / `description-prompt.md`) | A separate tuning axis (prompt engineering, not generation parameters). Edited directly in the prompt files, evaluated via `docs/runbooks/prompt-evaluation.md`. |
| `base-url`, `api-key`, `timeout-seconds` | Transport/infra config, not quality tuning. Allowing a caller to redirect `base-url` per-request would be an SSRF-style risk; allowing a caller-supplied `api-key` would be a credential-handling anti-pattern. |
| `candidateCount` | Not wired up. Our response handling (`GeminiClient.request`) only ever reads `candidates[0]` — exposing `candidateCount` without also building multi-candidate selection logic would just pay for N candidates and throw away N-1 of them. A real feature addition, not a knob flip; flagged as a possible follow-up, not implemented. |
| `responseLogprobs`, `logprobs` | Diagnostic/debugging fields (per-token confidence), not a generation-quality lever. Would need new response plumbing to surface (`GeminiResponse` doesn't carry this today) — a possible future addition for confidence analysis, not implemented. |

---

## Example

```bash
curl -X POST http://localhost:8080/debug/v1/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "text": "... free-text or HTML recipe content ...",
    "returnFormat": "json",
    "verbose": true,
    "cleanHtml": "disabled",
    "model": "gemini-3.5-flash-lite",
    "thinkingBudget": 1024,
    "maxOutputTokens": 16384,
    "temperature": 0.0,
    "topK": 40,
    "seed": 42
  }'
```

Saved tuning session outputs (request/response JSON per model/round) live in
`~/dev/sar/sar-srv/.aki/tuning/` — not checked into the repo.
