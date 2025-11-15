# Testing CI/CD Workflow on Feature Branches

## Quick Start

You can now test the entire CI/CD pipeline on any branch without merging to main or affecting production.

## Testing Options

### Option 1: Full Test (Build + Deploy)

**Use case**: Test the complete pipeline including deployment

```bash
# Push your changes to feature branch
git checkout -b test/ci-workflow
git push origin test/ci-workflow
```

Then in GitHub:
1. Go to **Actions** tab
2. Click **Build and Deploy to Production** workflow
3. Click **Run workflow** dropdown
4. Select your branch: `test/ci-workflow`
5. Leave both checkboxes **unchecked**
6. Click **Run workflow**

**What happens**:
- ✅ Phase 0-1: Version prep + tests
- ✅ Phase 2: Build native image and push to GCR
- ✅ Phase 3: Deploy to Cloud Run (OVERWRITES PRODUCTION!)
- ✅ Phase 4: Create tag + version bump + commit

**Duration**: ~20-25 minutes

---

### Option 2: Build & Push Only (Recommended for Testing)

**Use case**: Test build process without deploying to production

```bash
git checkout -b test/build-only
git push origin test/build-only
```

Then in GitHub:
1. Go to **Actions** tab
2. Click **Build and Deploy to Production** workflow
3. Click **Run workflow** dropdown
4. Select your branch: `test/build-only`
5. ✅ Check **"Skip deployment"**
6. Click **Run workflow**

**What happens**:
- ✅ Phase 0-1: Version prep + tests
- ✅ Phase 2: Build native image and push to GCR
- ❌ Phase 3: **Skipped** (no deployment)
- ❌ Phase 4: **Skipped** (no tag/version bump)

**Duration**: ~15-20 minutes

**Result**:
- Docker image built and pushed to GCR
- Production unchanged
- No git tags created
- No version commit

---

### Option 3: Build Only (No Push to GCR)

**Use case**: Test build process without pushing to registry

Unfortunately, the current workflow doesn't support this. To test builds locally:

```bash
cd extractor/scripts
./build.sh 0.6.0-test --native --memory=12g --local
```

This builds the image locally without pushing.

---

## Testing Scenarios

### Scenario 1: Test Version Management

**Goal**: Verify version-updater.sh works correctly

```bash
cd extractor/scripts

# Test extraction
./version-updater.sh extract
# Expected: 0.6.0-SNAPSHOT

# Test release preparation
./version-updater.sh prepare-release
# Expected: 0.6.0

# Test next snapshot
./version-updater.sh next-snapshot
# Expected: 0.6.1-SNAPSHOT

# Reset to original
git checkout ../build.gradle.kts
```

### Scenario 2: Test PR Validation

**Goal**: Verify PR checks work

1. Create test branch and make a small change
2. Create PR to `main`
3. Watch PR validation workflow run
4. Verify coverage comment appears
5. Close PR without merging

### Scenario 3: Test Full Deployment (Careful!)

**Goal**: Test complete pipeline

⚠️ **WARNING**: This WILL deploy to production!

1. Create test branch
2. Trigger workflow with all options unchecked
3. Monitor all 4 phases
4. Verify deployment at Cloud Run URL
5. Check git tags were created
6. Verify version was bumped

**To rollback after test**:
```bash
# Roll back Cloud Run to previous version
gcloud run services update-traffic cookbook \
  --to-revisions=PREVIOUS_REVISION=100 \
  --region=us-west1 \
  --project=kukbuk-tf

# Delete test tag
git push origin --delete v0.6.0

# Reset version in main
git checkout main
git pull
# Edit build.gradle.kts to revert version
git commit -am "chore: revert test version [skip ci]"
git push
```

---

## Understanding Workflow Outputs

### Successful Build Output

```
✅ Phase 0-1: Test & Validate (5-7 min)
  - Version: 0.6.0-SNAPSHOT → 0.6.0
  - Tests: All passed
  - Coverage: 40%+
  - Security: No critical issues

✅ Phase 2: Build & Push (10-15 min)
  - Image: gcr.io/kukbuk-tf/cookbook:0.6.0
  - Size: ~50-100MB (native)
  - Pushed: ✅

⚠️ Phase 3: Skipped (skip_deploy = true)

⚠️ Phase 4: Skipped (skip_finalize = true)
```

### Failed Build Output

```
✅ Phase 0-1: Test & Validate
  - Version: 0.6.0-SNAPSHOT → 0.6.0
  - Tests: Unit tests passed
  - Tests: Integration tests FAILED ❌

❌ Workflow stopped (fail fast)
```

**What to do**:
1. Check test logs in Actions → Failed job
2. Download test artifacts
3. Fix tests locally
4. Push fix and retry

---

## Common Testing Tasks

### Test New Feature

```bash
# Create feature branch
git checkout -b feature/my-feature

# Make changes
# ... edit code ...

# Test locally
cd extractor
./gradlew test intTest checkCoverage

# Push and create PR
git push origin feature/my-feature
# Create PR - validation runs automatically

# Optional: Test full build on feature branch
# Use GitHub Actions → Run workflow → select branch → skip_deploy=true
```

### Test Version Bump Logic

```bash
# On feature branch
cd extractor/scripts

# Simulate release process
source ./version-updater.sh

echo "Current: $(extract_version)"
# 0.6.0-SNAPSHOT

RELEASE=$(prepare_release | tail -n 1)
echo "Release: $RELEASE"
# 0.6.0

NEXT=$(bump_to_next_snapshot | tail -n 1)
echo "Next: $NEXT"
# 0.6.1-SNAPSHOT

# Reset
git checkout ../build.gradle.kts
```

### Test Build Script Changes

```bash
# Modify build.sh
vim extractor/scripts/build.sh

# Test locally (JVM build - fast)
cd extractor/scripts
./build.sh 0.6.0-test --local

# Test locally (native build - slow)
./build.sh 0.6.0-test --native --local

# Test via GitHub Actions
# Push branch → Run workflow with skip_deploy=true
```

---

## Cleanup After Testing

### Remove Test Images from GCR

```bash
# List images
gcloud container images list-tags gcr.io/kukbuk-tf/cookbook

# Delete test image
gcloud container images delete gcr.io/kukbuk-tf/cookbook:0.6.0-test \
  --quiet
```

### Remove Test Git Tags

```bash
# Delete local tag
git tag -d v0.6.0-test

# Delete remote tag
git push origin --delete v0.6.0-test
```

### Reset Version After Failed Test

```bash
# If workflow created a bad version commit
git checkout main
git pull

# Find the bad commit
git log --oneline -5

# Revert it
git revert <commit-hash>
git push
```

---

## Monitoring During Tests

### Watch Workflow Progress

```bash
# Using GitHub CLI
gh run list --workflow=deploy.yml
gh run watch <run-id>

# Or in browser
# Go to Actions tab → Select workflow run
```

### Monitor Cloud Run Deployment

```bash
# Watch revisions
watch -n 5 gcloud run revisions list \
  --service=cookbook \
  --region=us-west1 \
  --project=kukbuk-tf

# Stream logs
gcloud run services logs tail cookbook \
  --region=us-west1 \
  --project=kukbuk-tf
```

### Check Docker Image

```bash
# Verify image exists
gcloud container images describe \
  gcr.io/kukbuk-tf/cookbook:0.6.0

# Check image size
gcloud container images list-tags \
  gcr.io/kukbuk-tf/cookbook
```

---

## Troubleshooting Tests

### "Image not found in GCR"

**Cause**: Build succeeded but push failed

**Solution**:
1. Check build logs for push step
2. Verify GCP credentials are valid
3. Manually push: `cd extractor/scripts && ./push.sh 0.6.0`

### "Health check failed"

**Cause**: Deployment succeeded but service not healthy

**Solution**:
1. Check Cloud Run logs
2. Verify health endpoint: `curl https://YOUR_URL/actuator/health`
3. Check for startup errors

### "Tests failed"

**Cause**: Code doesn't pass validation

**Solution**:
1. Download test artifacts from GitHub Actions
2. Run tests locally: `./gradlew test intTest`
3. Fix issues and push again

### "Concurrency limit"

**Cause**: Another deployment running on same branch

**Solution**:
1. Wait for other deployment to finish
2. Or cancel it from Actions tab
3. Then retry

---

## Best Practices for Testing

1. **Use skip_deploy=true** for most tests to avoid production impact
2. **Test version script** locally before running workflow
3. **Review logs** carefully in GitHub Actions
4. **Clean up** test images and tags after testing
5. **Document** any issues found during testing

---

## FAQ

**Q: Will testing on a feature branch affect production?**

A: Only if you run with `skip_deploy=false`. Always use `skip_deploy=true` for testing.

**Q: Can I test in parallel with production deployment?**

A: Yes! Branch-specific concurrency allows feature branch tests alongside main deployments.

**Q: How do I test without using GitHub Actions minutes?**

A: Run builds locally using the build scripts in `extractor/scripts/`.

**Q: What if I accidentally deploy to production from a feature branch?**

A: Follow the rollback procedures in `docs/CD.md`. Typically:
```bash
gcloud run services update-traffic cookbook \
  --to-revisions=PREVIOUS_REVISION=100 \
  --region=us-west1
```

**Q: Can I test against a staging environment?**

A: Not currently. You'd need to create a separate staging workflow and Cloud Run service.

---

## Next Steps

After successful testing:
1. Merge your changes to main
2. Automatic deployment will run
3. Monitor the production deployment
4. Verify via health checks

For more information:
- [CI/CD Workflow Guide](CI_CD_WORKFLOW.md)
- [CD Strategy](CD.md)
- [Build Documentation](../BUILD_PUSH_SEPARATION.md)
