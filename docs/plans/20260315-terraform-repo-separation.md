> **Status**: Superseded by 20260327-terraform-split-sar-infra.md and 20260327-terraform-split-sar-srv-1-pipeline.md + sar-srv-2-cutover.md

# Plan: Separate OpenTofu Infrastructure into Dedicated Repository

**Date**: 2026-03-15
**Branch**: N/A (cross-repo migration)
**Status**: Draft

## Goal

Move all OpenTofu/Terraform infrastructure code from `sar-srv` into a new `sar-infra` repository, while maintaining automated deployment triggered by app merges to `main`. Use the **Repository Dispatch** pattern (Option A) for cross-repo coordination.

## Current State

Single repo (`sar-srv`) with a monolithic deploy workflow:
```
Phase 0-1: Test & Validate (extract version, run tests)
Phase 2:   Build & Push (native Docker image → GCR)
Phase 3:   Deploy (tofu init/plan/apply → Cloud Run, health check)
Phase 4:   Finalize (git tag, version bump, GitHub Release)
```

The coupling point is `image_tag` — extracted from `build.gradle.kts` in Phase 0, passed to `tofu apply` in Phase 3.

## Target State

Two repositories:
- **`sar-srv`** — application code, tests, Docker build, Phase 0-2 + 4
- **`sar-infra`** — all `.tf` files, deploy workflow (Phase 3), infra-only PRs/changes

```
sar-srv merge to main:
  Phase 0-1: Test → Phase 2: Build & Push → repository_dispatch("deploy") → Phase 4: Finalize
                                                     ↓
sar-infra (triggered):
  Deploy: tofu init → plan → apply → health check → callback status
```

---

## Table of Contents

- [ ] 1. Create `sar-infra` repository
  - [ ] 1.1 Initialize repo with README and .gitignore
  - [ ] 1.2 Move terraform files
  - [ ] 1.3 Move supporting files (Makefile, Dockerfiles, scripts)
  - [ ] 1.4 Adjust paths (no longer nested under `terraform/`)
  - [ ] 1.5 Verify `tofu init` works standalone
- [ ] 2. Create deploy workflow in `sar-infra`
  - [ ] 2.1 `repository_dispatch` trigger for automated deploys
  - [ ] 2.2 `workflow_dispatch` trigger for manual/infra-only deploys
  - [ ] 2.3 Deploy job (tofu init/plan/apply/health check)
  - [ ] 2.4 Status callback to `sar-srv` (deployment-status dispatch)
  - [ ] 2.5 Add `tofu validate` / `tofu fmt` PR check workflow
- [ ] 3. Modify `sar-srv` deploy workflow
  - [ ] 3.1 Remove Phase 3 (deploy job) entirely
  - [ ] 3.2 Add repository_dispatch step after Phase 2
  - [ ] 3.3 Update Phase 4 to handle async deploy status
  - [ ] 3.4 Update `workflow_dispatch` inputs (remove `include_deploy`)
  - [ ] 3.5 Update deploy workflow `paths-ignore`
- [ ] 4. Configure secrets and permissions
  - [ ] 4.1 Create cross-repo PAT or GitHub App token
  - [ ] 4.2 Configure `GCP_SA_KEY` in `sar-infra`
  - [ ] 4.3 Configure GitHub App for `sar-infra` (branch protection bypass)
- [ ] 5. Clean up `sar-srv`
  - [ ] 5.1 Remove `terraform/` directory
  - [ ] 5.2 Update `.gitignore` (remove terraform entries)
  - [ ] 5.3 Update `CLAUDE.md` (remove/redirect terraform sections)
  - [ ] 5.4 Update documentation references
- [ ] 6. Validation and cutover
  - [ ] 6.1 Dry-run: manual dispatch in `sar-infra` with current image tag
  - [ ] 6.2 End-to-end test: PR merge to `sar-srv` main triggers infra deploy
  - [ ] 6.3 Verify health check and GitHub Release still work
  - [ ] 6.4 Verify infra-only change (e.g., Firestore index) deploys independently

---

## Detailed Plan

### 1. Create `sar-infra` Repository

**Objective:** Stand up the new repo with all infrastructure code, verifiable independently.

#### 1.1 Initialize repo

```bash
gh repo create khisamutdinov/sar-infra --private --description "Infrastructure (OpenTofu) for Save-a-Recipe"
```

Add `.gitignore`:
```gitignore
# OpenTofu / Terraform
.terraform/
*.tfstate
*.tfstate.*
*.tfvars
no-git/
crash.log
*.tfplan
```

#### 1.2 Move terraform files

Files to move from `sar-srv/terraform/` to `sar-infra/` (root level):

| Source (`sar-srv/terraform/`)     | Destination (`sar-infra/`) |
|-----------------------------------|----------------------------|
| `main.tf`                         | `main.tf`                  |
| `cloudrun.tf`                     | `cloudrun.tf`              |
| `firestore.tf`                    | `firestore.tf`             |
| `iam.tf`                          | `iam.tf`                   |
| `apis.tf`                         | `apis.tf`                  |
| `kms.tf`                          | `kms.tf`                   |
| `token-broker.tf`                 | `token-broker.tf`          |
| `variables.tf`                    | `variables.tf`             |
| `versions.tf`                     | `versions.tf`              |
| `.terraform.lock.hcl`            | `.terraform.lock.hcl`      |
| `firestore.rules`                | `firestore.rules`          |
| `firestore-schema.md`            | `docs/firestore-schema.md` |
| `Makefile`                        | `Makefile`                 |
| `Dockerfile`                      | `Dockerfile`               |
| `Dockerfile.minimal`             | `Dockerfile.minimal`       |
| `README.md`                      | `README.md`                |

#### 1.3 Adjust Makefile paths

The current Makefile mounts `-v "$(PWD)/..:/workspace"` (parent of terraform dir). After the move, `.tf` files are at repo root, so adjust:
- Working directory: `-w /workspace` (root, not `/workspace/terraform`)
- Volume mount: `-v "$(PWD):/workspace"` (current dir, not parent)

#### 1.4 Verify standalone operation

```bash
cd sar-infra
tofu init        # must connect to existing GCS backend
tofu plan -var="image_tag=0.14.0" -var="project_id=kukbuk-tf"
# Should show "No changes" (infrastructure already matches state)
```

**Critical**: The GCS backend (`kukbuk-tf-tfstate-bucket`) and state file remain unchanged. The new repo reads the same state — no state migration needed.

**Files to create/modify:** New repo structure
**Dependencies:** GitHub CLI (`gh`) authenticated, GCP service account key available
**Expected outcome:** `tofu plan` shows no changes from the new repo

---

### 2. Create Deploy Workflow in `sar-infra`

**Objective:** Handle both app-triggered and infra-only deployments.

#### 2.1-2.3 Main deploy workflow

**File:** `sar-infra/.github/workflows/deploy.yml`

```yaml
name: Deploy Infrastructure

on:
  # Triggered by sar-srv after image push
  repository_dispatch:
    types: [deploy]

  # Manual trigger for infra-only changes or re-deploys
  workflow_dispatch:
    inputs:
      image_tag:
        description: 'Docker image tag to deploy'
        required: true
        type: string
      commit_sha:
        description: 'Source commit SHA (for traceability)'
        required: false
        type: string
      skip_health_check:
        description: 'Skip health check after deploy'
        required: false
        type: boolean
        default: false

concurrency:
  group: deploy-production
  cancel-in-progress: false

env:
  GCP_PROJECT_ID: kukbuk-tf
  REGION: us-west1
  SERVICE_NAME: cookbook

jobs:
  deploy:
    name: Deploy to Cloud Run
    runs-on: ubuntu-latest
    environment: production
    outputs:
      cloud_run_url: ${{ steps.deploy.outputs.URL }}
      status: ${{ steps.result.outputs.status }}

    steps:
    - uses: actions/checkout@v4

    - name: Resolve inputs
      id: inputs
      run: |
        # repository_dispatch passes payload; workflow_dispatch uses inputs
        if [ "${{ github.event_name }}" = "repository_dispatch" ]; then
          echo "image_tag=${{ github.event.client_payload.image_tag }}" >> $GITHUB_OUTPUT
          echo "commit_sha=${{ github.event.client_payload.commit_sha }}" >> $GITHUB_OUTPUT
          echo "source_repo=${{ github.event.client_payload.source_repo }}" >> $GITHUB_OUTPUT
          echo "skip_health_check=false" >> $GITHUB_OUTPUT
        else
          echo "image_tag=${{ inputs.image_tag }}" >> $GITHUB_OUTPUT
          echo "commit_sha=${{ inputs.commit_sha || 'manual' }}" >> $GITHUB_OUTPUT
          echo "source_repo=manual" >> $GITHUB_OUTPUT
          echo "skip_health_check=${{ inputs.skip_health_check }}" >> $GITHUB_OUTPUT
        fi

    - name: Validate image tag
      run: |
        TAG="${{ steps.inputs.outputs.image_tag }}"
        if [ -z "$TAG" ]; then
          echo "::error::image_tag is required"
          exit 1
        fi
        echo "Deploying image tag: $TAG"

    - uses: google-github-actions/auth@v2
      with:
        credentials_json: ${{ secrets.GCP_SA_KEY }}

    - uses: google-github-actions/setup-gcloud@v2

    - name: Install OpenTofu
      uses: opentofu/setup-opentofu@v1
      with:
        tofu_version: '1.8.8'

    - name: Terraform Init
      run: tofu init

    - name: Terraform Plan
      run: |
        tofu plan \
          -var="image_tag=${{ steps.inputs.outputs.image_tag }}" \
          -var="project_id=${{ env.GCP_PROJECT_ID }}" \
          -out=tfplan

    - name: Terraform Apply
      run: tofu apply -auto-approve tfplan

    - name: Get deployment URL
      id: deploy
      run: |
        URL=$(tofu output -raw cloud_run_url)
        echo "URL=$URL" >> $GITHUB_OUTPUT

    - name: Health check
      if: steps.inputs.outputs.skip_health_check != 'true'
      run: |
        echo "Testing health endpoint..."
        MAX_ATTEMPTS=30
        DELAY=10
        for i in $(seq 1 $MAX_ATTEMPTS); do
          echo "Attempt $i/$MAX_ATTEMPTS..."
          if curl -f -s "${{ steps.deploy.outputs.URL }}/actuator/health" | grep -q "UP"; then
            echo "Health check passed"
            exit 0
          fi
          if [ $i -lt $MAX_ATTEMPTS ]; then
            sleep $DELAY
          fi
        done
        echo "::error::Health check failed"
        exit 1

    - name: Set result
      id: result
      if: always()
      run: |
        if [ "${{ job.status }}" = "success" ]; then
          echo "status=success" >> $GITHUB_OUTPUT
        else
          echo "status=failure" >> $GITHUB_OUTPUT
        fi

    - name: Summary
      if: always()
      run: |
        echo "## Deployment Result" >> $GITHUB_STEP_SUMMARY
        echo "- **Image**: gcr.io/kukbuk-tf/cookbook:${{ steps.inputs.outputs.image_tag }}" >> $GITHUB_STEP_SUMMARY
        echo "- **URL**: ${{ steps.deploy.outputs.URL }}" >> $GITHUB_STEP_SUMMARY
        echo "- **Source commit**: ${{ steps.inputs.outputs.commit_sha }}" >> $GITHUB_STEP_SUMMARY
        echo "- **Status**: ${{ job.status }}" >> $GITHUB_STEP_SUMMARY

  # Notify sar-srv of deployment result
  notify:
    name: Notify Source Repo
    needs: deploy
    runs-on: ubuntu-latest
    if: github.event_name == 'repository_dispatch'
    steps:
    - name: Send deployment status
      uses: peter-evans/repository-dispatch@v3
      with:
        token: ${{ secrets.CROSS_REPO_PAT }}
        repository: ${{ github.event.client_payload.source_repo }}
        event-type: deployment-status
        client-payload: |
          {
            "image_tag": "${{ github.event.client_payload.image_tag }}",
            "status": "${{ needs.deploy.outputs.status }}",
            "cloud_run_url": "${{ needs.deploy.outputs.cloud_run_url }}",
            "deploy_run_url": "${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
          }
```

#### 2.5 PR validation workflow for infra changes

**File:** `sar-infra/.github/workflows/pr-validation.yml`

```yaml
name: Validate Infrastructure

on:
  pull_request:
    branches: [main]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: opentofu/setup-opentofu@v1
      with:
        tofu_version: '1.8.8'
    - uses: google-github-actions/auth@v2
      with:
        credentials_json: ${{ secrets.GCP_SA_KEY }}
    - run: tofu init
    - run: tofu fmt -check -recursive
    - run: tofu validate
    - name: Plan (dry run)
      run: |
        tofu plan \
          -var="project_id=kukbuk-tf" \
          -detailed-exitcode || true
```

**Files to create:** `.github/workflows/deploy.yml`, `.github/workflows/pr-validation.yml`
**Dependencies:** `CROSS_REPO_PAT` secret, `GCP_SA_KEY` secret
**Expected outcome:** Workflow responds to both dispatch types, deploys, health checks, notifies back

---

### 3. Modify `sar-srv` Deploy Workflow

**Objective:** Remove Phase 3, add cross-repo dispatch, handle async deploy status.

#### 3.1-3.2 Replace Phase 3 with dispatch

Remove the entire `deploy` job. Add a new `trigger-deploy` job after `build-and-push`:

```yaml
  # Phase 3: Trigger Infrastructure Deployment
  trigger-deploy:
    name: Trigger Deploy
    needs: [test, build-and-push]
    runs-on: ubuntu-latest
    if: ${{ github.event_name == 'push' || inputs.include_build != false }}

    steps:
    - name: Trigger sar-infra deployment
      uses: peter-evans/repository-dispatch@v3
      with:
        token: ${{ secrets.CROSS_REPO_PAT }}
        repository: khisamutdinov/sar-infra
        event-type: deploy
        client-payload: >
          {
            "image_tag": "${{ needs.test.outputs.release-version }}",
            "commit_sha": "${{ github.sha }}",
            "source_repo": "${{ github.repository }}"
          }

    - name: Summary
      run: |
        echo "## Deployment Triggered" >> $GITHUB_STEP_SUMMARY
        echo "- **Image tag**: ${{ needs.test.outputs.release-version }}" >> $GITHUB_STEP_SUMMARY
        echo "- **Target**: sar-infra repository" >> $GITHUB_STEP_SUMMARY
        echo "- Check [sar-infra Actions](https://github.com/khisamutdinov/sar-infra/actions) for deploy status" >> $GITHUB_STEP_SUMMARY
```

#### 3.3 Update Phase 4 (Finalize)

Phase 4 currently depends on `deploy` job's success. Two options:

**Option A (simpler — decouple finalize from deploy):**
- Finalize runs after `trigger-deploy`, not after deploy completes
- Git tag + version bump happen immediately after image push
- GitHub Release body notes "deployment in progress" with link to infra repo
- Pro: No cross-repo waiting. Con: Release created before deploy confirmed.

**Option B (callback-based — preserve deploy verification):**
- Add a separate workflow in `sar-srv` triggered by `repository_dispatch` type `deployment-status`
- This workflow creates the GitHub Release only after deploy succeeds
- Pro: Release only after confirmed deploy. Con: More complex, two workflows.

**Recommendation: Option A** for simplicity. The GitHub Release already has a "Quality Metrics" section — just update it to say deployment was triggered (not confirmed), and add a link to the infra Actions run. If deploy fails, you'll see it in sar-infra Actions and can re-trigger manually.

Updated finalize `needs` and `if`:
```yaml
  finalize:
    name: Finalize Release
    needs: [test, build-and-push, trigger-deploy]
    # ...
    if: |
      github.ref == 'refs/heads/main' &&
      always() &&
      (github.event_name == 'push' || inputs.include_finalize != false) &&
      needs.test.result == 'success' &&
      (needs.build-and-push.result == 'success' || needs.build-and-push.result == 'skipped') &&
      (needs.trigger-deploy.result == 'success' || needs.trigger-deploy.result == 'skipped')
```

Update GitHub Release body to reference infra repo for deploy status.

#### 3.4 Update workflow_dispatch inputs

Remove `include_deploy` input (deployment is now in the other repo). Optionally add `skip_deploy_trigger` to allow building without triggering infra.

#### 3.5 Update paths-ignore

Remove `terraform/**` from `deploy.yml` paths-ignore (terraform will no longer be in this repo).

**Files to modify:** `.github/workflows/deploy.yml`
**Dependencies:** `CROSS_REPO_PAT` secret configured in sar-srv
**Expected outcome:** sar-srv deploy workflow ends after image push + dispatch, no terraform steps

---

### 4. Configure Secrets and Permissions

**Objective:** Set up cross-repo authentication and GCP access.

#### 4.1 Cross-repo PAT or GitHub App

Two approaches:

**Fine-grained PAT** (simpler):
- Create a fine-grained PAT with `repo` scope on both `sar-srv` and `sar-infra`
- Add as `CROSS_REPO_PAT` secret in both repos
- Used for `peter-evans/repository-dispatch`

**GitHub App** (more secure, recommended):
- Reuse existing `sar-ci-bot` GitHub App
- Install on `sar-infra` repo
- App can dispatch events and bypass branch protection in both repos

#### 4.2 GCP secrets in sar-infra

Add these secrets to `sar-infra` repository:
- `GCP_SA_KEY` — same service account key (copy from sar-srv)

Eventually: migrate both repos to **Workload Identity Federation** (eliminates service account keys entirely). The existing TODO in deploy.yml already notes this.

#### 4.3 GitHub App installation

If using the existing `sar-ci-bot` GitHub App:
- Go to App settings → Install → Add `sar-infra` repository
- Add `APP_ID` and `APP_PRIVATE_KEY` as secrets in `sar-infra`

**Files to modify:** GitHub repo settings (web UI)
**Dependencies:** Org/repo admin access
**Expected outcome:** Both repos can dispatch events to each other

---

### 5. Clean Up `sar-srv`

**Objective:** Remove all terraform artifacts from the app repo.

#### 5.1 Remove terraform directory

```bash
git rm -r terraform/
```

#### 5.2 Update .gitignore

Remove these lines:
```
.terraform/
*.tfstate
*.tfstate.*
*.tfvars
```

#### 5.3 Update CLAUDE.md

- Remove/shorten the "Infrastructure (Google Cloud Platform)" section
- Add a pointer: "Infrastructure managed in `sar-infra` repo"
- Remove terraform file references from "Critical Files Reference"
- Keep the environment variable documentation (still relevant to the app)

#### 5.4 Update documentation

- `docs/CI_CD_WORKFLOW.md` — update deployment flow description
- `docs/deployment/strategy.md` — reference sar-infra for deploy steps
- Any references to `terraform/` paths in other docs

**Files to modify:** `.gitignore`, `CLAUDE.md`, docs
**Dependencies:** Terraform fully operational in sar-infra
**Expected outcome:** Clean app repo with no infrastructure code

---

### 6. Validation and Cutover

**Objective:** Verify everything works end-to-end before going live.

#### Cutover sequence (minimize downtime risk):

1. **Set up sar-infra** (Tasks 1-2) — deploy workflow ready but not triggered yet
2. **Dry-run**: Manual `workflow_dispatch` in sar-infra with current production image tag
   ```bash
   gh workflow run deploy.yml -R khisamutdinov/sar-infra \
     -f image_tag=0.14.0 \
     -f commit_sha=manual-test
   ```
   - Should show `tofu plan` with no changes (same image already deployed)
   - Health check should pass

3. **Modify sar-srv** (Task 3) — update deploy workflow on a feature branch
4. **Test PR**: Merge the sar-srv changes to main
   - Phases 0-2 run as before
   - `trigger-deploy` dispatches to sar-infra
   - sar-infra deploys (should be no-op if same version)
   - Phase 4 finalizes

5. **Verify**: Check both repos' Actions tabs for clean runs
6. **Clean up** (Task 5) — remove terraform from sar-srv on a follow-up PR

#### Rollback plan

If the new flow fails:
- Revert the sar-srv deploy workflow change (re-add Phase 3 with terraform)
- Terraform state is untouched (both repos point to same GCS backend)
- No state migration = instant rollback

**Expected outcome:** Full deploy pipeline works across two repos

---

## Architecture Decision Records

### ADR-1: Shared Terraform State

**Decision:** Both repos use the same GCS backend bucket and state path during migration.
**Rationale:** Avoids state migration complexity. After sar-srv terraform is removed, only sar-infra accesses the state.
**Risk:** During transition, concurrent applies from both repos could corrupt state. Mitigated by: cutover is fast (one PR), and concurrency groups prevent parallel deploys.

### ADR-2: Fire-and-forget dispatch (not callback-gated finalize)

**Decision:** Phase 4 (finalize) runs after dispatch is sent, not after deploy confirms.
**Rationale:** Cross-repo callback adds complexity (second workflow, state tracking). If deploy fails, it's visible in sar-infra Actions. Git tag/release represent "image built and deploy requested", not "running in production."
**Alternative considered:** Callback-based finalize. Revisit if deploy failures become common.

### ADR-3: GitHub App over PAT for cross-repo dispatch

**Decision:** Prefer GitHub App (sar-ci-bot) for authentication.
**Rationale:** Apps have scoped permissions, don't expire like PATs, and are already set up for branch protection bypass in sar-srv.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Dispatch event lost/delayed | Low | Medium | Manual re-trigger via `workflow_dispatch` |
| GCS state lock contention | Very Low | High | Short cutover window, concurrency groups |
| PAT/App token misconfigured | Medium | Low | Test with dry-run before cutover |
| Health check fails in new workflow | Low | Low | Same logic, just different repo |
| Developer confusion (two repos) | Medium | Low | Clear README and CLAUDE.md pointers |

---

## Estimated Effort

| Task | Effort |
|------|--------|
| 1. Create sar-infra repo | Small (file moves) |
| 2. Deploy workflow in sar-infra | Medium (new workflow) |
| 3. Modify sar-srv workflow | Small (remove + add dispatch) |
| 4. Secrets/permissions | Small (web UI config) |
| 5. Clean up sar-srv | Small (delete + docs) |
| 6. Validation | Medium (end-to-end testing) |
