# Review: feat-user-tags / 01-initial

scope: `/Users/alexey/dev/sar/sar-srv/.revmux/tasks/feat-user-tags/01-initial/input/scope.md`

## Major

### A failed user-tag lookup aborts the recipe save (Drive: never caught; Dropbox: only ClientException caught)

`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java:165-166`

`uploadRecipe` now calls `provider.findRecipeYaml(...)` before every upload, and nothing on that path catches lookup failures. The goal says "a failed lookup never blocks a save".

- Google Drive: `GoogleDriveService.findFileContent` calls `drive.getFile(...)` and then `drive.downloadFileAsString(...)` with no try/catch. The download is a new network call on the save path. It can throw a `RestClientResponseException` on a transient 5xx, a 403 rate limit or a 404 race, or a `ClientException` on an empty body. That exception goes out through `uploadRecipe` → `extractAndStore`, and the catch-all handler turns it into a 500.
- Dropbox: `DropboxStorageProvider.findRecipeYaml` catches only `ClientException`. `DropboxClient.notFoundOrSame` turns only a 409 `not_found` into `ClientException`, so a 5xx, a long 429, another 409 reason, or a `ResourceAccessException` (network error) also aborts the save.

`withExistingUserData` catches only `InvalidRecipeFormatException`, so it does not help. By the time the lookup fails, Gemini has already run and `@CheckEntitlement` has already counted the quota. The user gets an error, nothing is saved, and they lose a quota unit, all because of a best-effort lookup. No test covers a lookup that throws.

Fix: In `uploadRecipe`, or in both `findRecipeYaml` implementations, catch `RuntimeException` around the lookup, log a warning and use `Optional.empty()`. Keep the Dropbox not-found case as a debug-level path.

_confidence: 99 | sources: bugs, adversarial | lenses: bugs, adversarial | verdict: confirmed_

## Immaterial

### Whole-file writes have no guard against concurrent re-saves

`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java:333`

Concurrent POST and PATCH requests can overwrite fields they did not mean to change. Example: PATCH reads the old recipe, POST uploads newly extracted ingredients, then PATCH uploads its old snapshot with the updated tags, and the new ingredients disappear. In the reverse order, POST reads the old tags, PATCH replaces them, and POST then restores the old tags. Both providers write unconditionally, so the new flow does not give the field-level last-write-wins behaviour the contract promises.

Fix: Use provider revision checks and retry against current content, preserving unrelated fields on PATCH and current user tags on re-save.

_confidence: 85 | sources: adversarial | lenses: adversarial | verdict: immaterial_

### Dropbox commits to a resolved path without checking file identity

`extractor/src/main/java/net/shamansoft/cookbook/service/DropboxStorageProvider.java:133-134`

This needs the user or Dropbox sync to move the file between getMetadata and upload. The upload then goes to the old path with mode=overwrite. That creates a new file if the path is now empty, or overwrites whatever replaced it. The file with the requested ID stays unchanged at its new location. updateRecipe then fetches that ID's metadata and can return 200 saying the tags were updated. The new ID-to-path write introduces this race.

Fix: Commit using a provider-supported identity/revision condition so a move or replacement cannot redirect the write.

_confidence: 85 | sources: adversarial | lenses: adversarial | verdict: immaterial_

### PATCH writes a partial recipe file before response mapping fails

`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java:331-336`

Take a manually edited stored file that contains only `metadata: {title: Pancakes}`. recipeParser.parse succeeds because it deserializes without Bean Validation. PATCH then writes user_tags, and only after that does RecipeMapper.extractAllImageUrls iterate the null instructions list and throw. The request returns 500 after changing an invalid file, instead of the promised 422 with the file unchanged. The mapper weakness predates this change; writing before reaching it is new.

Fix: Check the stored recipe's required structure and ensure DTO mapping succeeds before writing; translate invalid stored content to InvalidRecipeFormatException.

_confidence: 85 | sources: adversarial | lenses: adversarial | verdict: immaterial_

## Sources

| agent | executor | model | effort | tokens | raised | status |
| --- | --- | --- | --- | --- | --- | --- |
| bugs | claude | claude-opus-5-5 (requested opus) | high | 1443355 | 1 | ok |
| adversarial | codex | gpt-6.1-sol | high | 80917 | 4 | ok |
