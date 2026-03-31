# Orchestration: Terraform Repo Split

**Date**: 2026-03-27
**Human operator**: coordinates parallel agents and performs manual steps

This file is the human's script. The individual plans below are executed by ralphex (automated).

---

## Plans Overview

| Plan file | Repo | Phase | Depends on |
|-----------|------|-------|------------|
| `sar-infra/docs/plans/20260327-terraform-split-sar-infra.md` | sar-infra | Phase 1 | PAT created |
| `sar-srv/docs/plans/20260327-terraform-split-sar-srv-1-pipeline.md` | sar-srv | Phase 1 | PAT created |
| `sar-srv/docs/plans/20260327-terraform-split-sar-srv-2-cutover.md` | sar-srv | Phase 2 | sar-infra plan + sar-srv plan 1 both complete |

---

## Step 0 — Manual Prerequisite (YOU do this before starting any agent)

### Create `CROSS_REPO_PAT`

GitHub web UI only — cannot be automated.

1. Go to: **GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens**
2. Generate new token:
   - **Name**: `sar-cross-repo-dispatch`
   - **Resource owner**: khisamutdinov
   - **Repository access**: Select → `sar-srv` only for now (add `sar-infra` after it's created)
   - **Permissions**: Actions → Read/Write · Contents → Read/Write · Metadata → Read
3. Copy and save the token value — you'll need it in two places:
   - Export for sar-srv agent: `export CROSS_REPO_PAT=ghp_...`
   - Export for sar-infra agent: `export CROSS_REPO_PAT=ghp_...`

### Locate `GCP_SA_KEY`

The sar-infra agent needs to set this secret. Export the key file contents:

```bash
export GCP_SA_KEY=$(cat /path/to/kukbuk-sa-tf-key.json)
```

---

## Step 1 — Phase 1: Run Both Agents in Parallel

Start both simultaneously (two terminals / two ralphex sessions):

**Terminal A — sar-infra:**
```bash
cd /Users/alexey/dev/sar/sar-infra
export CROSS_REPO_PAT=ghp_...
export GCP_SA_KEY=$(cat /path/to/kukbuk-sa-tf-key.json)
ralphex run docs/plans/20260327-terraform-split-sar-infra.md
```

**Terminal B — sar-srv:**
```bash
cd /Users/alexey/dev/sar/sar-srv
export CROSS_REPO_PAT=ghp_...
ralphex run docs/plans/20260327-terraform-split-sar-srv-1-pipeline.md
```

**Wait** for both agents to complete. Expected duration: ~15–20 min each.

### After sar-infra is created (during Phase 1):

Update the PAT to include the new repo:
- GitHub → Settings → Fine-grained tokens → `sar-cross-repo-dispatch` → Edit
- Add `sar-infra` to Repository access
- Save

---

## Step 2 — Gate Check: Verify Both Phase 1 Plans Passed

Before proceeding, confirm:

```bash
# sar-infra: repo exists with workflows and secrets
gh repo view shamansoft/sar-infra
gh secret list -R shamansoft/sar-infra        # expect: GCP_SA_KEY, CROSS_REPO_PAT
gh workflow list -R shamansoft/sar-infra       # expect: Deploy Infrastructure, Validate Infrastructure

# sar-infra: dry-run passed (check last Actions run)
gh run list -R shamansoft/sar-infra --limit 3  # expect: green run from dry-run validation

# sar-srv: deploy.yml was changed and PR exists
gh pr list -R shamansoft/kukbuk-srv               # expect: PR with title "feat: cross-repo dispatch..."
gh secret list -R shamansoft/kukbuk-srv           # expect: CROSS_REPO_PAT present
```

If anything is red → fix before proceeding.

---

## Step 3 — Phase 2: Run sar-srv Cutover Agent

Only after both Phase 1 agents are green:

```bash
cd /Users/alexey/dev/sar/sar-srv
export CROSS_REPO_PAT=ghp_...
ralphex run docs/plans/20260327-terraform-split-sar-srv-2-cutover.md
```

This agent will:
1. Merge the pipeline PR (triggers first real cross-repo deploy)
2. Watch both repos' Actions for green runs
3. Verify production health
4. Open cleanup PR (remove `terraform/`)

**Wait** for agent to complete.

---

## Step 4 — Final Gate: Review and Merge Cleanup PR

The cleanup PR removes `terraform/` from sar-srv. This is irreversible — review before merging.

```bash
gh pr list -R shamansoft/kukbuk-srv   # find the cleanup PR
gh pr view <number> --web             # review in browser
gh pr merge <number> --squash         # merge when satisfied
```

---

## Rollback (if anything goes wrong)

**If Phase 1 sar-infra fails** → discard, no production impact. Fix and retry.

**If Phase 1 sar-srv fails** → PR not merged yet, no production impact. Fix and retry.

**If Phase 2 cutover fails during e2e test** → pipeline PR is merged but uses new dispatch workflow.
  - Temporary rollback: revert the deploy.yml change on a hotfix PR
  - The `terraform/` directory is still in sar-srv (cleanup not done yet)
  - Tofu state is untouched — sar-infra and sar-srv share same GCS backend

**If cleanup PR is merged and something breaks** → terraform is gone from sar-srv.
  - Cherry-pick `terraform/` back from git history: `git show <commit>:terraform/ | ...`
  - Or deploy infrastructure manually from sar-infra via `workflow_dispatch`
