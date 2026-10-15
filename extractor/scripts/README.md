# Local Docker Build Scripts

⚠️ **Note**: Production builds and deploys run in CI/CD (`.github/workflows/deploy.yml` builds the native
image and dispatches the deploy to `sar-infra`). These scripts are for building and testing images locally.

## Prerequisites

- Docker installed and running
- Google Cloud SDK (`gcloud`) installed and authenticated, for pushing to GCR
- For native builds: 12GB+ RAM available to Docker

## Scripts

### build.sh

Builds Docker images for the Cookbook application (`../Dockerfile.jvm` or `../Dockerfile.native`).

**Usage**:
```bash
# Build JVM image locally (no push)
./build.sh --local

# Build JVM image and push to GCR
./build.sh v1.0.0 --push

# Build native image locally (requires more memory)
./build.sh --native --local --memory=12g

# Build with custom tag
./build.sh my-test-tag --local
```

**Options**:
- `--local`: Build locally without pushing to GCR (default behavior)
- `--push`: Push image to Google Container Registry after build
- `--native`: Build GraalVM native image (slower, smaller, faster startup)
- `--memory=XGg`: Set Docker memory limit (native builds need 12GB+)
- First argument: Version tag (e.g., `v1.0.0`). If starts with number, prefixed with 'v'

**Output**: `gcr.io/kukbuk-tf/cookbook:<tag>` (JVM and native use the same name, different content)

Native images need an x86-64-v3 CPU; on Apple Silicon only the JVM image builds locally.

### push.sh

Pushes an existing local image to Google Container Registry (used in the
[rollback procedure](../../docs/deployment/rollback.md)).

**Usage**:
```bash
./push.sh v1.0.0
./push.sh latest
```

**Prerequisites**:
- Image must already be built locally
- Must be authenticated to GCR via `gcloud auth configure-docker`

### version-updater.sh

Version helpers for `build.gradle.kts`. `deploy.yml` sources it to release and bump versions.

**Usage**:
```bash
source ./version-updater.sh
extract_version          # print current version
prepare_release          # 0.6.5-SNAPSHOT → 0.6.5
bump_to_next_snapshot    # 0.6.5 → 0.6.6-SNAPSHOT
remove_snapshot          # drop -SNAPSHOT
add_snapshot             # add -SNAPSHOT
increment_patch          # 0.6.5 → 0.6.6
```

## Testing an image locally

```bash
./build.sh --local
docker run --rm -p 8080:8080 \
  -e COOKBOOK_GEMINI_API_KEY=$COOKBOOK_GEMINI_API_KEY \
  -e COOKBOOK_GOOGLE_OAUTH_ID=$COOKBOOK_GOOGLE_OAUTH_ID \
  gcr.io/kukbuk-tf/cookbook:latest
curl http://localhost:8080/actuator/health
```

## Troubleshooting

### Build Fails - Out of Memory

Native build fails with OOM: increase Docker memory, e.g. `./build.sh --native --local --memory=14g`.

### Push Fails - Not Authenticated

`denied: Permission "storage.buckets.get" denied`: run `gcloud auth configure-docker`.

### Image Not Found

```bash
gcloud container images list --repository=gcr.io/kukbuk-tf
gcloud container images list-tags gcr.io/kukbuk-tf/cookbook
```

## See Also

- [Main README](../../README.md) - CI/CD deployment strategy
- [CI/CD Workflow Guide](../../docs/CI_CD_WORKFLOW.md) - Automated pipeline details
- [Deployment Strategy](../../docs/CD.md) - Full deployment process
