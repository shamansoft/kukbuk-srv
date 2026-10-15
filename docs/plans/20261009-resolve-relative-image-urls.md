# Plan: Resolve relative recipe image URLs to absolute

_Handoff written 2026-10-09 from an investigation in sar-kmp. Executed the same day on branch
`worktree-image-url-fix`; see [Outcome](#outcome) for what was done and the decisions taken._

## 📋 Table of Contents

- [x] 1. Confirm the behaviour in sar-srv
  - [x] 1.1 Reproduce with a unit test on `RecipeMapper` (relative `cover_image.path` in, relative URL out)
  - [x] 1.2 Check how Drive/Dropbox-hosted media paths are represented, so they are not rewritten by mistake
- [x] 2. Resolve image URLs at read time
  - [x] 2.1 Add a URL-resolution helper (path + recipe source URL → absolute URL)
  - [x] 2.2 Use it in `extractCoverImageUrl` and `extractAllImageUrls`
  - [x] 2.3 Decide and implement what to return when a path cannot be resolved
- [x] 3. Resolve image URLs at write time (optional, see Open decisions)
  - [x] 3.1 Normalise `cover_image.path` and instruction media paths in `RecipePostProcessor`
- [x] 4. Tests
  - [x] 4.1 `RecipeMapperTest` cases for every path shape listed below
  - [x] 4.2 `RecipePostProcessorTest` cases if step 3 is done
- [x] 5. Docs
  - [x] 5.1 Update the `coverImageUrl` / `allImageUrls` contract wording (OpenAPI yaml, `RecipeDto` comment)

---

## Background

### Symptom

In the sar-kmp app a recipe shows an empty grey box where its cover image should be, both in the
recipe list and on the details screen.

### What was found

The app renders `coverImageUrl` from `GET /v1/recipes` exactly as received. For the affected recipe
the backend returned a relative path:

| Field | Value |
|---|---|
| Recipe | "Puerto Rican Style Steak Sandwich" |
| `source` | `https://www.gordonramsay.com/gr/recipes/prsteaksan…` (truncated when read) |
| `coverImageUrl` | `/assets/Uploads/_resampled/CroppedFocusedImage192072050-50-Screen-Shot-2021-10-01-at-2.54.00-PM.png` |
| `allImageUrls` | the same single relative path |

The image itself is fine: the same path resolved against `https://www.gordonramsay.com` returns
`200 image/png`. A URL with no host cannot be loaded by the client.

### Why sar-srv sends it

- `RecipeMapper.extractCoverImageUrl` and `extractAllImageUrls`
  (`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeMapper.java`, around lines 91-124)
  return `cover_image.path` and instruction `ImageMedia` paths unchanged.
- The value comes straight from the LLM. `extractor/src/main/resources/prompt.md` line 261 says
  "Convert relative image paths to absolute URLs", but the template's only placeholder (line 285) is
  filled with the HTML. `sourceUrl` is a parameter of `GeminiRestTransformer.transform` (line 22) and
  a grep of the `service/gemini` package shows no other use of it, so the model is asked to resolve
  paths without being given the page URL.
- `RecipePostProcessor` and `RecipeAdjustmentService.adjustMetadata` copy `coverImage()` through
  untouched.
- The result is inconsistent rather than always wrong: of the 6 `cover_image` entries in
  `no-git/examples/*.yaml` (all gordonramsay.com / gordonramsayeats.com), 2 are relative and 4 are
  absolute.

### Not verified

- The flow was not reproduced through the running API; the evidence is the app's local database,
  the code read, and the example YAML files.
- No sar-srv tests were run.
- How Drive/Dropbox-hosted media (served via `GET /v1/media/{id}`) is represented in `path` was not
  checked. See step 1.2.

---

## Detailed Plan

### 1. Confirm the behaviour in sar-srv

**Objective:** Pin the bug down with a failing test before changing anything, and learn what path
shapes exist.

**Steps:**
1. Add a `RecipeMapperTest` case: recipe with `metadata.source = https://www.gordonramsay.com/gr/recipes/x/`
   and `cover_image.path = /assets/Uploads/a.png`; assert the DTO's `coverImageUrl` is
   `https://www.gordonramsay.com/assets/Uploads/a.png`. It should fail today.
2. Find how stored-media references appear in `path` (search `RecipeMediaProxyService`, the storage
   providers, and anything writing `ImageMedia`). If such paths are not `http(s)` URLs, the resolver
   must leave them alone.

**Files to create/modify:**
- `extractor/src/test/java/net/shamansoft/cookbook/service/RecipeMapperTest.java` - failing case

**Expected outcome:**
- A red test that describes the bug, and a known list of path shapes to preserve.

---

### 2. Resolve image URLs at read time

**Objective:** Make `coverImageUrl` and `allImageUrls` absolute for every recipe, including ones
already saved to users' Drive/Dropbox with relative paths.

**Steps:**
1. Add a small helper that resolves a path against the recipe's `metadata.source`. `java.net.URI.resolve`
   covers the standard cases. Path shapes to handle:

   | Input `path` | Expected output |
   |---|---|
   | `https://…` / `http://…` | unchanged |
   | `//cdn.example.com/a.jpg` | scheme taken from the source URL |
   | `/assets/a.png` | source origin + path |
   | `images/a.png` | resolved against the source URL's directory |
   | `data:…`, blank, or malformed | see step 3 |
   | relative path with null/blank/invalid `source` (e.g. custom recipes) | see step 3 |

2. Call it from `extractCoverImageUrl` and `extractAllImageUrls`. Both `toDto` overloads already go
   through these two methods.
3. Unresolvable paths: recommended behaviour is to return `null` for the cover and drop the entry
   from `allImageUrls`, so clients never receive a URL they cannot load. This needs confirming with
   Alexey (see Open decisions).

**Files to create/modify:**
- `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeMapper.java` - resolution at mapping time
- (optional) a small `ImageUrlResolver` class next to it if the logic is reused by step 3

**Dependencies:**
- Outcome of step 1.2.

**Expected outcome:**
- The test from step 1 passes; `GET /v1/recipes` and `GET /v1/recipes/{id}` return absolute image URLs.

---

### 3. Resolve image URLs at write time (optional)

**Objective:** Store correct absolute URLs in the recipe YAML so other readers of the file (and any
future consumer that bypasses `RecipeMapper`) get them too.

**Steps:**
1. In `RecipePostProcessor`, which already receives `sourceUrl`, normalise `cover_image.path` and
   instruction media paths with the same helper before the recipe is stored.

**Files to create/modify:**
- `extractor/src/main/java/net/shamansoft/cookbook/service/RecipePostProcessor.java`

**Expected outcome:**
- Newly extracted recipes are stored with absolute image URLs. Step 2 is still required for
  recipes stored before this change.

---

### 4. Tests

**Steps:**
1. `RecipeMapperTest`: one case per row of the table in step 2, for both the cover image and
   instruction media.
2. `RecipePostProcessorTest`: the same shapes, if step 3 is implemented.

**Files to create/modify:**
- `extractor/src/test/java/net/shamansoft/cookbook/service/RecipeMapperTest.java`
- `extractor/src/test/java/net/shamansoft/cookbook/service/RecipePostProcessorTest.java`

---

### 5. Docs

**Steps:**
1. `docs/openapi/recipe-api.yaml` (`coverImageUrl` near line 484, `allImageUrls` near line 490) and
   the comment in `dto/RecipeDto.java` line 39: state that the URLs are always absolute, and what is
   returned when one cannot be resolved.

---

## Outcome

**Step 1.2 finding:** sar-srv never writes a stored-media reference into `path`. `CoverImage` and
`ImageMedia` are only deserialized from LLM output, and `GET /v1/media/{id}` takes a bare Drive file
ID that no code puts into a recipe. There is no storage path shape to preserve.

**What changed:**
- `service/ImageUrlResolver` (new): `resolve(path, sourceUrl)` returns an absolute http(s) URL or empty.
  Beyond the table in step 2 it also handles a source with no path (`https://site.com`), `..`
  segments that climb above the root, a source whose query contains characters `java.net.URI`
  rejects (the query is ignored), and paths with spaces (percent-encoded instead of rejected).
- `RecipeMapper`: both image extractors go through the resolver.
- `RecipePostProcessor`: `cover_image.path` and instruction `ImageMedia` paths are resolved before
  the recipe is cached and stored. `VideoMedia` (`path`, `thumbnail`) is left alone; the API does not
  expose it.
- Tests: `ImageUrlResolverTest` (new), `RecipeMapperTest`, `RecipePostProcessorTest`.
- Docs: `docs/openapi/recipe-api.yaml`, `RecipeDto` comments.

**Decisions taken (the recommended option in each case; not separately confirmed):**
1. Unresolvable paths on read: `coverImageUrl` is `null` and the entry is left out of `allImageUrls`.
2. Write-time normalisation: done. An unresolvable path is stored as extracted rather than removed,
   so the write path never discards model output; the read path filters it.
3. Prompt: left alone.

**Not done:**
- Not exercised through the running API; verified by unit tests only.
- Recipes created via `POST /v1/recipes/custom` do not pass through `RecipePostProcessor`, so they
  are covered by the read path only.
- `sar-kmp/docs/reference/backend-api-get-recipes.md` (client view of this contract) is in another
  repo and was not updated.

## Open decisions (for Alexey)

1. **Unresolvable paths:** return `null` / drop the entry (recommended), or pass the raw value through.
2. **Write-time normalisation (step 3):** do it now, or keep the change to the read path only.
3. **Prompt:** whether to also pass the page URL into `prompt.md` so the model can resolve paths
   itself. Not needed once resolution is deterministic, and prompt changes need the evaluation
   runbook (`docs/runbooks/prompt-evaluation.md`), so the suggestion is to leave the prompt alone.

## Follow-up outside this repo

- **sar-kmp:** the app keeps the old relative URL in its local database until that recipe is
  re-synced. Separately, the app's image failures are silent (no error fallback, and its configured
  Coil `ImageLoader` is registered in Koin but never installed). These are tracked on the sar-kmp
  side and are not part of this task.
