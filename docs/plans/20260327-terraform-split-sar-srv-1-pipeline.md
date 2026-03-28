# Terraform Split — sar-srv Pipeline Changes

## Overview

Replace the embedded terraform `deploy` job in `.github/workflows/deploy.yml` with a `trigger-deploy` job that fires a `repository_dispatch` event to `sar-infra`. After this change, sar-srv's pipeline ends at image push — all infrastructure work happens in sar-infra.

**Problem solved**: sar-srv deploy workflow contains terraform steps that belong in the infrastructure repo.

**Integration points**: `.github/workflows/deploy.yml` (Phase 3 deploy job → trigger-deploy), `finalize` job (remove dependency on deploy job outputs).

**Depends on**: `CROSS_REPO_PAT` exported as env var before running (see `docs/plans/20260327-split-orchestration.md` Step 0).

**Runs in parallel with**: `sar-infra/docs/plans/20260327-terraform-split-sar-infra.md` — no dependency between these two Phase 1 plans.

**Unblocks**: `docs/plans/20260327-terraform-split-sar-srv-2-cutover.md` — do not run that plan until both this plan AND the sar-infra plan are complete.

---

## Context

- **Files to modify**: `.github/workflows/deploy.yml`
- **Key changes**:
  - Remove job `deploy` (lines ~258–351): the entire tofu init/plan/apply/health-check job
  - Add job `trigger-deploy` after `build-and-push`: fires `repository_dispatch` to `shamansoft/sar-infra`
  - Update job `finalize`: replace all `needs.deploy` references with `needs.trigger-deploy`; remove `needs.deploy.outputs.URL` from release body; add link to sar-infra Actions instead
  - Remove `include_deploy` from `workflow_dispatch` inputs; add `skip_deploy_trigger` boolean
  - Remove `terraform/**` from `paths-ignore`
- **New secret**: `CROSS_REPO_PAT` (set via `gh secret set`)
- **No tests needed**: workflow YAML changes only; verified by the cutover plan's e2e test

---

## Development Approach

- Make all changes to `deploy.yml` in a single branch; create a PR but do NOT merge.
- The PR merge is the responsibility of `sar-srv-2-cutover.md` — it triggers the first live deploy.
- Verify YAML syntax after editing.

---

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix

---

## What Goes Where

**Implementation Steps**: all file edits, secret setup, branch/PR creation — automated by claude-code.

**Post-Completion**: the PR merge is done by `sar-srv-2-cutover.md` after sar-infra is ready.

---

## Implementation Steps

### Task 1: Create feature branch

- [x] `git checkout -b feat/cross-repo-dispatch`
- [x] confirm clean working tree: `git status`

### Task 2: Remove Phase 3 `deploy` job from deploy.yml

- [x] read `.github/workflows/deploy.yml` to locate the `deploy:` job block
- [x] delete the entire `deploy:` job — from `# Phase 3: Deploy to Cloud Run` comment through the final `Cleanup` step (including `if: always()` cleanup step)
- [x] verify no remaining references to `tofu`, `terraform`, or `working-directory: terraform` in the file

### Task 3: Add `trigger-deploy` job

Insert immediately after the `build-and-push:` job closing line:

- [x] add job `trigger-deploy` with:
  - `name: Trigger Deploy`
  - `needs: [test, build-and-push]`
  - `runs-on: ubuntu-latest`
  - `if: ${{ (github.event_name == 'push' || inputs.include_build != false) && inputs.skip_deploy_trigger != true && github.ref == 'refs/heads/main' }}`
  - step `Dispatch deploy to sar-infra` using `peter-evans/repository-dispatch@v3` with `token: ${{ secrets.CROSS_REPO_PAT }}`, `repository: shamansoft/sar-infra`, `event-type: deploy`, `client-payload` containing `image_tag`, `commit_sha`, `source_repo`
  - step `Summary` writing image tag and link to sar-infra Actions to `$GITHUB_STEP_SUMMARY`
- [x] verify indentation is consistent with surrounding jobs (2-space YAML)

### Task 4: Update `finalize` job

- [x] change `needs: [test, build-and-push, deploy]` → `needs: [test, build-and-push, trigger-deploy]`
- [x] in the `if:` condition, replace both occurrences of `needs.deploy.result` → `needs.trigger-deploy.result`
- [x] find the `Create GitHub Release` step:
  - change its `if:` from `needs.deploy.result == 'success'` → `needs.trigger-deploy.result == 'success' || needs.trigger-deploy.result == 'skipped'`
  - in the release `body:`, remove the `**Service URL**: ${{ needs.deploy.outputs.URL }}` line
  - replace it with `**Deploy status**: [Check sar-infra Actions](https://github.com/shamansoft/sar-infra/actions)`
  - change `✅ Health check verified` → `🔄 Deployment triggered (see sar-infra for health check result)`
- [x] find the `Deployment summary` step at the end of `finalize`:
  - replace the `if [[ "${{ needs.deploy.result }}" == "success" ]]` block
  - use `needs.trigger-deploy.result` instead
  - replace `Service URL` / `URL` output lines with a link to sar-infra Actions
- [x] verify no remaining references to `needs.deploy` in the file: `grep -n "needs.deploy" .github/workflows/deploy.yml` must return empty

### Task 5: Update `workflow_dispatch` inputs

- [x] remove the `include_deploy` input block entirely
- [x] add `skip_deploy_trigger` input after `include_build`:
  ```yaml
        skip_deploy_trigger:
          description: 'Skip triggering sar-infra deployment (build only)'
          required: false
          type: boolean
          default: false
  ```
- [x] verify `include_deploy` no longer appears in the file

### Task 6: Update `paths-ignore`

- [x] remove the `- 'terraform/**'` line from the `paths-ignore` list in the `push:` trigger
- [x] verify `terraform` no longer appears under `paths-ignore`

### Task 7: Verify YAML syntax

- [x] check YAML is valid: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy.yml'))"` — no errors
- [x] check job dependency graph is consistent: `grep -E "needs:" .github/workflows/deploy.yml` — `finalize` needs `trigger-deploy`, not `deploy`
- [x] confirm the file has no references to the old `deploy` job: `grep -n "needs\.deploy\b" .github/workflows/deploy.yml` → empty

### Task 8: Configure `CROSS_REPO_PAT` secret

Requires `CROSS_REPO_PAT` to be set in the shell environment.

- [x] `gh secret set CROSS_REPO_PAT --repo shamansoft/kukbuk-srv --body "$CROSS_REPO_PAT"`
- [x] verify: `gh secret list -R shamansoft/kukbuk-srv | grep CROSS_REPO_PAT`

### Task 9: Commit and create PR

- [x] `git add .github/workflows/deploy.yml`
- [x] `git commit -m "feat: replace deploy job with cross-repo dispatch to sar-infra"`
- [x] `git push -u origin feat/cross-repo-dispatch`
- [x] create PR: `gh pr create --title "feat: cross-repo dispatch — move deploy to sar-infra" --body "Replaces embedded terraform deploy with repository_dispatch to sar-infra. DO NOT MERGE until sar-infra plan is complete (see docs/plans/20260327-split-orchestration.md)."`
- [x] confirm PR URL is printed — note it for the cutover plan

### Task 10: Verify acceptance criteria

- [x] PR exists and is open: `gh pr list -R shamansoft/kukbuk-srv`
- [x] `grep -c "tofu\|terraform" .github/workflows/deploy.yml` → 0
- [x] `grep "trigger-deploy" .github/workflows/deploy.yml` → found in `finalize` needs
- [x] `gh secret list -R shamansoft/kukbuk-srv | grep CROSS_REPO_PAT` → found

---

## Post-Completion

**Do not merge the PR** — it is merged by `sar-srv-2-cutover.md`.

Inform the human operator that Phase 1 sar-srv is complete. They will check if sar-infra Phase 1 is also done before launching the cutover plan.
