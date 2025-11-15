# CI/CD Workflow Guide

## Overview

This project uses an automated CI/CD pipeline that implements Maven/Gradle -SNAPSHOT versioning best practices with zero-downtime deployments to Google Cloud Run.

## Version Management Strategy

### Snapshot Versioning

We use **-SNAPSHOT** suffix for development versions:

- **Development**: `0.5.5-SNAPSHOT` (version in main branch)
- **Release**: `0.5.5` (version deployed to production)
- **Next Development**: `0.5.6-SNAPSHOT` (after release)

### Why -SNAPSHOT?

This follows Maven/Gradle conventions:
- **Clear distinction** between released and unreleased code
- **Prevent confusion** about what's in production vs development
- **Enable semantic versioning** without manual intervention
- **Standard practice** in Java ecosystem

## Workflow Overview

```
┌──────────────┐
│ Developer    │
│ creates PR   │
└──────┬───────┘
       │
       ▼
┌────────────────────────────────┐
│ PR Validation Workflow         │
│ - Unit tests                   │
│ - Integration tests            │
│ - Code coverage (40% min)      │
│ - Security scan                │
│ - Build verification           │
└────────────────────────────────┘
       │
       ▼
┌──────────────┐
│ Code review  │
│ & approval   │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Merge to     │
│ main         │
└──────┬───────┘
       │
       ▼
┌────────────────────────────────────────────────┐
│ Deployment Workflow (4 Phases)                 │
│                                                 │
│ Phase 0: Version Preparation                   │
│   • Extract 0.5.5-SNAPSHOT                     │
│   • Remove -SNAPSHOT → 0.5.5                   │
│                                                 │
│ Phase 1: Code Validation                       │
│   • Run all tests                              │
│   • Verify coverage                            │
│   • Security scan                              │
│                                                 │
│ Phase 2: Build & Push                          │
│   • Build native Docker image                  │
│   • Tag as gcr.io/kukbuk-tf/cookbook:0.5.5    │
│   • Push to Google Container Registry          │
│                                                 │
│ Phase 3: Deploy                                │
│   • Deploy to Cloud Run via Terraform          │
│   • Health check verification                  │
│                                                 │
│ Phase 4: Finalize                              │
│   • Create git tag v0.5.5                      │
│   • Bump version to 0.5.6-SNAPSHOT             │
│   • Commit back to main [skip ci]              │
│   • Create GitHub Release                      │
└────────────────────────────────────────────────┘
```

## Workflows

### 1. PR Validation (`.github/workflows/pr-validation.yml`)

**Trigger**: Every pull request to `main` or `develop`

**Purpose**: Ensure code quality before merging

**Jobs**:
1. **Validate**: Runs all tests, coverage, and security scans
2. **Build Check**: Verifies JAR builds successfully

**Duration**: ~5-7 minutes

**Artifacts**:
- Test results (HTML reports)
- Coverage reports (JaCoCo)
- Security scan results (OWASP)

**Features**:
- Automatic PR comment with coverage report
- Concurrency control (cancels old runs when new commits pushed)
- Detailed summary in GitHub Actions

### 2. Build & Deploy (`.github/workflows/deploy.yml`)

**Trigger**: Merge to `main` branch

**Purpose**: Automated deployment to production

**Jobs**:
1. **Test**: Version prep + validation (~5-7 min)
2. **Build-and-Push**: Native image build (~10-15 min)
3. **Deploy**: Terraform deployment (~2-3 min)
4. **Finalize**: Git tagging + version bump (~1 min)

**Total Duration**: ~20-25 minutes

**Features**:
- Concurrency control (serializes deployments)
- Separate build and push steps (better visibility)
- Health check with retry logic (30 attempts)
- Automatic version management
- GitHub Release creation

## Version Updater Script

Location: `extractor/scripts/version-updater.sh`

### Commands

```bash
# Extract current version
./version-updater.sh extract
# Output: 0.5.5-SNAPSHOT

# Prepare release (remove -SNAPSHOT)
./version-updater.sh prepare-release
# Output: 0.5.5

# Bump to next snapshot
./version-updater.sh next-snapshot
# Output: 0.5.6-SNAPSHOT

# Legacy: increment patch
./version-updater.sh update
# Output: 0.5.6
```

### Usage in Workflows

```yaml
# Prepare release version
- name: Prepare release version
  working-directory: extractor/scripts
  run: |
    source ./version-updater.sh
    RELEASE=$(prepare_release | tail -n 1)
    echo "RELEASE_VERSION=$RELEASE" >> $GITHUB_OUTPUT

# Bump to next snapshot
- name: Bump to next snapshot
  working-directory: extractor/scripts
  run: |
    source ./version-updater.sh
    NEXT=$(bump_to_next_snapshot | tail -n 1)
    echo "NEXT_SNAPSHOT=$NEXT" >> $GITHUB_ENV
```

## Development Workflow

### Making Changes

1. **Create feature branch**
   ```bash
   git checkout -b feature/my-feature
   ```

2. **Make changes and commit**
   ```bash
   git add .
   git commit -m "feat: add new feature"
   git push origin feature/my-feature
   ```

3. **Create Pull Request**
   - PR validation automatically runs
   - Wait for all checks to pass
   - Address any failing tests or coverage issues

4. **Get approval and merge**
   - Request code review
   - Once approved, merge to `main`
   - Deployment starts automatically

5. **Monitor deployment**
   - Go to Actions tab in GitHub
   - Watch the deployment progress
   - Verify health checks pass

### Version Lifecycle Example

**Initial state** (in `main` branch):
```
build.gradle: version = '0.5.5-SNAPSHOT'
```

**On merge to main**, workflow:
1. Prepares release: `0.5.5`
2. Builds and deploys with version `0.5.5`
3. Creates git tag `v0.5.5`
4. Bumps to `0.5.6-SNAPSHOT`
5. Commits back to main

**Final state**:
```
build.gradle: version = '0.5.6-SNAPSHOT'
Git tags: v0.5.5
Production: running 0.5.5
```

## Manual Operations

### Manual Deployment

If you need to manually trigger a deployment:

1. Go to Actions tab
2. Select "Build and Deploy to Production"
3. Click "Run workflow"
4. Select `main` branch
5. Click "Run workflow"

### Skipping Deployment

To merge to main WITHOUT deploying:

1. Include `[skip ci]` in your commit message
   ```bash
   git commit -m "docs: update README [skip ci]"
   ```

2. Or update workflow to ignore specific paths (already configured for `**.md` files)

### Rollback

See [docs/CD.md](CD.md#rollback-procedures) for detailed rollback procedures.

**Quick rollback** (emergency):
```bash
# List recent revisions
gcloud run revisions list \
  --service=cookbook \
  --region=us-west1 \
  --project=kukbuk-tf

# Roll back to specific revision
gcloud run services update-traffic cookbook \
  --to-revisions=cookbook-v0-5-4=100 \
  --region=us-west1 \
  --project=kukbuk-tf
```

## Environment Variables

### Required Secrets

Configure in GitHub Settings → Secrets:

- `GCP_SA_KEY`: Service account key JSON (base64 encoded)
- `GITHUB_TOKEN`: Automatically provided by GitHub

### Optional Secrets

- `CODECOV_TOKEN`: For coverage reporting to Codecov

## Troubleshooting

### Tests Fail in PR

1. Run tests locally:
   ```bash
   cd extractor
   ./gradlew test intTest
   ```

2. Check test reports in GitHub Actions artifacts

3. Fix failing tests and push new commit

### Coverage Below 40%

1. Check coverage report in PR comment
2. Add tests for uncovered code
3. Or adjust threshold in `build.gradle` (not recommended)

### Security Scan Fails

1. Check security report in artifacts
2. Update vulnerable dependencies:
   ```bash
   cd extractor
   ./gradlew dependencyUpdates
   ```

3. Suppress false positives in `dependency-check-suppressions.xml`

### Build Timeout

Native builds can take 15+ minutes. If timeout:

1. Check if using `ubuntu-latest-8-cores` runner
2. Increase `--memory` flag in `build.sh`
3. Consider using JVM build for testing (faster)

### Deployment Fails

1. Check Terraform logs in GitHub Actions
2. Verify GCP credentials are valid
3. Check Cloud Run service logs:
   ```bash
   gcloud run services logs read cookbook \
     --region=us-west1 \
     --project=kukbuk-tf
   ```

### Health Check Fails

1. Check if service is actually unhealthy
2. Increase retry attempts (currently 30)
3. Check startup time - native images can take 30-60s

## Best Practices

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add new feature
fix: resolve bug in parser
docs: update API documentation
chore: bump dependencies
test: add integration tests
refactor: simplify extraction logic
```

### PR Size

- Keep PRs focused and small (<500 lines)
- One feature/fix per PR
- Break large features into multiple PRs

### Testing

- Write tests for all new features
- Maintain 40%+ code coverage
- Include both unit and integration tests

### Version Management

- **Never** manually edit version in `build.gradle` after merge
- Always use -SNAPSHOT for development
- Let CI/CD handle version bumps

## Migration from Old Workflow

If you have an existing workflow:

1. **Update build.gradle** to use -SNAPSHOT version
   ```gradle
   version = '0.5.5-SNAPSHOT'
   ```

2. **Test version-updater.sh** locally
   ```bash
   cd extractor/scripts
   ./version-updater.sh help
   ./version-updater.sh extract
   ```

3. **Test PR validation** by creating a test PR

4. **Monitor first deployment** closely after merge

## Advanced Configuration

### Using Workload Identity Federation

For better security, migrate from service account keys to Workload Identity Federation.

See [docs/CD.md#workload-identity-federation](CD.md#workload-identity-federation) for setup.

### Environment Protection

Add manual approval for production deployments:

1. Go to Settings → Environments
2. Create "production" environment
3. Add required reviewers
4. Workflow already configured to use this environment

### Canary Deployments

To enable gradual rollout:

1. Update Terraform to use traffic splitting
2. Modify deployment workflow to deploy with 10% traffic
3. Add monitoring and automatic rollback

## Resources

- [CD Strategy](CD.md) - Comprehensive deployment strategy
- [Build Documentation](../BUILD_PUSH_SEPARATION.md) - Build script details
- [Terraform README](../terraform/README.md) - Infrastructure docs
- [GitHub Actions Docs](https://docs.github.com/en/actions)

## Support

For issues or questions:
- Create an issue in GitHub
- Check GitHub Actions logs
- Review workflow files for inline comments
