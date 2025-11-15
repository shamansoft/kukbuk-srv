# Deployment Strategy

## Overview

This document outlines the CI/CD deployment strategy for the cookbook service to Google Cloud Run using GitHub Actions.

### Goals
- ✅ Automated testing before every deployment
- ✅ Secure authentication using Workload Identity Federation
- ✅ Version management with git tags
- ✅ Zero-downtime deployments
- ✅ Easy rollback capabilities
- ✅ Full audit trail

### Principles
- **Test First**: Never deploy untested code
- **Build Once**: Build artifacts are immutable
- **Deploy Validated**: Only deploy verified builds
- **Tag After Success**: Git tags represent production state
- **Fail Fast**: Stop pipeline on first error

## Current State Analysis

### Existing Workflow (`.github/workflows/deploy.yml`)

**✅ What Works Well:**
- Triggers on main branch only
- Uses native GraalVM build
- Auto-increments version
- Deploys with OpenTofu
- Creates GitHub releases
- Health check verification

**❌ Issues to Address:**
1. **No Testing**: Deploys without running tests
2. **Security**: Uses long-lived service account keys
3. **Tag Timing**: Creates git tag BEFORE deployment verification
4. **Version Management**: Doesn't commit version back to repo
5. **Combined Build/Push**: Makes troubleshooting harder
6. **Deprecated Actions**: Uses deprecated `create-release@v1`

### Current Scripts

#### `extractor/scripts/build.sh`
- Supports `--native` flag for GraalVM builds
- Supports `--push` flag to push to GCR
- Auto-increments version via `version-updater.sh`
- Updates `build.gradle.kts` with new version
- Memory configurable via `--memory=12g`

#### `extractor/scripts/push.sh`
- Validates image exists locally
- Pushes to `gcr.io/kukbuk-tf/cookbook:<tag>`
- Supports dry-run mode

#### `extractor/scripts/version-updater.sh`
- Reads version from `build.gradle.kts`
- Increments patch version (0.5.4 → 0.5.5)
- Updates `build.gradle.kts` in-place

## Deployment Workflow

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│ 1. CODE VALIDATION (5-7 min)                                │
│    ├── Unit Tests                                            │
│    ├── Integration Tests                                     │
│    ├── Code Coverage Check (40% minimum)                     │
│    └── Security Scan (OWASP dependency check)                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. BUILD & PUSH (10-15 min)                                 │
│    ├── Authenticate to GCP (Workload Identity)              │
│    ├── Increment version in build.gradle.kts                    │
│    ├── Build native Docker image (--native)                 │
│    ├── Push image to GCR                                    │
│    └── Verify image in registry                             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. DEPLOY (2-3 min)                                         │
│    ├── Deploy to Cloud Run (OpenTofu)                       │
│    ├── Wait for rollout completion                          │
│    └── Run smoke tests (health endpoint)                    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. FINALIZE (1 min)                                         │
│    ├── Commit version bump to repo                          │
│    ├── Create git tag v<version>                            │
│    ├── Create GitHub release                                │
│    └── Send notifications (optional)                        │
└─────────────────────────────────────────────────────────────┘
```

### Phase 1: Code Validation (5-7 minutes)

**Purpose**: Ensure code quality before building expensive native image

```yaml
- name: Run unit tests
  run: cd extractor && ./gradlew test

- name: Run integration tests
  run: cd extractor && ./gradlew intTest

- name: Check code coverage
  run: cd extractor && ./gradlew checkCoverage
  # Enforces 40% minimum coverage

- name: Security vulnerability scan
  run: cd extractor && ./gradlew dependencyCheck
  # Fails on CVSS >= 7.0
```

**Why This Matters**:
- Native builds take 10-15 minutes and cost compute time
- Catching failures early saves time and money
- Prevents deploying broken code to production

### Phase 2: Build & Push (10-15 minutes)

**Purpose**: Create immutable deployment artifact

**Step 2.1: Authenticate to GCP**
```yaml
- name: Authenticate to Google Cloud
  uses: google-github-actions/auth@v2
  with:
    workload_identity_provider: 'projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider'
    service_account: 'github-actions-deploy@kukbuk-tf.iam.gserviceaccount.com'
```

**Step 2.2: Increment Version**
```yaml
- name: Increment version
  id: version
  run: |
    cd extractor/scripts
    source ./version-updater.sh
    NEW_VERSION=$(update_version | tail -n 1)
    echo "VERSION=$NEW_VERSION" >> $GITHUB_OUTPUT
    echo "Building version: $NEW_VERSION"
```

**Why Separate**: Version increment is separate from build so we know the exact version before building

**Step 2.3: Build Native Image**
```yaml
- name: Build native Docker image
  run: |
    cd extractor/scripts
    ./build.sh ${{ steps.version.outputs.VERSION }} --native --memory=12g
    # Note: NO --push flag
```

**Why Separate from Push**:
- Better visibility - can see exactly where failures occur
- Can inspect local image before pushing
- Allows for local testing if needed
- Clear separation of concerns

**Step 2.4: Push to GCR**
```yaml
- name: Push image to GCR
  run: |
    cd extractor/scripts
    ./push.sh ${{ steps.version.outputs.VERSION }}
```

**Step 2.5: Verify Image**
```yaml
- name: Verify image in GCR
  run: |
    gcloud container images describe \
      gcr.io/kukbuk-tf/cookbook:${{ steps.version.outputs.VERSION }}
```

**Why Verify**: Ensures image is actually in registry before proceeding to deploy

### Phase 3: Deploy (2-3 minutes)

**Purpose**: Deploy verified artifact to production

**Step 3.1: Deploy with OpenTofu**
```yaml
- name: Terraform Init
  working-directory: terraform
  run: tofu init

- name: Terraform Plan
  working-directory: terraform
  run: |
    tofu plan \
      -var="image_tag=${{ steps.version.outputs.VERSION }}" \
      -var="project_id=kukbuk-tf" \
      -out=tfplan

- name: Terraform Apply
  working-directory: terraform
  run: tofu apply -auto-approve tfplan
```

**Step 3.2: Get Deployment URL**
```yaml
- name: Get deployment URL
  id: deploy
  working-directory: terraform
  run: |
    CLOUD_RUN_URL=$(tofu output -raw cloud_run_url)
    echo "URL=$CLOUD_RUN_URL" >> $GITHUB_OUTPUT
```

**Step 3.3: Smoke Test**
```yaml
- name: Test deployment
  run: |
    echo "Testing health endpoint..."
    # Retry logic for startup time
    for i in {1..30}; do
      if curl -f ${{ steps.deploy.outputs.URL }}/actuator/health; then
        echo "Health check passed!"
        exit 0
      fi
      echo "Attempt $i/30 failed, retrying in 10s..."
      sleep 10
    done
    echo "Health check failed after 30 attempts"
    exit 1
```

**Why Retry Logic**: Native images can take 30-60 seconds to start up

### Phase 4: Finalize (1 minute)

**Purpose**: Record successful deployment in git and GitHub

**Step 4.1: Commit Version Bump**
```yaml
- name: Commit version change
  run: |
    git config user.name "github-actions[bot]"
    git config user.email "github-actions[bot]@users.noreply.github.com"
    git add extractor/build.gradle.kts
    git commit -m "chore: bump version to ${{ steps.version.outputs.VERSION }} [skip ci]"
    git push origin main
```

**Important Notes**:
- Uses `[skip ci]` to prevent infinite loop
- Only commits AFTER successful deployment
- Uses bot identity for clear audit trail

**Step 4.2: Create Git Tag**
```yaml
- name: Create Git tag
  run: |
    git tag -a "v${{ steps.version.outputs.VERSION }}" \
      -m "Release version ${{ steps.version.outputs.VERSION }}"
    git push origin "v${{ steps.version.outputs.VERSION }}"
```

**Why After Deploy**: Tags should only represent versions actually deployed to production

**Step 4.3: Create GitHub Release**
```yaml
- name: Create GitHub Release
  uses: ncipollo/release-action@v1
  with:
    tag: v${{ steps.version.outputs.VERSION }}
    name: Release v${{ steps.version.outputs.VERSION }}
    body: |
      ## Automated Release v${{ steps.version.outputs.VERSION }}

      ### Deployed to
      - **Environment**: Production (GCP Cloud Run)
      - **Service URL**: ${{ steps.deploy.outputs.URL }}
      - **Region**: us-west1
      - **Image**: gcr.io/kukbuk-tf/cookbook:${{ steps.version.outputs.VERSION }}

      ### Build Type
      - Native image (GraalVM)

      ### Changes
      See commit history for details.
    draft: false
    prerelease: false
```

## Version Management

### Why Commit Versions Back

**Advantages**:
- ✅ Version visible in git history
- ✅ `build.gradle.kts` always shows current production version
- ✅ Local builds use correct version
- ✅ Easy to see what version is deployed

**Disadvantages**:
- ⚠️ Adds commit to main after PR merge
- ⚠️ Requires careful `[skip ci]` usage to prevent loops
- ⚠️ Need to handle race conditions

### Implementation Details

**Commit Message Format**:
```
chore: bump version to 0.5.5 [skip ci]
```

**Why `[skip ci]`**: Prevents the version commit from triggering another deployment

**Race Condition Handling**:
```yaml
- name: Commit version change
  run: |
    # Pull latest to avoid conflicts
    git pull --rebase origin main

    # Only commit if build.gradle.kts changed
    if git diff --quiet extractor/build.gradle.kts; then
      echo "No version change detected, skipping commit"
      exit 0
    fi

    git config user.name "github-actions[bot]"
    git config user.email "github-actions[bot]@users.noreply.github.com"
    git add extractor/build.gradle.kts
    git commit -m "chore: bump version to ${{ steps.version.outputs.VERSION }} [skip ci]"

    # Retry push if race condition occurs
    for i in {1..5}; do
      if git push origin main; then
        echo "Push succeeded"
        exit 0
      fi
      echo "Push failed, retrying after rebase..."
      git pull --rebase origin main
      sleep 2
    done

    echo "Failed to push after 5 attempts"
    exit 1
```

## FAQ

### Q: Why not use Cloud Build instead of GitHub Actions?

**A**: GitHub Actions provides:
- Better integration with GitHub (PR checks, releases)
- More familiar to most developers
- Easier to test locally (act)
- Free for public repos
- Existing workflow already in place

### Q: Why GraalVM native image? It's so slow to build.

**A**: Native images provide:
- Faster startup (2-3 seconds vs 10-30 seconds)
- Lower memory usage (512MB vs 1GB+)
- Lower Cloud Run costs (billed per 100ms)
- Better cold start performance

For development, use JVM builds. For production, native is worth the build time.

### Q: What if the version commit fails?

The workflow will fail and deployment won't be tagged. This is safe because:
- Deployment already succeeded
- Service is running
- You can manually commit version and tag later

Retry logic handles most race conditions.

### Q: Can I deploy without merging to main?

Use manual workflow trigger with a feature branch:
1. Update workflow to allow workflow_dispatch on any branch
2. Trigger manually from Actions tab
3. Deploy to staging environment instead of production

### Q: How do I deploy to staging first?

Add a staging environment:
1. Create separate Cloud Run service (cookbook-staging)
2. Create `.github/workflows/deploy-staging.yml`
3. Trigger on `develop` branch
4. Use same workflow but different service name

## Related Documents

- [Rollback Procedures](./rollback.md)
- [Monitoring & Alerts](./monitoring.md)
- [Production Readiness](./production-readiness.md)
- [GitHub Setup](../github-setup/deployment-setup.md)
- [Infrastructure Documentation](../../terraform/README.md)
