# Scope
- Branch `feat/user-tags` vs `origin/main`, worktree `/Users/alexey/dev/sar/sar-srv/.claude/worktrees/user-tags`
- See it: `git diff origin/main...HEAD` (30 files, +1027/-26)
- New `PATCH /v1/recipes/{id}` (user tags), `userTags` on RecipeDto, `user_tags` key in stored YAML (recipe-sdk `Recipe`), Drive + Dropbox write-by-id, carry-over of user tags on re-save (`RecipeService.uploadRecipe`), global 400 for malformed JSON, PATCH in CORS
- Read in full: RecipeController, RecipeService (update + uploadRecipe), RecipeUpdateRequest, Dropbox/Drive provider changes
- Contract: `/Users/alexey/dev/sar/feature-requests/user-tags.md` section "Contract"
- Ignore: docs wording, OpenAPI formatting
