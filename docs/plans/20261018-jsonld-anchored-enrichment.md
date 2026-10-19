# Plan: JSON-LD anchored enrichment (story 9, increment D) — BACKLOG 9.5

_Written 2026-10-18. Story: [feature-requests/jsonld-anchored-extraction.md](../../../feature-requests/jsonld-anchored-extraction.md)
(tasks 4–8, "Open decisions"). Starts from sar-srv `main` @ `a74dcc6` (A–C merged in #98)._

> **Status (2026-10-18):** the owner approved both phases and the four default decisions. Phase 1 is built and
> measured (see "Phase 1 result"); Phase 2 follows on top of it.

## 📋 Table of Contents

- [x] 0. Measure first (9.5.1, read-only)
  - [x] 0.1 Production logs since the #98 deploy
  - [x] 0.2 Offline: do plugin cards line up with the JSON-LD lines?
  - [x] 0.3 Offline: sections assigned in code, on the `jsonld-abc` answers
  - [x] 0.4 Offline: does today's model drop JSON-LD lines?

**Phase 1: sections in code (9.5.2)**
- [x] 1. Read the card's groups in code
  - [x] 1.1 `RecipeCardCutter.rows` reads (group name, text) rows from the compact block; WPRM `<div>` group names
        become `<h4>` before attributes are dropped
  - [x] 1.2 Unit tests with hand-written fixtures
- [x] 2. Assign components after the model answers
  - [x] 2.1 `CardSectionAssigner`: match each ingredient to a card row, set `component` from the row's group
  - [x] 2.2 Gate: card rows = JSON-LD lines and at least one named group; otherwise keep the model's components
  - [x] 2.3 Wire into the JSON-LD path beside `JsonLdFieldOverwriter`; flag, metric, log
- [x] 3. Evaluate on the 50 samples (`results/jsonld-d-sections`)
  - [x] 3.1 Fix found on the way: `JsonLdFieldOverwriter`'s recipe check skipped 4 pages (longest word = unit)
- [x] 4. Fix `expected/indianhealthyrecipes.com.json` and `expected/inspiredtaste.net.json` (sections the page has)
- [ ] 5. Docs, commit, PR

**Phase 2: full increment D (story tasks 4–8), on a trigger**
- [ ] 6. Enrichment prompt and response schema (story 4.1)
- [ ] 7. `RequestBuilder.buildRequestFromJsonLd` + feedback variant (story 4.2, additive)
- [ ] 8. `JsonLdRecipeAssembler`: reader fields + enrichment, coverage check (story 5.1, 5.2)
- [ ] 9. Retry with feedback, then fall back to today's path (story 5.3)
- [ ] 10. Route before the cleaning cascade; flag `recipe.jsonld-anchored.enabled`; metrics (story 6)
- [ ] 11. Retire the interim wiring: `JsonLdFieldOverwriter` (2.3), card in the prompt (3.3), Phase 1's assigner
- [ ] 12. Evaluate (story 7) and docs (story 8)

---

## 0. Measurements (2026-10-18)

### 0.1 Production (gcp-logs, `kukbuk-tf` / `cookbook`)

| What | Result |
|---|---|
| #98 revision | `cookbook-v-0-15-16`, created 2026-10-18 06:21:39 UTC |
| Window checked | 06:21 – 06:44 UTC (23 min, just after the deploy) |
| Recipe saves in the window | **0** (only `GET /v1/user/profile` and one health check) |
| "Initial HTML cleaning" lines in the window | 0, so the `STRUCTURED_DATA` share **cannot be measured** yet |
| Errors from the new code in the window | none; the new code has not run in production yet |
| Pre-deploy context (30-day log retention) | 11 `POST /v1/recipes*` requests in 30 days; 6 cleaning lines: `CONTENT_FILTER` 1, `FALLBACK` 3, `SECTION_BASED` 2, `STRUCTURED_DATA` 0 |
| `html.preprocessing.ingredient_card{found}` | **not measurable**: Stackdriver export is commented out in `application-gcp.yaml`, and the card result is not logged |

Production volume is too low to decide anything from it. The decision rests on the 50 samples
(47 with JSON-LD, card found on 39). If production numbers are wanted later: log the card result in the
"Initial HTML cleaning" line, or re-enable the Stackdriver export.

### 0.2 Plugin cards against JSON-LD (offline, `samples/html/`)

On all **25** sample pages with a WPRM / Tasty / Mediavine card, the card's `li` rows match the JSON-LD
`recipeIngredient` lines **one to one and in the same order** (25/25: same count, line i's longest word in row i).
The group names come straight from the plugin (`wprm-recipe-group-name`; Tasty `p > strong`).
carlsbadcravings.com has 15 rows and 15 JSON-LD lines; `expected/` has 18 because compound lines are split.
So the story's "the JSON-LD has 15 of the page's 18" means split lines, not ingredients missing from the JSON-LD.

### 0.3 Sections assigned in code, on the saved `jsonld-abc` answers (no model calls)

Each ingredient in the model's answer was matched to the card row with the most shared words (ties go to the
next row in order), and its `component` set from that row's group ("For the X:" → "X"; no header → `main`).
Pages without a plugin card, or with one that has no group names, were left alone.
Results in `tools/extractor-tuner/results/jsonld-d-offline-sections/SCORE.md`.

| Measure | `jsonld-abc` | offline code sections |
|---|---|---|
| Ingredients fully right | 574/615 | **582/615** |
| Group right, of found | 587/613 | **594/613** |
| **Pages with sections fully right** | **13/15** | **15/15** |
| Everything else | — | unchanged (sections are the only field touched) |

Per page: spendwithpennies.com 5/12 → **12/12**, thefullhelping.com 3/10 → **10/10**,
indianhealthyrecipes.com 19/21 → 13/21. The last one comes from `expected/`: the page's card has two headed groups ("To saute & blend",
13 lines; "To make Paneer Butter Masala", 8), the expected file puts all 21 under `main`, and the model's
answer was wrong as well (one line in a group, 20 in `main`). Same class as inspiredtaste.net (story, "Result of A and B").

### 0.4 Coverage on today's path

On the 47 JSON-LD pages of `jsonld-abc`, every JSON-LD ingredient line shows up in the model's answer (one
false alarm: a two-letter Korean word). Repeated item names (salt, oil, …) are the same ingredient listed in two
sections, which is correct. With A–C, the dropped/duplicated ingredients seen in July do not
happen on the samples. Phase 2's coverage check is a guarantee for the future, not a fix for anything seen today.

**Output size.** On the JSON-LD path, the model's JSON is 46% ingredients, 34% steps, and 14% metadata plus
nutrition (the part D reads in code). The production path does not record tokens. D with steps still rewritten
saves about 14% of output tokens, and about 45% if steps are copied from the JSON-LD (open decision 1).

## Phase 1 result (2026-10-18)

All 50 pages on the production path, `gemini-3.1-flash-lite`, `results/jsonld-d-sections/SCORE.md`. Baseline:
`jsonld-abc` re-scored with the two corrected expected files (`results/jsonld-abc/SCORE-expected-20261018.md`).

| Measure | `jsonld-abc` (fixed expected) | Phase 1 |
|---|---|---|
| Ingredients fully right | 569/615 | **591/615** |
| Quantity right, of found | 599/613 | 599/613 |
| Group right, of found | 581/613 | **604/613** |
| **Pages with sections fully right** | **13/17** | **16/17** |
| Servings · times · oven temps · nutrition · title | 47 · 125 · 17 · 215 · 50 | same |
| Recipes without the cover their JSON-LD has | 0 | 0 |
| JSON-LD ingredient lines missing from the recipe | 0 | 0 |

Per page: indianhealthyrecipes.com 12/21 → 20/21, spendwithpennies.com 5/12 → 12/12, thefullhelping.com 3/10 → 10/10;
no page worse. Outcomes: applied 11, no_groups 25, no_card 7, lines_differ 4. Still wrong: inspiredtaste.net (its
card puts lines in `<p>`, not `<li>`, so the gate fails and the model's 13/17 stays).

What the samples forced:
- **Group names in a `<div>`** (indianhealthyrecipes.com): `compact()` dropped the class, so neither the model nor the
  code saw a heading. `.wprm-recipe-group-name` now becomes `<h4>` first.
- **Notes words outvoted the item** (tudogostoso.com.br, first draft): "2 xícaras (chá) de açúcar" and "1 xícara (chá) de
  açúcar" are both in the card; the model's notes "xícara (chá)" pulled the first sugar into the second group. Item
  words now count twice, a matching amount adds one, notes words count once.
- **The overwriter's recipe check** (`JsonLdFieldOverwriter.describes`) took each JSON-LD line's longest word, which is
  often the unit ("1 ½ teaspoons chili powder" → "teaspoons"). It refused the recipe on cucchiaio.it, inspiredtaste.net,
  omnivorescookbook.com and panlasangpinoy.com, so C's fields were silently not applied there (inspiredtaste.net lost
  its cover in one run). A line now counts when any of its words appears (3-letter words whole, minus fillers).

## Recommendation

1. **Build Phase 1 now.** It fixes the two remaining section pages deterministically, it is small (one class, one
   cutter change, wiring next to `JsonLdFieldOverwriter`), it needs no new prompt, and it was measured offline before any
   code. Section headers then come from the plugin, not from the model. The story lists "parsing WP Recipe Maker markup in code"
   as out of scope; this reverses that for group names only (amounts and units stay with the model).
2. **Park Phase 2** (the owner chose to build it anyway, 2026-10-18) until a trigger: production shows dropped or duplicated ingredients (needs 0.1's logging),
   save volume makes the ~14% output saving worth it, or the prompt has to change anyway. Its value after A–C plus
   Phase 1 is a coverage guarantee in code and the smaller output. On the samples it would fix no field.

## Open decisions (story), with recommendations

Phase 1 needs none of these. They apply to Phase 2.

| # | Decision | Story default | Recommendation | Why |
|---|---|---|---|---|
| 1 | Instruction wording | keep the short imperative rewrite | **Keep the default** | Copying `HowToStep.text` saves ~34% of output but changes the stored style for JSON-LD pages only, and step counts already differ from `expected/` on 3 pages because of JSON-LD splits |
| 2 | Ingredients the card shows but the JSON-LD lacks | allow, log and count | **Allow, log and count** | 0 of 25 plugin pages have such rows (0.2), so it costs nothing; a generic card may still have them |
| 3 | Cuisine | append `recipeCuisine` to `tags` | **Default** | No schema change; the app shows tags already |
| 4 | No card found | new path with JSON-LD alone | **Default** | 8 of 47 pages; today's path has no headers for them either, and the coverage check still applies |

---

## Detailed Plan

### 1. Read the card's groups in code

**Objective:** Know, for each JSON-LD ingredient line, the plugin group it sits in.

**Steps:**
1. In `RecipeCardCutter`, add `rows(Document, List<String> lines)` that returns the plugin card's
   `(group, text)` rows in document order: WPRM `.wprm-recipe-group-name` / `li.wprm-recipe-ingredient`;
   Tasty and Mediavine `h2–h6` or a `p` holding only `strong`/`b` as headers, skipping the card's own
   "Ingredients" heading block; `li` as rows. Plugin containers only, not the generic `[class*=ingredient]` rule.
2. Normalize group names as the prompt does: drop a leading "For"/"For the", trailing colon, title-case an all-caps name;
   no header → `main`.
3. `StructuredDataStrategy` keeps sending the compact block to the model unchanged (the model still sees headers; no
   prompt change). The rows travel to the assigner via the cleaner's result, or are re-cut from the raw HTML.
   Choose during the build; whichever keeps `HtmlCleaner.Results` simplest.

**Files:** `html/strategy/RecipeCardCutter.java`, its test, maybe `html/HtmlCleaner.java` (`Results`).

**Expected outcome:** unit tests with hand-written fixtures for WPRM (unheaded rows then a header, as on
spendwithpennies), Tasty (`p > strong`), Mediavine, a card without group names, and a card whose rows do not match
the JSON-LD count.

### 2. Assign components after the model answers

**Objective:** The stored `component` of each ingredient is the plugin's group, whatever the model wrote.

**Steps:**
1. `service/CardSectionAssigner` (beside `JsonLdFieldOverwriter`, same shape): for each ingredient, the card row
   sharing the most words with `item` + `notes`; ties go to the first row at or after the previous match, so compound
   lines split into several rows stay in their line's group. No word shared → keep the model's component.
2. Gate: only on the `STRUCTURED_DATA` path, single-recipe answers, card rows = JSON-LD lines, at least one named
   group. Otherwise the answer is unchanged.
3. Call it in `AdaptiveCleaningTransformerService` next to `jsonLdFields.apply(...)` (both call sites).
4. Flag `recipe.jsonld-sections.enabled` (default true). Counter `recipe.jsonld.sections{outcome=applied|skipped_*}`,
   number of components changed. One INFO log line per save with the outcome and the card result, so production
   can be measured from logs while the Stackdriver export is off (covers 0.1's gap).

**Files:** `service/CardSectionAssigner.java` (+ test), `service/AdaptiveCleaningTransformerService.java` (+ test),
`application.yaml`.

**Expected outcome:** unit tests for the matching (compound line, reordered answer, unmatched ingredient), the gate,
and the flag. `./gradlew :cookbook:test :cookbook:intTest` green.

### 3. Evaluate

Server on port 8081, `tuner.py run matrices/samples-all.json --base-url http://localhost:8081 --out
results/jsonld-d-sections`, iterate with `--cases` on the 15 section pages first. Compare per page with
`jsonld-abc`; check covers and dropped/duplicated ingredients by hand. Gate for the PR: no page worse on
ingredients or sections, apart from pages where `expected/` is shown wrong.

### 4. Expected files

`samples/expected/indianhealthyrecipes.com.json` (two groups) and `inspiredtaste.net.json` (two groups), after
checking each against the page. Re-score `jsonld-abc` and the new run with the fixed files so the comparison
stays fair. (Top-level `samples/` is not a git repo: edit in place, note it in the samples README.)

### 5. Docs, commit, PR

Story: Phase 1 result and 9.5.1 numbers; BACKLOG 9.5.1/9.5.2; `docs/metrics.md` (new counter); `CLAUDE.md`
pipeline note; tuner README results row. Commit, push, PR only if step 3's gate holds.

### 6–12. Phase 2 (full increment D)

As in the story, tasks 4–8, with these changes from what we now know:
- The assembler takes components from Phase 1's rows where the gate holds; the model's `header` rows are needed
  only for JSON-LD lists that carry headers themselves (koket.se) and for generic cards.
- `RequestBuilder` gets new methods and a new system prompt only (additive; 12.1/12.2 edit the same class).
- Retire `JsonLdFieldOverwriter` and the prompt's card rule only once the new path is the default; keep both as the
  fallback path's behaviour until then.
- Record output tokens per request (add `usageMetadata` to the debug response) to report the saving.
