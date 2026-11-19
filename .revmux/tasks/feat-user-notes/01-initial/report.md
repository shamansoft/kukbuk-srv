# Review: feat-user-notes / 01-initial

scope: `/Users/alexey/dev/sar/sar-srv/.revmux/tasks/feat-user-notes/01-initial/input/scope.md`

No findings.

## Pre-existing

### A failed read of the existing file is treated as no file, which erases the saved note

`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java:193`

When an existing recipe is saved again, a temporary failure reading its content is turned into Optional.empty() by findExistingYaml. That sets userNotes to an empty string. If the upload then succeeds, uploadRecipe overwrites the existing file without user_notes, so the user's saved note is erased because of a failed read. Tags already worked this way. This change makes notes work the same way, which breaks the promise that user data is kept when a recipe is saved again.

Fix: Tell a confirmed missing file apart from a read failure, and stop the overwrite when the existing user data cannot be read. Make sure provider implementations pass on every failure except a confirmed file-not-found.

_confidence: 95 | sources: adversarial | lenses: adversarial | verdict: pre_existing_

## Immaterial

### Concurrent field patches write stale copies of the entire YAML

`extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java:349-355`

If a PATCH that changes only tags and a PATCH that changes only notes both read the same file before either one writes, each edits its own copy and replaces the whole file. The second write puts back the old value of the field the first request changed, even though both requests succeed. This breaks the promise that fields absent from a request stay unchanged, and the promise that the last write wins per field. The unconditional read-then-write was already there, but adding userNotes creates two fields that can be edited separately, which exposes this conflict.

Fix: Write with a storage revision precondition. On conflict, read the latest file, reapply only the fields present in the request, and retry.

_confidence: 95 | sources: adversarial | lenses: adversarial | verdict: immaterial_

## Sources

| agent | executor | model | effort | tokens | raised | status |
| --- | --- | --- | --- | --- | --- | --- |
| bugs | claude | claude-opus-5-5 (requested opus) | high | 311544 | 0 | ok, nothing raised |
| adversarial | codex | gpt-6.1-sol | high | 74298 | 2 | ok |
