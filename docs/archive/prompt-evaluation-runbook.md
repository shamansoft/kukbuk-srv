# Runbook: Prompt Evaluation Loop

Iterative quality improvement for the Gemini extraction prompt (`extractor/src/main/resources/prompt.md`). Each iteration scores the output against 10 quality dimensions and improves the prompt based on the lowest-scoring areas.

**Activity log:** `docs/prompt-improvement-log.md`

---

## Prerequisites

- Spring Boot running locally with `local` profile
- `COOKBOOK_GEMINI_API_KEY` set
- `/tmp/sar-srv/dumps/` writable

---

## Per-Iteration Procedure

### 1. Backup current prompt

```bash
cp extractor/src/main/resources/prompt.md \
   extractor/src/main/resources/prompt_v$(date +%Y%m%d)-iter{N}.md
```

### 2. Start the server

```bash
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'
```

Wait for `Started CookbookApplication` in logs.

### 3. Run extraction

```bash
SESSION_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
echo "Session: $SESSION_ID"

curl --location 'http://localhost:8080/debug/v1/recipes' \
  --header 'Content-Type: application/json' \
  --header "X-Session-Id: $SESSION_ID" \
  --data '{
    "url": "https://www.gordonramsay.com/gr/recipes/chickenthighswithbacongravy/",
    "returnFormat": "yaml",
    "cleanHtml": "auto",
    "skipCache": true,
    "dumpExtractedHtml": true,
    "dumpCleanedHtml": true,
    "dumpResultJson": true,
    "dumpResultYaml": true,
    "dumpLLMResponse": true,
    "verbose": false
  }'
```

### 4. Evaluate (10 dimensions, 0–10 each)

Read the dump files from `/tmp/sar-srv/dumps/` and score:

| # | Dimension | What to check |
|---|---|---|
| 1 | HTML Data Preservation | Extracted vs cleaned — no valuable ingredients/steps removed |
| 2 | YAML Structure | Well-formed, parseable, proper indentation |
| 3 | Schema Compliance | All required fields present, correct types |
| 4 | Data Loss | All ingredients and steps from source HTML captured |
| 5 | Hallucinations | No invented data — verify against source HTML |
| 6 | Ingredients Completeness | Count extracted vs source |
| 7 | Ingredients Deduplication | No duplicate entries |
| 8 | Ingredients Categorization | Component grouping matches source sections (e.g., Chicken vs Gravy) |
| 9 | Instruction Correctness | Logical order, complete steps, correct timing/temperatures |
| 10 | Metadata Accuracy | Title, servings, description match source |

**Overall score** = average of all 10 dimensions.

### 5. Log the iteration

Append to `docs/prompt-improvement-log.md`:

```markdown
### Iteration N — YYYY-MM-DD

- **Session ID**: {uuid}
- **Prompt backup**: prompt_v{date}-iter{N}.md
- **Overall score**: X.X/10

| Dimension | Score | Notes |
|---|---|---|
| HTML Preservation | X | ... |
| YAML Structure | X | ... |
...

**Strengths:** ...
**Weaknesses:** ...
**Changes for next iteration:** ...
```

### 6. Stop the server, run tests

```bash
# Ctrl+C to stop bootRun
./gradlew :cookbook:test
```

### 7. Improve prompt (if score < 9.0)

Edit `extractor/src/main/resources/prompt.md` — focus on the lowest-scoring dimensions. Commit, then repeat from step 1.

---

## Exit Condition

Stop when **overall score ≥ 9.0/10** or after 10 iterations, whichever comes first.

---

## After the Loop

- Compare baseline (iter 1) vs final prompt side-by-side
- Test the improved prompt against several different recipes (not just Gordon Ramsay) to verify generalization
- Run `./gradlew :cookbook:intTest` before deploying to production

---

## Test Recipe

**Gordon Ramsay Chicken Thighs with Bacon Gravy** — chosen because it has two distinct ingredient components (chicken + gravy), tests component grouping, ingredient parsing, and instruction clarity.

```
https://www.gordonramsay.com/gr/recipes/chickenthighswithbacongravy/
```
