# Prompt: Story 9, increment A — find the JSON-LD `Recipe` on every page that has one

_Handoff prompt for a new Claude Code session, written 2026-10-17. Paste everything below the line._

---

Work in `/Users/alexey/dev/sar/sar-srv`. Read `CLAUDE.md` there and the umbrella `/Users/alexey/dev/sar/CLAUDE.md` first.

## Task

Implement **increment A** (tasks 1.1–1.3) of story 9:
`/Users/alexey/dev/sar/feature-requests/jsonld-anchored-extraction.md`. Read its "What we learned" section and task 1 first.
The goal: `StructuredDataStrategy` recognises the schema.org `Recipe` on all 47 of the 47 sample pages that publish one. Today it recognises 40.

## What is known (verified 2026-10-17 on `samples/html/`)

`extractor/src/main/java/net/shamansoft/cookbook/html/strategy/StructuredDataStrategy.java` misses 7 pages:

| Page | Cause |
|---|---|
| delish.com, essen-und-trinken.de, matprat.no | JSON-LD root is an array; `Recipe` at `[0]` |
| koket.se | array root; `Recipe` at `[1]` |
| theguardian.com | array root; `Recipe` at `[2]` |
| cucchiaio.it, inspiredtaste.net | object root, but strings contain raw control characters (CR); Jackson's strict parser throws, the block is skipped |

`findRecipeNodes` handles an object or an object with `@graph` only. No sample nests the `Recipe` under `mainEntity`, but real pages do, so task 1.2 is covered by unit tests only.

## Steps

1. Branch from up-to-date `main` (e.g. `feat/jsonld-detection`).
2. **1.1 / 1.2:** make `findRecipeNodes` walk the JSON-LD: an array root (every element), `@graph` at any of those levels, and one level of `mainEntity` (object or array). Keep the existing order: the first `Recipe` that passes `minCompleteness` wins. Keep the completeness scoring as it is.
3. **1.3:** parse with unescaped control characters allowed. The `ObjectMapper` is the shared Spring bean, so do not reconfigure it globally. Use a reader with `JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS` (Jackson 3: `tools.jackson.core.json.JsonReadFeature`; check the exact `ObjectReader` API in Jackson 3.0.3). The node the strategy returns is re-serialised by `writeValueAsString`, so the output stays valid JSON.
4. **Unit tests** in `StructuredDataStrategyTest`, with small hand-written fixtures. Do not copy the sample pages: they are third-party content. One test per shape: array root with the `Recipe` first, array root with the `Recipe` after other nodes, `@graph` inside an array element, `mainEntity` object, `mainEntity` array, raw CR/LF inside a string value, and an array with no `Recipe` (returns empty). Keep the existing tests passing.
5. Run `./gradlew :cookbook:test :cookbook:intTest`. Both must pass. Report the counts.

## Measure (needs `COOKBOOK_GEMINI_API_KEY`; about 50 paid `gemini-3.1-flash-lite` calls)

Tooling: `/Users/alexey/dev/sar/tools/extractor-tuner/` (read its README).

1. Start the server on the branch: `./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'`.
2. Run `./tuner.py run matrices/samples-all.json --out results/jsonld-detection` from the tuner folder.
3. Score it: `./tuner.py score results/jsonld-detection --details > results/jsonld-detection/SCORE.md`.
4. Compare with the baseline `results/jsonld-baseline-main/SCORE.md` (sar-srv `main` @ `519352d`, 2026-10-16):

   | Measure | Baseline |
   |---|---|
   | Pages cleaned with `STRUCTURED_DATA` (`metadata.htmlCleanupStrategy` in each `_resp.json`) | 40 |
   | Ingredients fully right | 548/615 |
   | Group right, of found | 568/604 |
   | Pages with sections grouped fully right | 8/15 |
   | Servings · times · oven temps · nutrition | 47/50 · 121/125 · 17/19 · 210/216 |
   | Pages with no recipe | 1 (`gotovim-doma.ru`) |

5. Stop the server when done.

**Known risk, check it explicitly.** essen-und-trinken.de, koket.se and matprat.no get their sections fully right today from the HTML. Once their JSON-LD is recognised, the model sees no section headers, so they will probably lose their groups. Section headers come back only with increment B (the recipe-card cutter). Report per page what changed for the 7 newly recognised pages: strategy, ingredients right, sections. If sections regress, **do not open the PR on your own**. Stop and present the numbers, with these options:
- (a) ship A now and accept the loss until B;
- (b) hold A and ship it together with B;
- (c) ship A with the 3 pages' loss offset by gains elsewhere, if the totals show that.

## Then

- If the numbers are acceptable, or the user picks (a) or (c): commit, push and open a PR. The commit message and PR body end with the attribution lines from the session's system reminder. The PR body carries the before/after table and the per-page list for the 7 pages.
- Update the docs (the top-level `/Users/alexey/dev/sar` is not a git repo; edit in place):
  - Story: check off tasks 1.1–1.3 and record the result.
  - `BACKLOG.md` item 9.2: mark it done when merged.
  - Tuner README "Contents" table: add a row for `results/jsonld-detection/`.
- Do not touch the prompt (`prompt.md`), the LLM schema or the cleaning cascade order. Those belong to later increments.

## Conventions

- Plans and progress use a checkbox table of contents; show progress as you go.
- Match the surrounding code style. `StructuredDataStrategy` is small, so keep it that way.
- Archive, don't delete, any doc you supersede.
