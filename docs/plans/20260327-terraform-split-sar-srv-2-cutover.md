# Terraform Split — sar-srv Cutover, E2E Test, and Cleanup

## Overview

Merge the pipeline PR (created by `sar-srv-1-pipeline.md`), verify the full cross-repo deploy chain works end-to-end, then remove `terraform/` from sar-srv.

**Problem solved**: After this plan, sar-srv contains no infrastructure code.

**Integration points**: `.github/workflows/deploy.yml` (new `trigger-deploy` job live), `sar-infra` deploy workflow (receives `repository_dispatch`), `terraform/` directory (deleted).

**Depends on**:
- `sar-infra/docs/plans/20260327-terraform-split-sar-infra.md` — must be fully complete (repo exists, workflows committed, secrets set, dry-run passed ✅)
- `docs/plans/20260327-terraform-split-sar-srv-1-pipeline.md` — must be fully complete (PR open ✅)
- Human operator must confirm both are green before launching this plan (see `docs/plans/20260327-split-orchestration.md` Step 2)

---

## Context

- **PR to merge**: `feat/cross-repo-dispatch` (created by Plan 1)
- **Files to delete**: `terraform/` directory (entire tree)
- **Files to update**: `.gitignore` (remove terraform entries), `CLAUDE.md` (remove terraform sections, add sar-infra pointer), `docs/CI_CD_WORKFLOW.md`, `docs/deployment/strategy.md` (if they exist)
- **Verification**: watch both `sar-srv` and `sar-infra` Actions runs after merge

---

## Development Approach

- Merge the pipeline PR first — this triggers the live deploy chain.
- Only proceed to cleanup after the end-to-end test passes.
- Cleanup PR is separate so it can be reviewed independently.

---

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix

---

## What Goes Where

**Implementation Steps**: PR merges, gh CLI monitoring, file deletions, doc updates — automated by claude-code.

**Post-Completion**: cleanup PR review and merge by human (irreversible — terraform deleted from history perspective).

---

## Implementation Steps

### Task 5: Remove `terraform/` directory

Only run after Task 4 confirms the pipeline is healthy.

- [x] `git checkout main && git pull`
- [x] `git checkout -b chore/remove-terraform`
- [x] `git rm -r terraform/`
- [x] confirm deletion: `git status` shows all `terraform/` files as deleted

### Task 6: Update `.gitignore`

- [x] read `.gitignore`
- [x] remove these lines (no longer relevant in sar-srv): `.terraform/`, `*.tfstate`, `*.tfstate.*`, `*.tfvars`, `no-git/`
- [x] verify `.gitignore` no longer contains any of those patterns: `grep -E "\.terraform|tfstate|tfvars|no-git" .gitignore` → empty

### Task 7: Update `CLAUDE.md`

- [x] read `CLAUDE.md` to locate the "Infrastructure (Google Cloud Platform)" section
- [x] replace the entire section content (list of terraform resources, files) with:
  ```markdown
  Infrastructure is managed in the [`sar-infra`](https://github.com/shamansoft/sar-infra) repository using OpenTofu. See that repo for `.tf` files, deploy scripts, and infrastructure docs.
  ```
- [x] in the "Critical Files Reference" section, find the Infrastructure subsection and replace the list of `.tf` files with:
  ```markdown
  - See [`shamansoft/sar-infra`](https://github.com/shamansoft/sar-infra) for all infrastructure files
  - `.github/workflows/deploy.yml` — triggers sar-infra deployment via `repository_dispatch` after image push
  ```
- [x] remove any mention of `terraform/**` in paths-ignore description
- [x] update deploy command reference from `terraform/scripts/deploy.sh` to `gh workflow run deploy.yml -R shamansoft/sar-infra`
- [x] verify no remaining references to `terraform/` directory paths in CLAUDE.md: `grep "terraform/" CLAUDE.md` → empty (links to sar-infra repo are fine)

### Task 8: Update documentation files

- [x] check if `docs/CI_CD_WORKFLOW.md` exists: if yes, find Phase 3 deploy description and replace with "Triggers sar-infra deployment via `repository_dispatch`"
- [x] check if `docs/deployment/strategy.md` exists: if yes, add pointer to `sar-infra` repo for infrastructure deploy steps
- [x] add superseded notice to `docs/plans/20260315-terraform-repo-separation.md`: prepend `> **Status**: Superseded by 20260327-terraform-split-sar-infra.md and 20260327-terraform-split-sar-srv-1-pipeline.md + sar-srv-2-cutover.md`

### Task 9: Commit and create cleanup PR

- [x] `git add -A`
- [x] `git commit -m "chore: remove terraform — infrastructure moved to sar-infra"`
- [x] `git push -u origin chore/remove-terraform`
- [x] create PR:
  ```
  gh pr create \
    --title "chore: remove terraform — infrastructure moved to sar-infra" \
    --body "Removes terraform/ directory (now in shamansoft/sar-infra). Cleans up .gitignore, CLAUDE.md, and docs. End-to-end deploy pipeline verified before this cleanup."
  ```
- [x] print PR URL

### Task 10: Verify acceptance criteria

- [ ] `ls terraform/` on main returns "no such file or directory" (directory was deleted)
- [ ] `grep -r "terraform/" .github/workflows/` → empty
- [ ] `gh pr list -R shamansoft/kukbuk-srv` shows the cleanup PR open and ready for review
- [ ] `gh run list -R shamansoft/sar-infra --limit 1 --json conclusion -q '.[0].conclusion'` → `success`

---

## Post-Completion

**Human action required** — review and merge the cleanup PR:

```bash
gh pr view --web   # review in browser
gh pr merge <number> --squash
```

This is irreversible: once merged, `terraform/` is gone from sar-srv's working tree (still in git history). Verify production is healthy before merging:

```bash
curl -s https://<cloud-run-url>/actuator/health | grep -q "UP" && echo "✅ Production healthy"
```
