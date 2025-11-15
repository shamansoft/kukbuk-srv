# Making Workflow Production-Ready

This document describes changes needed to lock down the deployment workflow for production use.

## Current State (Testing Mode)

The workflow is currently configured for **testing flexibility**:
- ✅ Can be triggered manually on **any branch**
- ✅ Has options to skip deployment
- ✅ Has options to skip finalization
- ⚠️ Branch-specific concurrency (allows parallel testing)

## Production-Ready Configuration

For production, the workflow should:
- ✅ Run automatically **only on push to main**
- ✅ Allow manual trigger **only from main**
- ✅ Remove "skip" options (always deploy)
- ✅ Use main-specific concurrency (serialize all deployments)

---

## Changes Required

### 1. Restrict Workflow Triggers

**File**: `.github/workflows/deploy.yml`

**Current** (lines 3-21):
```yaml
on:
  push:
    branches: [ main ]
    paths-ignore:
      - '**.md'
      - 'docs/**'
      - '.github/deployment-setup.md'
  workflow_dispatch:  # Allow manual trigger on any branch
    inputs:
      skip_deploy:
        description: 'Skip deployment (build and push only)'
        required: false
        type: boolean
        default: false
      skip_finalize:
        description: 'Skip version finalization (no tag, no version bump)'
        required: false
        type: boolean
        default: false
```

**Change to**:
```yaml
on:
  push:
    branches: [ main ]
    paths-ignore:
      - '**.md'
      - 'docs/**'
      - '.github/deployment-setup.md'
  workflow_dispatch:  # Allow manual trigger from main only
```

**What this does**:
- ✅ Automatic trigger: only on push to `main`
- ✅ Manual trigger: available, but GitHub will only show `main` branch in dropdown
- ❌ Removes skip options (always runs full pipeline)

---

### 2. Update Concurrency Group

**File**: `.github/workflows/deploy.yml`

**Current** (lines 23-26):
```yaml
# Ensure only one deployment runs at a time per branch
concurrency:
  group: deployment-${{ github.ref }}
  cancel-in-progress: false  # Don't cancel in-progress deployments
```

**Change to**:
```yaml
# Ensure only one deployment runs at a time
concurrency:
  group: production-deployment
  cancel-in-progress: false  # Don't cancel in-progress deployments
```

**What this does**:
- ✅ Serializes ALL deployments (no parallel runs)
- ✅ Simpler concurrency model
- ✅ Clear that this is for production

---

### 3. Remove Conditional Job Execution

**File**: `.github/workflows/deploy.yml`

**Current** (line 231):
```yaml
  deploy:
    name: Deploy to Cloud Run
    needs: [test, build-and-push]
    runs-on: ubuntu-latest
    environment: production  # Require manual approval if configured
    # Skip deployment if requested via workflow_dispatch
    if: ${{ !inputs.skip_deploy }}
```

**Change to**:
```yaml
  deploy:
    name: Deploy to Cloud Run
    needs: [test, build-and-push]
    runs-on: ubuntu-latest
    environment: production  # Require manual approval if configured
```

**What this does**:
- ✅ Deploy phase always runs (no skipping)
- ❌ Removes conditional execution

---

**Current** (line 326):
```yaml
  finalize:
    name: Finalize Release
    needs: [test, build-and-push, deploy]
    runs-on: ubuntu-latest
    # Skip finalization if requested via workflow_dispatch OR if deploy was skipped
    if: ${{ !inputs.skip_finalize && !inputs.skip_deploy }}
```

**Change to**:
```yaml
  finalize:
    name: Finalize Release
    needs: [test, build-and-push, deploy]
    runs-on: ubuntu-latest
```

**What this does**:
- ✅ Finalize phase always runs (no skipping)
- ❌ Removes conditional execution

---

## Summary of Changes

| Item | Location | Action |
|------|----------|--------|
| Remove skip_deploy input | Lines 12-16 | **DELETE** |
| Remove skip_finalize input | Lines 17-21 | **DELETE** |
| Update concurrency group | Lines 23-26 | **CHANGE** `deployment-${{ github.ref }}` to `production-deployment` |
| Remove deploy condition | Line 231 | **DELETE** `if: ${{ !inputs.skip_deploy }}` |
| Remove finalize condition | Line 326 | **DELETE** `if: ${{ !inputs.skip_finalize && !inputs.skip_deploy }}` |

---

## Quick Reference: Exact Lines to Modify

### Delete these lines:
```
Lines 11-21:  workflow_dispatch inputs (skip_deploy and skip_finalize)
Line 231:     if: ${{ !inputs.skip_deploy }}
Line 326:     if: ${{ !inputs.skip_finalize && !inputs.skip_deploy }}
```

### Change this line:
```
Line 25: group: deployment-${{ github.ref }}
    TO:  group: production-deployment
```

---

## Testing Before Going to Production

Before making these changes:

1. **Test the current workflow** on `ci` branch with skip options
   ```bash
   # Via GitHub Actions UI:
   # - Select ci branch
   # - Check "Skip deployment"
   # - Verify build works
   ```

2. **Test a full deployment** on `ci` branch
   ```bash
   # Via GitHub Actions UI:
   # - Select ci branch
   # - Leave both unchecked
   # - Verify full pipeline works
   ```

3. **Verify rollback works**
   ```bash
   gcloud run revisions list --service=cookbook --region=us-west1
   # Test rolling back to previous version
   ```

4. **After validation**, make the production changes above

---

## After Going to Production

### Manual Trigger Process

1. Go to GitHub **Actions** tab
2. Select **Build and Deploy to Production**
3. Click **Run workflow**
4. **Only `main` branch** will be available in dropdown
5. Click **Run workflow**

### Automatic Trigger Process

1. Merge PR to `main`
2. Workflow starts automatically
3. Monitor in Actions tab

### What You Can No Longer Do

- ❌ Run workflow on feature branches
- ❌ Skip deployment phase
- ❌ Skip version finalization

### What You Should Do Instead

**To test changes without deploying:**
- Use the **PR Validation** workflow (runs automatically on PRs)
- Build locally: `cd extractor/scripts && ./build.sh <version> --native --local`

**To test full build without deployment:**
- Create a separate staging workflow (copy and modify deploy.yml)
- Or use manual local testing

---

## Optional: Create Staging Workflow

If you want to keep the flexibility for testing, create a separate staging workflow:

```bash
# Create staging workflow
cp .github/workflows/deploy.yml .github/workflows/deploy-staging.yml
```

Then modify `deploy-staging.yml`:
- Change service name to `cookbook-staging`
- Keep the skip options
- Allow any branch
- Deploy to staging environment

This gives you:
- **deploy.yml**: Production-only, strict, main branch
- **deploy-staging.yml**: Flexible, any branch, testing

---

## Rollback Plan if Issues

If you make these changes and encounter problems:

```bash
# Revert the workflow file
git checkout HEAD~1 .github/workflows/deploy.yml
git commit -m "revert: restore flexible workflow configuration [skip ci]"
git push
```

---

## Final Checklist

Before making production-ready:
- [ ] Test current workflow on feature branch (with skip_deploy)
- [ ] Test current workflow on feature branch (full deployment)
- [ ] Verify rollback procedures work
- [ ] Document rollback plan for team
- [ ] Make the 5 changes listed above
- [ ] Test manual trigger from main
- [ ] Test automatic trigger via PR merge
- [ ] Monitor first production deployment closely

After making production-ready:
- [ ] Update `docs/CI_CD_WORKFLOW.md` to remove skip_deploy references
- [ ] Update `docs/TESTING_WORKFLOW.md` to note it only applies to staging
- [ ] Communicate changes to team
- [ ] Archive this file or keep for reference

---

## Questions?

**Q: Can I still test builds without deploying after these changes?**

A: Yes, but only via:
- PR Validation workflow (automatic on PRs)
- Local builds using `./build.sh --local`
- A separate staging workflow (if created)

**Q: What if I need to deploy an old version?**

A: Use Terraform manually:
```bash
cd terraform
tofu apply -var="image_tag=0.5.5"
```

**Q: Can I partially revert (keep some skip options)?**

A: Yes, you can keep `skip_deploy` if needed for testing. Just remove `skip_finalize` to ensure version management always runs.

**Q: Will this prevent emergency deployments?**

A: No, you can still:
- Use `workflow_dispatch` to manually trigger from main
- Deploy via Terraform manually
- Use `gcloud run deploy` directly

---

## Additional Security (Optional)

### Require PR Reviews

In GitHub Settings → Branches:
```
Branch protection rule: main
- Require pull request before merging
- Require 1 approval
- Require status checks: PR Validation
```

### Environment Protection

In GitHub Settings → Environments → production:
```
Required reviewers: [select team members]
Wait timer: 0 minutes
Deployment branches: main only
```

This adds manual approval before deployment even on automatic triggers.

---

## Version

- **Created**: 2025-11-08
- **Applies to**: Workflow version with skip options (testing mode)
- **Target**: Production-ready deployment workflow
