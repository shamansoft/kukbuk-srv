# Scope
- Branch `feat/user-notes` vs `feat/user-tags` (stacked), worktree `/Users/alexey/dev/sar/sar-srv/.claude/worktrees/user-notes`
- See it: `git diff feat/user-tags...HEAD` (17 files, ~+305/-17). Only this range is under review.
- Adds `userNotes` to PATCH body (`RecipeUpdateRequest`), `UserNotes` validation, `user_notes` YAML key on recipe-sdk `Recipe`, RecipeDto/RecipeMapper, carry-over on re-save next to user tags
- Must stay separate from the existing page `notes` field
- Contract: `/Users/alexey/dev/sar/feature-requests/user-tags.md` section "Contract"
- Ignore: docs wording
