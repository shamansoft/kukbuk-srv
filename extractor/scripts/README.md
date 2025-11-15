# Legacy Deployment Scripts

⚠️ **Note**: These scripts are for manual/local deployment only. For production deployments, use the automated CI/CD pipeline (see main [README.md](../../README.md)).

## Overview

This directory contains shell scripts for manual building, deploying, and managing Docker containers for the Cookbook application. These scripts were used before CI/CD automation was implemented.

## Prerequisites

- Docker installed and running
- Google Cloud SDK (`gcloud`) installed and authenticated
- GCP project configured with Cloud Run enabled
- For native builds: 12GB+ RAM available to Docker

## Scripts

### build.sh

Builds Docker images for the Cookbook application.

**Usage**:
```bash
# Build JVM image locally (no push)
./build.sh --local

# Build JVM image and push to GCR
./build.sh v1.0.0 --push

# Build native image locally (requires more memory)
./build.sh --native --local --memory=12g

# Build native image and push to GCR
./build.sh --native --memory=12g --push

# Build with custom tag
./build.sh my-test-tag --local
```

**Options**:
- `--local`: Build locally without pushing to GCR (default behavior)
- `--push`: Push image to Google Container Registry after build
- `--native`: Build GraalVM native image (slower, smaller, faster startup)
- `--memory=XGg`: Set Docker memory limit (native builds need 12GB+)
- First argument: Version tag (e.g., `v1.0.0`). If starts with number, prefixed with 'v'

**Examples**:
```bash
# Quick local test
./build.sh --local

# Build specific version for GCR
./build.sh v0.6.5 --push

# Build native for testing locally
./build.sh test-native --native --local --memory=12g
```

**Output**:
- JVM image: `gcr.io/kukbuk-tf/cookbook:<tag>`
- Native image: `gcr.io/kukbuk-tf/cookbook:<tag>` (same tag, different content)

### push.sh

Pushes an existing Docker image to Google Container Registry.

**Usage**:
```bash
# Push specific version
./push.sh v1.0.0

# Push latest
./push.sh latest
```

**Prerequisites**:
- Image must already be built locally
- Must be authenticated to GCR via `gcloud auth configure-docker`

**Example Workflow**:
```bash
# Build locally
./build.sh v1.0.0 --local

# Test the image
docker run -p 8080:8080 gcr.io/kukbuk-tf/cookbook:v1.0.0

# Push after testing
./push.sh v1.0.0
```

### deploy.sh

Deploys a Docker image to Google Cloud Run.

**Usage**:
```bash
# Deploy specific version
./deploy.sh v1.0.0

# Deploy latest
./deploy.sh latest

# Deploy with custom revision tag
./deploy.sh v1.0.0 custom-rev-tag
```

**What it does**:
1. Validates image exists in GCR
2. Generates revision tag (adds 'v' prefix if starts with number)
3. Deploys to Cloud Run in `us-west1` region
4. Configures:
   - Service account: `cookbook-cloudrun-sa@kukbuk-tf.iam.gserviceaccount.com`
   - Secrets from Secret Manager (Gemini API key, OAuth ID)
   - Environment variables (project IDs, Firestore settings)
   - Memory: 512Mi, CPU: 1
   - Max instances: 1
   - Startup probe: `/actuator/health`

**Prerequisites**:
- Image must exist in GCR
- GCP project must have:
  - Cloud Run API enabled
  - Service account created
  - Secrets configured in Secret Manager

### release.sh

Chains build and deploy into a single command.

**Usage**:
```bash
# Build JVM image and deploy
./release.sh v1.0.0

# Build native image and deploy
./release.sh --native v1.0.0
```

**What it does**:
1. Builds image (JVM or native)
2. Pushes to GCR
3. Deploys to Cloud Run
4. Shows deployment URL

**Example**:
```bash
# Quick release
./release.sh v0.6.5

# Native release (takes longer)
./release.sh --native v0.6.5
```

### run-docker.sh

Runs the Cookbook application locally in Docker.

**Usage**:
```bash
# Run latest image
./run-docker.sh

# Run specific version
./run-docker.sh v1.0.0

# Run with custom port
PORT=9090 ./run-docker.sh
```

**What it does**:
- Starts container on port 8080 (or `$PORT`)
- Mounts `.env` file from project root
- Auto-removes container on stop
- Named container: `cookbook`

**Prerequisites**:
- `.env` file in project root with required environment variables:
  ```bash
  COOKBOOK_GEMINI_API_KEY=your_key_here
  COOKBOOK_GOOGLE_OAUTH_ID=your_oauth_id_here
  ```

**Testing**:
```bash
# Start container
./run-docker.sh v1.0.0

# Test in another terminal
curl http://localhost:8080/actuator/health

# Stop container
docker stop cookbook
```

### version-updater.sh

Manages version numbers in `build.gradle`.

**Usage**:
```bash
# Show current version
source ./version-updater.sh
extract_version

# Prepare release (remove -SNAPSHOT)
prepare_release

# Bump to next snapshot version
bump_to_next_snapshot

# Just remove -SNAPSHOT
remove_snapshot

# Just add -SNAPSHOT
add_snapshot
```

**Functions**:
- `extract_version`: Print current version from build.gradle
- `prepare_release`: Remove -SNAPSHOT suffix (0.6.5-SNAPSHOT → 0.6.5)
- `bump_to_next_snapshot`: Increment patch and add -SNAPSHOT (0.6.5 → 0.6.6-SNAPSHOT)
- `remove_snapshot`: Remove -SNAPSHOT from current version
- `add_snapshot`: Add -SNAPSHOT to current version
- `increment_patch`: Increment patch number (0.6.5 → 0.6.6)

**Example Manual Release**:
```bash
# 1. Prepare release version
cd extractor/scripts
source ./version-updater.sh
prepare_release  # 0.6.5-SNAPSHOT → 0.6.5

# 2. Build and deploy
./release.sh v0.6.5

# 3. Tag the release
cd ../../
git tag -a v0.6.5 -m "Release version 0.6.5"
git push origin v0.6.5

# 4. Bump to next snapshot
cd extractor/scripts
source ./version-updater.sh
bump_to_next_snapshot  # 0.6.5 → 0.6.6-SNAPSHOT

# 5. Commit version bump
cd ../../
git add extractor/build.gradle
git commit -m "chore: bump version to 0.6.6-SNAPSHOT"
git push origin main
```

## Common Workflows

### Local Development Testing

```bash
# Build JVM image locally
./build.sh --local

# Run locally
./run-docker.sh latest

# Test
curl http://localhost:8080/actuator/health

# Stop
docker stop cookbook
```

### Native Image Testing

```bash
# Build native image (takes ~10 minutes)
./build.sh --native --local --memory=12g

# Run
./run-docker.sh latest

# Native images start much faster (<1 second)
```

### Production Deployment (Manual)

⚠️ **Not recommended**: Use CI/CD pipeline instead.

```bash
# 1. Update version
cd extractor/scripts
source ./version-updater.sh
prepare_release

# 2. Build and push
./build.sh --native v0.6.5 --push --memory=12g

# 3. Deploy
./deploy.sh v0.6.5

# 4. Tag release
cd ../../
git tag -a v0.6.5 -m "Release 0.6.5"
git push origin v0.6.5

# 5. Bump version
cd extractor/scripts
source ./version-updater.sh
bump_to_next_snapshot

git add ../../extractor/build.gradle
git commit -m "chore: bump to 0.6.6-SNAPSHOT"
git push origin main
```

## Differences from CI/CD

| Feature | Manual Scripts | CI/CD Pipeline |
|---------|---------------|----------------|
| **Build Time** | ~10-15 min | ~10-15 min |
| **Testing** | Manual | Automated (unit + integration) |
| **Version Management** | Manual | Automated |
| **Git Tagging** | Manual | Automated |
| **Deployment** | Manual | Automated on merge |
| **Rollback** | Manual | GitHub Actions re-run |
| **Coverage Badge** | Manual | Auto-updated |
| **Security Scan** | Manual | Automated on PR |
| **Branch Protection** | Can bypass | Enforced |

## Migration to CI/CD

To switch from manual to automated deployment:

1. **Stop using these scripts** for production deployments
2. **Use Pull Requests** for all changes
3. **Merge to main** triggers automatic deployment
4. **Keep scripts** for local testing only

## Troubleshooting

### Build Fails - Out of Memory

**Error**: Native build fails with OOM error

**Fix**: Increase Docker memory:
```bash
./build.sh --native --local --memory=14g
```

### Push Fails - Not Authenticated

**Error**: `denied: Permission "storage.buckets.get" denied`

**Fix**: Authenticate to GCR:
```bash
gcloud auth configure-docker
```

### Deploy Fails - Service Account Missing

**Error**: `Service account not found`

**Fix**: Create service account:
```bash
gcloud iam service-accounts create cookbook-cloudrun-sa \
  --display-name="Cookbook Cloud Run Service Account"
```

### Image Not Found

**Error**: `Image not found in registry`

**Fix**: Verify image exists:
```bash
gcloud container images list --repository=gcr.io/kukbuk-tf
gcloud container images list-tags gcr.io/kukbuk-tf/cookbook
```

## Best Practices

✅ **DO**:
- Use `--local` for testing before pushing
- Test images locally with `run-docker.sh` before deploying
- Use version tags (not `latest`) for production
- Keep Docker memory at 12GB+ for native builds

❌ **DON'T**:
- Deploy to production without testing
- Use `latest` tag in production
- Skip version management
- Build native images without enough memory

## See Also

- [Main README](../../README.md) - CI/CD deployment strategy
- [CI/CD Workflow Guide](../../docs/CI_CD_WORKFLOW.md) - Automated pipeline details
- [Deployment Strategy](../../docs/CD.md) - Full deployment process
