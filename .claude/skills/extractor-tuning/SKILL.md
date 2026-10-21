---
name: extractor-tuning
description: Measure a change to recipe extraction (prompts, llm-recipe-schema.json, HTML cleaning, transformers, post-processing, Gemini model or parameters) with the extractor tuner, before and after, on the 50 sample pages. Use when asked to "score with the tuner", "run the samples", "check for regressions", compare prompt versions or models, or resume an interrupted tuner run.
---

# Extractor tuning: before/after on the sample pages

The tuner is `/Users/alexey/dev/sar/tools/extractor-tuner/` (umbrella folder beside this repo, not in git; full
manual in its `README.md`). From a worktree, `../tools` does not reach it: use the absolute path.
It sends saved pages to this server's local debug endpoint and scores the answers against hand-written
expected recipes. Every page is one real Gemini call, so reuse what is already saved.

## 1. Pick the matrix

| What changed | Matrix | Score with |
|---|---|---|
| `prompt.md`, schema, cleaning, JSON-LD, post-processing (HTML path) | `matrices/samples-all.json` (50 pages) | `score <folder>` |
| `prompt-markdown.md` (markdown.new path) | `matrices/markdown-new.json` (50 pages) | `score <folder>` |
| `description-prompt.md` (`POST /v1/recipes/custom`) | `matrices/descriptions.json` (28 texts) | `score <folder> --expected expected-desc --optional` |

A shared change (schema, post-processor) affects all three; run at least the HTML matrix.

## 2. Reuse the baseline if it still fits

Read "Current baselines" in the tuner README, then:

```bash
cd /Users/alexey/dev/sar/tools/extractor-tuner
./tuner.py baseline results/<baseline folder> --srv <sar-srv checkout the "after" server will run from>
```

- "Same extraction code" → reuse it; send only the "after" run.
- Changed files listed → reuse only if none of them changes what the extractor returns (controllers, DTOs,
  `RecipeMapper`, storage don't). If unsure, or no `run-info.json`, run a new baseline: start the server from a
  clean checkout of the base commit (a separate worktree) and run the matrix into `results/<name>-before`.

## 3. Start the server from the checkout under test

```bash
cd <sar-srv checkout>   # the worktree when working in one
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local' > <scratchpad>/server.log 2>&1   # background
```

- Needs `COOKBOOK_GEMINI_API_KEY`. Wait for `Started` in the log; port 8080 must be free.
- The server reads prompts and the schema at startup: restart after changing them.
- Don't run Gradle builds in the same checkout while the server serves a run.

## 4. Run: smoke first, then everything

```bash
./tuner.py run matrices/samples-all.json --cases eda.ru kingarthurbaking.com --out results/<name>-after --srv <checkout>
./tuner.py run matrices/samples-all.json --out results/<name>-after --srv <checkout>
./tuner.py retry matrices/samples-all.json --out results/<name>-after --srv <checkout>   # if any failed
```

- Check the smoke answers by hand (`*_resp.json`) for the fields you changed; a schema the API rejects fails here.
- Always pass `--srv`: `run-info.json` records which code produced the folder, and `run` refuses to mix code states.
- The local key is free tier (15 requests/min). `503 high demand` / `429` / read timeouts come in waves: `retry`
  re-sends only failed or missing calls, 10 s apart, 5 min between passes, up to 3 h. Run it in the background;
  it is safe to stop and restart. Shell loops: write them in bash (zsh does not word-split `$list`).
- Done when `responses_failed` in `run-info.json` is 0 in both folders.

## 5. Score and compare

```bash
./tuner.py score results/<before> --summary
./tuner.py score results/<after> --summary
./tuner.py score results/<after> --details > results/<after>/SCORE.md   # and the same for before
```

- Headline: "Ingredients fully right". On 615 expected ingredients, 1 point is ~6 ingredients; a drop of 1-2 points
  can be noise, so read the `--details` differences of the pages that changed before calling it a regression.
  Re-run the changed pages into `<folder>-run2` to separate noise from effect.
- Also compare servings, times, temperatures, nutrition, language, category, and pages with the expected number of
  recipes and steps.
- The expected files were written by hand: check a miss against the page before acting on it.

## 6. Record it

- Add a row for the new folders to the README "Contents" table, with what changed and the result.
- After the change merges to `main`, its "after" folder is the new baseline: update "Current baselines".
- Put the before/after numbers in the PR description.
