# Deployment Guide

This guide explains how to build and deploy the Cookbook application to Google Cloud Platform.

## Deployment Methods

### 1. Automated CI/CD (Recommended for Production)
TBD!!!

**GitHub Actions automatically deploys on every PR merge to main.**

- ✅ Builds native GraalVM image
- ✅ Pushes to Google Container Registry
- ✅ Deploys via Terraform/OpenTofu
- ✅ Creates GitHub releases
- ✅ Tests deployment health

**Setup**: See [.github/deployment-setup.md](.github/deployment-setup.md)

### 2. Manual Deployment (Current Method)

Use the build scripts and Terraform for manual deployments during development.

## Manual Deployment Overview

The manual deployment process has two main stages:
1. **Build**: Create Docker image and push to Google Container Registry (GCR)
2. **Deploy**: Deploy the image to Cloud Run using OpenTofu/Terraform

## Prerequisites

### Required
- Docker installed and running
- Google Cloud SDK (`gcloud`) installed and authenticated (`gcloud auth login`)
- Access to GCP project (`kukbuk-tf`)

### For Native Builds (Optional)
- At least 12GB RAM available for Docker
- Additional build time (~10-15 minutes vs ~2-3 minutes for JVM)

## Quick Start

### Standard Deployment (native image)

```bash
# 1. Build and push Docker image
cd extractor/scripts
./build.sh --native
# Output will show the version tag (e.g., 0.0.55)

# 2. Deploy with Terraform (from project root)
cd ../../terraform
make deploy TAG=<version>
```
Replace `<version>` with the version shown in build output (e.g., `0.0.55`).

## Detailed Build Process

### Build Script Location
```
extractor/scripts/build.sh
```

### Build Options

#### 1. Auto-versioned Build (Recommended)
```bash
cd extractor/scripts
./build.sh
```
- Automatically increments version in `build.gradle`
- Outputs: `0.0.X` (where X is incremented patch number)
- Builds JVM image by default
- Pushes to `gcr.io/kukbuk-tf/cookbook:0.0.X`

#### 2. Custom Version Build
```bash
cd extractor/scripts
./build.sh v1.5.0
```
- Uses your specified version tag
- Pushes to `gcr.io/kukbuk-tf/cookbook:v1.5.0`

#### 3. Native Build (GraalVM)
```bash
cd extractor/scripts
./build.sh --native --memory=12g
```
- Creates native executable (faster startup, smaller memory footprint)
- Requires more RAM during build (minimum 12GB)
- Takes significantly longer to build
- Target version for production deployments

#### 4. Local Build (No Push)
```bash
cd extractor/scripts
./build.sh local
```
- Builds image locally without pushing to GCR
- Useful for local testing with `run-docker.sh`

### Build Process Details

**JVM Build:**
1. Runs `./gradlew build` (tests, compiles, packages JAR)
2. Builds Docker image using `Dockerfile.jvm`
3. Pushes to GCR: `gcr.io/kukbuk-tf/cookbook:TAG`

**Native Build:**
1. Skips Gradle (native compilation happens in Docker)
2. Builds Docker image using `Dockerfile.native`
3. GraalVM native-image compilation inside Docker
4. Pushes to GCR: `gcr.io/kukbuk-tf/cookbook:TAG`

## Detailed Deployment Process

### Deployment Method 1: Terraform/OpenTofu (Recommended)

Located in `terraform/` directory.

#### Prerequisites
```bash
# Ensure secrets exist in Secret Manager
gcloud config set project kukbuk-tf

# Create secrets if they don't exist
echo -n "YOUR_GEMINI_API_KEY" | gcloud secrets create gemini-api-key --data-file=-
echo -n "YOUR_OAUTH_CLIENT_ID" | gcloud secrets create google-oauth-id --data-file=-
```

#### Deploy Commands

**Full Deployment:**
```bash
cd terraform

# Plan and review changes
make plan TAG=0.0.55

# Deploy (with confirmation prompt)
make deploy TAG=0.0.55

# Deploy without confirmation
make apply TAG=0.0.55
```

**View Deployment Status:**
```bash
cd terraform
make output
```

**Interactive Terraform Shell:**
```bash
cd terraform
make shell

# Inside shell:
tofu plan -var="image_tag=0.0.55"
tofu apply -var="image_tag=0.0.55"
tofu output
```

#### What Terraform Deploys

- **Cloud Run Service**: Main cookbook API
  - Image: `gcr.io/cookbook-451120/cookbook:TAG`
  - Region: `us-west1`
  - Port: `8080`
  - Environment: `SPRING_PROFILES_ACTIVE=gcp`

- **Secrets Configuration**: Auto-injects from Secret Manager
  - `COOKBOOK_GEMINI_API_KEY`
  - `COOKBOOK_GOOGLE_OAUTH_ID`
  - `FIRESTORE_SERVICE_KEY`
  - `FIRESTORE_PROJECT_ID`

- **Cloud Function**: Token broker (OAuth handling)
- **Firestore Database**: Recipe storage
- **IAM Policies**: Service permissions

## Complete Workflow Examples

### Example 1: Standard native Deployment
```bash
# Step 1: Build (auto-version)
cd extractor/scripts
./build.sh --native
# Output: "Build complete! Image details: 0.0.55"

# Step 2: Deploy with Terraform
cd ../../terraform
make deploy TAG=0.0.55
# Review plan, type 'y' to confirm

# Step 3: Verify
make output
# Shows Cloud Run URL and other outputs
```

### Example 4: Local Testing First
```bash
# Step 1: Build locally
cd extractor/scripts
./build.sh local

# Step 2: Test locally
./run-docker.sh
```

## Understanding the Image Registry

The build scripts push to GCR. Note the project ID differences:

- **Build script** uses: `gcr.io/kukbuk-tf/cookbook:TAG`
- **Terraform** references: `gcr.io/cookbook-451120/cookbook:TAG` (in `main.tf`)

**Important**: Check that your `main.tf` variable `repo_base` matches where `build.sh` pushes images (line 10 of `build.sh`).

## Troubleshooting

### Build Issues

**Gradle build fails:**
```bash
cd extractor
./gradlew clean build
```

**Native build out of memory:**
```bash
./build.sh --native --memory=16g  # Increase memory
```

**Docker push fails:**
```bash
# Re-authenticate with GCR
gcloud auth configure-docker
```

### Deployment Issues

**Image not found:**
```bash
# Verify image exists in GCR
gcloud container images list --repository=gcr.io/kukbuk-tf

# List all tags for cookbook
gcloud container images list-tags gcr.io/kukbuk-tf/cookbook
```

**Secrets not found:**
```bash
# List secrets
gcloud secrets list

# Create missing secrets
echo -n "YOUR_VALUE" | gcloud secrets create SECRET_NAME --data-file=-
```

**Terraform state issues:**
```bash
cd terraform
make shell
tofu state list
tofu refresh
```

### Testing Deployment

```bash
# Get deployed URL
cd terraform
make output

# Test health endpoint
curl https://YOUR_CLOUD_RUN_URL/actuator/health

# View logs
gcloud run services logs read cookbook --region=us-west1 --limit=50
```

## Best Practices

1. **Always use Terraform for production deployments** (infrastructure as code, reproducible)
2. **Use auto-versioning for development** (`./build.sh` without args)
3. **Use semantic versioning for releases** (`./build.sh v1.2.3`)
4. **Use native builds for production** (better performance)
5. **Test locally first** (`./build.sh local` + `./run-docker.sh`)
6. **Review Terraform plan** before applying changes
7. **Keep secrets in Secret Manager**, never commit to git

## Version Management

Version is stored in `extractor/build.gradle`:
```gradle
version = '0.0.55'
```

The `build.sh` script automatically increments the patch number when no tag is provided.

Manual version update:
```bash
cd extractor/scripts
source ./version-updater.sh
update_version
```

## Infrastructure Management

### View Current Infrastructure
```bash
cd terraform
make output
```

### Destroy Infrastructure
```bash
cd terraform
make destroy
# Type 'y' to confirm
```

### Clean Up Docker Resources
```bash
cd terraform
make clean
```

## Summary

| Task | Command | Location |
|------|---------|----------|
| Build JVM image | `./build.sh` | `extractor/scripts/` |
| Build native image | `./build.sh --native --memory=12g` | `extractor/scripts/` |
| Build with version | `./build.sh v1.0.0` | `extractor/scripts/` |
| Deploy (Terraform) | `make deploy TAG=v1.0.0` | `terraform/` |
| View deployment | `make output` | `terraform/` |
| Test locally | `./run-docker.sh` | `extractor/scripts/` |

## Additional Resources

- **Terraform README**: `terraform/README.md` - Detailed Terraform documentation
- **Main README**: `README.md` - Project overview
- **CLAUDE.md**: Project-specific guidance for AI assistants
- **Firestore Schema**: `terraform/firestore-schema.md` - Database structure
