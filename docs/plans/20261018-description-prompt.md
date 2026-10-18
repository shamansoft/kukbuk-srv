# Plan: Tune and actualize the plain-text prompt (BACKLOG 12.2)

`POST /v1/recipes/custom` → `RecipeService.structureDescription` → `GeminiRestTransformer.transformDescription` →
`RequestBuilder.buildRequestFromDescription` (`description-prompt.md`, system part split at
`**User's recipe description:**`, user text in `<USER_DESCRIPTION>`). No validation retry on this path; the answer goes
through `RecipeAdjustmentService` like the HTML path. Measured with `tools/extractor-tuner` (matrix
`matrices/descriptions.json`, debug route `/debug/v1/recipes/custom`, server on port 8083), production model
(`gemini-3.1-flash-lite`).

Starting point: `description-prompt.md` was last revised in #94 (commit `fb902b9`) on 6 hand-written cases, which now
score 43/43 (`results/desc-reviewfix`). The set is saturated, so it cannot show whether a change helps or hurts.

## 📋 Table of Contents

- [x] 1. Gap list: `prompt.md` rule by rule against `description-prompt.md` (below)
- [x] 2. Scorer: measure what this task changes (additive, `tools/extractor-tuner`)
  - [x] 2.1 Expected files with several recipes (`{"recipes": [...]}`), matched to `recipesJson` by title
  - [x] 2.2 `optional` and substitutions on found ingredients (own rows, not part of "fully right")
  - [x] 2.3 Title not scored when the expected title is `null` (text without a title)
  - [x] 2.5 Optional/substitution rows and misses only with `score --optional` (HTML runs unchanged but one row)
  - [x] 2.4 Checks in `test_scoring.py` (16 pass); README rows
- [x] 3. Test set: 17 new hand-written cases in `cases-desc/` + expected files in `expected-desc/`
  - [x] 3.1 Settle the judgment calls (below) before writing expected files
  - [x] 3.2 Write the cases and expected files; `two-dips` expected file gets both recipes
  - [x] 3.3 5 held-out cases written after v5, run on v0 and v5 only
- [x] 4. Baseline: today's prompt on all 23 cases (`results/description-v0`): 223/234
- [x] 5. Rewrite `description-prompt.md` and iterate, one measured version at a time (v1–v5)
- [x] 6. Final full run, per-case comparison against the baseline, record versions and scores here
- [ ] 7. `./gradlew :cookbook:test :cookbook:intTest`, commit, push, PR; BACKLOG 12.2 and tuner README rows

---

## 1. Gap list

Status: **exists** (description prompt has it), **partial**, **missing**, **n/a** (web pages only).
Action: what changes in `description-prompt.md`. "Not carried over" means the rule stays in `prompt.md` only;
`prompt.md` itself is not changed.

| `prompt.md` rule | Free text? | Status in `description-prompt.md` | Action |
|---|---|---|---|
| Keep original language | yes | exists (same block) | keep; add mixed-language rule (below) |
| Component grouping: scan HTML headings, bold text | partly | partial: one line "use the user's own section headers" | rewrite for text: a header is a line (or a "Name:" lead-in) naming a part before its ingredients, in any form: "For the sauce:", "Sauce -", "SAUCE", "Тесто:", "Für den Teig:" |
| `<RECIPE_CARD_INGREDIENTS>`, JSON-LD flat list | no | — | n/a |
| Every ingredient takes the nearest header above; before the first header = `main` | yes | missing | add |
| Normalize names (drop "For the", colons) | yes | missing | add |
| Sections only from headers, never from steps or order | yes | missing | add |
| ">3 ingredients all `main` → re-scan" | no | — | not carried over: most typed recipes have no headers, this check invents groups |
| No fluff, imperative, keep cues and warnings | yes | exists (task item 6) | keep; add "a recipe told as a story" |
| `metadata.description`: one objective sentence | yes | missing | add |
| Split compound lines ("salt and pepper") | yes | partial: one line, no example | add the example, the shared note goes to both ("to taste"), and "each" lines ("½ tsp each cumin and coriander" → two items, 0.5 tsp each) |
| `amount` is a number, never `0`, never text `"null"` | yes | exists | keep |
| A number in the text MUST be the amount | yes | exists | keep |
| Fractions (½, 1 ⅓) | yes | partial (one example) | add "1/2", "1 1/2", "½" |
| Ranges: midpoint, range in notes; "2-3", "2 to 3", "2 or 3", "about 3-4" | yes | exists (2 examples) | add the "or" form |
| Unquantified: to taste, as needed, pinch, dash, splash, handful, drizzle, bare name | yes | exists | keep; add free-text words (below); "never use pinch/dash/handful as a unit" |
| "estimated" amount for a main ingredient with no amount | free text only | exists | keep |
| Keep every unit, also for salt and spices | yes | exists | keep |
| Two unit systems: first one, other in notes | yes | exists | keep |
| "1 head garlic" stays a head, not 10 cloves | yes | missing | add |
| Bare counts → `unit: null`; clove/slice/sprig/piece only when the text says that word | yes | partial (first half) | add second half |
| Unit whitelist | yes | exists | keep; add other-language unit words (below) |
| No parentheses/commas/descriptions in `unit` | yes | exists (one line) | keep, plus a short bad-example list |
| Packages: count + type, size in notes | yes | exists (one example) | add "1 can (400 g)", "2 x 400 g tins" ("tin" → `can`), "1 pack" |
| Preparation details in notes | yes | exists | keep |
| Keep identity descriptors ("thick cut bacon"), move prep words to notes ("diced onion") | yes | missing | add |
| `optional: true` for "optional", "if desired", garnish | yes | missing | add, with free-text phrasings ("if you like", "if you have it") |
| Substitutions only when the text states an alternative | yes | missing | add ("butter or coconut oil", "можно заменить") |
| Step time format | yes | exists | keep |
| Step time: total of the step's stated times | yes | missing | add |
| Step time: estimate times the source does not give ("bacon 5-7 min") | no | — | not carried over: for a user's own text that is invention |
| Oven temperature in `temperature`, mandatory on preheat/bake steps | yes | exists (one line) | add "mandatory" and the degree symbol |
| Media arrays, Markdown, relative image URLs | no | — | n/a |
| Multiple recipes | yes | exists (free-text version) | keep; add "a one-line variation is a note, not a recipe" |
| `recipe_confidence` scale, not-a-recipe output | no | always `true` / `1.0` | n/a |
| Required fields: versions, title, description, servings | yes | partial (title, servings) | add description |
| Times from JSON-LD (ISO 8601) | no | free-text version exists | keep the free-text rule |
| Nutrition table from JSON-LD | partly | exists ("null unless the user gives it") | add "per serving, plain numbers" |
| Storage instructions, tips, notes | yes | missing | add: storage → `storage`, tips → `notes` |
| Difficulty from context | yes | missing | add one line |
| Final checklist | yes | partial (6 of 12) | add compound split, prep in notes, temperature, optional/substitutions |

What free text needs that pages do not (none of it is in either prompt):

- **No structure:** ingredients inside sentences, no list at all. Take every ingredient from the text in the order it is
  first mentioned; a quantity in a step ("add 2 cups of stock") is that ingredient's amount.
- **A recipe told as a story:** drop the anecdote, keep the steps and their cues, write them as commands.
- **Typos:** fix obvious misspellings in item names and steps ("tomatos", "backing soda" → "baking soda"); never change
  which ingredient it is.
- **Mixed languages** (Russian text with English units or words): `language` is the language of most of the text; keep
  every word as the user wrote it, do not translate the other-language parts.
- **Number words:** "two", "half a cup", "a dozen", "три", "пол-": numbers in `amount`.
- **Vague quantities:** see judgment call b.
- **Other-language unit words:** "ст. л." / "ч. л." / "стакан" / "щепотка" / "по вкусу", "EL" / "TL" / "Prise" /
  "nach Belieben", "cda" / "cdta" / "pizca" / "al gusto".
- **Several recipes in one text:** exists; also needs the pie-and-crust case to stay one recipe.
- **Servings:** a range ("serves 4-6") → midpoint; "makes 12 cookies" → 12.

Not changed: `prompt.md`, the HTML/Markdown paths, and `llm-recipe-schema.json`. The schema is shared with the HTML
path, and some of its field descriptions say "page" (`is_recipe`, `language`, `nutrition`). The prompt overrides them
for free text, so the schema stays.

## 2. Scorer

`tuner.py score` today compares only `recipeJson` (the first recipe) and skips `optional` and substitutions. Those
are the rules this task adds, so I extend the scorer additively. Every existing expected file scores exactly as before:

- An expected file may be `{"recipes": [r1, r2]}`. Each expected recipe is scored against the returned recipe
  (`recipesJson`) with the closest title, and the counts are summed. A new row, "Pages with the expected number of
  recipes", catches both a merge (2 → 1) and a split (pie and crust 1 → 2).
- New rows over found ingredients: "Optional items flagged" (expected `optional: true` and got it), "Flagged optional
  but not" (got `true`, expected `false`), "Substitutions found" (expected alternative present by name), "Invented
  substitutions". They are not part of "fully right", so the headline stays comparable with every earlier run.
- `metadata.title: null` in an expected file means "the text has no title": the generated title is not scored.

## 3. Test set

Your own wording, as a person would type it into the app. 17 new cases, 23 in total.

| case | language | tests |
|---|---|---|
| `shakshuka` | en | optional ("feta if you like", cilantro to garnish), "salt and pepper to taste", "1 can (400 g) tomatoes" |
| `banana-bread` | en | substitutions ("butter or coconut oil", "honey instead of sugar, use 1/2 cup"), walnuts optional, 350F, "makes 1 loaf" |
| `lasagna` | en | three sections ("Meat sauce:", "Bechamel:", "To assemble:"), the same item (butter, parmesan) in two sections |
| `apple-pie` | en | one recipe with "Crust" and "Filling" sections, must NOT become two recipes; sugar in both sections |
| `grandma-pho` | en | a story with no list, vague quantities ("a couple of star anise", "a thumb of ginger", "a good glug of fish sauce") |
| `omelette` | en | very short, one line, lower case, no punctuation |
| `cookies-typos` | en | typos ("flower", "backing soda", "choclate chps"), number words ("two eggs") |
| `lentil-soup` | en | compound lines: "1 tsp each cumin and coriander", "2 carrots and 2 celery stalks", "oil and lemon to serve" |
| `pasta-bake` | en | packages: "2 x 400g tins", "1 pack (200 g) halloumi", "half a 500 g bag of penne", "1 jar pesto" |
| `curry-long` | en | very long (about 600 words): intro story, marinade + sauce sections, tips, variations, storage, "serves 4-6" |
| `brunch-two` | en | two recipes in one text (oats + smoothie), each with its own steps |
| `salad-prose` | en | a dressing named in prose ("for the dressing whisk 3 tbsp oil...") with no header line |
| `syrniki-ru` | ru | "по вкусу", "щепотка", optional raisins ("по желанию"), substitution ("или"), "1 стакан" |
| `pelmeni-ru` | ru | sections "Тесто:" / "Начинка:", number words ("два яйца"), "ст. л." / "ч. л." |
| `mixed-ru-en` | ru | Russian text with English units ("200g муки, 2 tbsp сахара, 1 cup молока") |
| `apfelkuchen-de` | de | sections "Für den Teig:" / "Für die Streusel:", "EL" / "TL", "1 Prise Salz", "nach Belieben" |
| `tortilla-es` | es | "una pizca de sal", "al gusto", number words ("seis huevos"), no list, sartén steps |

Expected files follow `samples/README.md` "Conventions used", plus these free-text choices:

### 3.1 Judgment calls (please confirm or change)

- a. **Ingredients named only in the steps** when the text has an ingredient list: not added (the samples' rule). With
  no list at all, every ingredient comes from the text.
- b. **Vague quantities:** "a couple of" → `2`, notes "a couple". "a few", "some", "several", "несколько" → `null`,
  wording in notes. "a knob", "a glug", "a thumb of ginger", "a splash" → `null`, wording in notes (like "a pinch").
  "half a lemon" → `0.5`.
- c. **`стакан`** (a Russian glass, 200–250 ml): `unit: "cup"`, notes "стакан", no conversion. The alternative,
  `unit: null` with notes "стакан", keeps the number but loses that it is a volume.
- d. **Typos:** corrected in `item`. The scorer matches names loosely, so this mainly matters for the app.
- e. **`optional`:** `true` for "optional", "if you like / if desired / if you have it", "по желанию", "nach Belieben"
  (when it is about adding the item), and garnishes. "for serving" alone stays `false` (as in today's `chili`).
- f. **Substitutions:** only alternatives the text states; the alternative's amount and unit when given; `ratio` only
  when stated.
- g. **Sections in prose:** "for the dressing, whisk 3 tbsp oil and 1 tbsp vinegar" names a part, so it is a section,
  the same as a header line. Parts are never invented from the steps.
- h. **Variations** ("for a chocolate version add 2 tbsp cocoa"): a note on the recipe, not a second recipe and not an
  ingredient.
- i. **Times:** set only when the text states a recipe-level time or there is one obvious cooking phase ("roast for 1
  hour 15"); otherwise `null` in the expected file. `null` expected values are not scored.
- j. **Title:** the user's title when there is one; `null` (not scored) when the text has none.

## 4–6. Measure

- Baseline: today's prompt, all 23 cases, `results/description-v0`. One call per case per version; the expected files
  are written before the baseline runs, so the prompt is not tuned to cases written after it.
- Each version: a subset of the cases it should fix first, then all 23 before it counts. Per-case changes, not only
  totals.
- To decide: a version must beat the baseline on "fully right" without a per-case regression I cannot explain. A
  single unexplained per-case drop is re-run once before it is counted as a drop (temperature is 0 but answers still
  vary).

### Results

All runs on the production model (`gemini-3.1-flash-lite`), one call per case, `tuner.py score --optional`. Folders are
`tools/extractor-tuner/results/description-<version>`. "optional" is expected `true` and got it / flagged but expected
`false`; "substitutions" is found / invented. Every run returned the expected number of recipes.

| version | change | fully right (23 cases) | optional | substitutions | other |
|---|---|---|---|---|---|
| v0 | today's prompt | 223/234 (95%) | 3/5 / 0 | 3/6 / 0 | |
| v1 | gap list carried over: groups, compound and "each" lines, number words, vague words, packages, sizes, optional, substitutions, list-vs-steps, storage/notes | 78/81 on the 7 cases v0 missed (v0: 71/81) | 3/3 / 5 | 3/3 / 0 | item kept in the sentence's case ("муки"); "bag" → `package` |
| v2 | items in dictionary form; package words kept; optional ONLY when the text says so | 230/234 (98%) | 5/5 / 5 | 6/6 / 0 | garlic `clove` moved into the item (2 cases); two oven temperatures merged into one step |
| v3 | nominative but plural for counts, unit word never in item; one step per temperature; "to serve" example | 232/234 (99%) | 5/5 / 0 | 6/6 / 0 | garlic still; `cook_time` included resting (1 case) |
| v4 | explicit clove example with `item`; resting is not `cook_time` | 233/234 (100%) | 5/5 / 0 | 6/6 / 0 | "1 litre" → no unit, twice in a row |
| **v5** | spelled-out metric units ("litre", "л", "gram") | **234/234 (100%)**, repeat run 234/234 | 5/5 / 0 | 6/6 / 0 | |

Expected-file fix during tuning: `pelmeni-ru` had `"лук"` where the text says "две луковицы" (the convention keeps the
user's word). Applied before scoring v4 and every version rescored; it costs v0 one ingredient (224 → 223).

**Per case, v0 → v5 (23 cases):** every v0 miss fixed, no case worse. Fixed: `apfelkuchen-de` (Puderzucker "nach
Belieben" optional), `curry-long` (3 substitutions; "a 5 cm piece of ginger" was `5 piece`), `grandma-pho` ("a thumb of
ginger" was `1 piece`; 6 ingredients put in an invented "Serving" group), `mixed-ru-en` ("+ для сковороды" split into an
extra ingredient), `omelette` ("knob of butter" was `1 piece`), `pasta-bake` ("half a 500 g bag" converted to `250 g`),
`pelmeni-ru` (item), `shakshuka` (cilantro "to garnish" optional).

**Held-out (5 cases written after v5, `description-v0-holdout`, `description-v5-holdout`):**

| | fully right | optional | substitutions |
|---|---|---|---|
| v0 | 45/49 (92%) | 2/3 / 0 | 0/3 / 1 (stock as an alternative to wine) |
| v5 | 46/49 (94%) | 3/3 / 0 | 3/3 / 0 |

v5 fixed on held-out: `plov-ru` ("1 головка чеснока" kept as a head), `risotto` ("a knob of butter" was `1`), salsa verde
anchovies (`piece` invented), plus all optional/substitution misses. v5 worse on held-out, both explained:
`risotto` "2 shallots and 1 garlic clove" lost `clove` again (the clove-into-item weakness, inside a compound line;
open), and `gazpacho-es` "un trozo de pan duro" read as a vague quantity (my expected `1 piece` is the debatable side).
Both versions miss chimichurri "a big bunch of parsley" (v0 `1 piece`, v5 `1`).

Caveat: the 23 cases and the prompt were written by the same author and v1–v5 were tuned on them; the held-out set is
small. The headline gain that generalises is substitutions and `optional` (0/3 → 3/3 and 2/3 → 3/3 on held-out), and
no invented amounts for vague words; ingredient quantities were already 92–95% right with v0.

## 7. Ship

`./gradlew :cookbook:test :cookbook:intTest` before each commit. `RequestBuilderTest` mocks `description-prompt.md` and
needs the `**User's recipe description:**` sentinel, which the rewrite keeps as the last line before `%s`. No
`RequestBuilder` change is planned. BACKLOG 12.2 and the tuner README: re-read right before editing, change only those
rows.
