# Cookbook Project

![Coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)
A recipe extraction and management system with a Java Spring Boot backend, featuring automated CI/CD pipelines, GraalVM native image compilation, and deployment to Google Cloud Run.

## Technologies

- **Java 21** with Spring Boot for the REST API
- **GraalVM Native Image** for fast startup and low memory footprint
- **Gradle** for building and dependency management
- **Docker** for containerization
- **GitHub Actions** for CI/CD automation
- **OpenTofu/Terraform** for infrastructure as code
- **Google Cloud Platform** (Cloud Run, Firestore, Secret Manager)

## CI/CD Workflow

This project uses automated CI/CD pipelines for all deployments:

### Pull Request Workflow

When you create a PR to `main`:

1. ✅ **Automated Testing**
   - Unit tests
   - Integration tests
   - Code coverage check (40% minimum)
   - Security vulnerability scan (OWASP Dependency Check)

2. 📊 **Automated PR Comments**
   - Coverage report with line-by-line breakdown
   - Security scan results with vulnerability counts

3. ✅ **Build Verification**
   - JAR build validation
   - All checks must pass before merge

### Deployment Workflow

When PR is merged to `main`:

1. 🧪 **Test & Validate** (2-3 min)
   - Remove `-SNAPSHOT` from version
   - Run all tests
   - Generate coverage report

2. 🏗️ **Build & Push** (10-15 min)
   - Build GraalVM native image for linux/amd64
   - Push to Google Container Registry
   - Tag with release version

3. 🚀 **Deploy** (2-3 min)
   - Deploy to Cloud Run via Terraform
   - Health check validation
   - Automatic rollback on failure

4. 📝 **Finalize** (1 min)
   - Create git tag (e.g., `v0.6.5`)
   - Bump version to next SNAPSHOT (e.g., `0.6.6-SNAPSHOT`)
   - Update coverage badge in README
   - Create GitHub Release with deployment details

**Total deployment time**: ~15-20 minutes from merge to production

### Version Management

- Versions follow **Maven/Gradle conventions**: `X.Y.Z-SNAPSHOT`
- Development uses `-SNAPSHOT` suffix (e.g., `0.6.6-SNAPSHOT`)
- Releases remove `-SNAPSHOT` (e.g., `0.6.5`)
- Patch version auto-incremented after each release
- Version managed by `extractor/scripts/version-updater.sh`

### Manual Deployment Triggers

```bash
# Manually trigger deployment workflow
gh workflow run deploy.yml --ref main

# Test finalize step only (skip build, deployment)
gh workflow run deploy.yml --ref main \
  -f skip_build=true \
  -f skip_deploy=true \
  -f skip_finalize=false

# Build and push only (skip deployment)
gh workflow run deploy.yml --ref main \
  -f skip_deploy=true
```

## Local Development

### Build the Project

```bash
# Build JAR (fast, for development)
./gradlew :cookbook:build

# Build native image (slow, for production testing)
./gradlew :cookbook:nativeCompile

# Run tests
./gradlew :cookbook:test

# Run integration tests
./gradlew :cookbook:intTest

# Check coverage
./gradlew :cookbook:checkCoverage
```

### Run Locally

```bash
# Run with Spring Boot (JVM)
./gradlew :cookbook:bootRun

# Run with Docker (JVM image)
cd extractor/scripts && ./build.sh --local
docker run -p 8080:8080 \
  -e COOKBOOK_GEMINI_API_KEY=$COOKBOOK_GEMINI_API_KEY \
  gcr.io/kukbuk-tf/cookbook:latest

# Run with Docker (native image, local build)
cd extractor/scripts && ./build.sh --native --local --memory=12g
docker run -p 8080:8080 \
  -e COOKBOOK_GEMINI_API_KEY=$COOKBOOK_GEMINI_API_KEY \
  gcr.io/kukbuk-tf/cookbook:latest
```

### Environment Variables

Required for local development:

```bash
# .env file or export
COOKBOOK_GEMINI_API_KEY=your_api_key_here
COOKBOOK_GOOGLE_OAUTH_ID=your_oauth_id_here

# For GCP deployment (handled by Cloud Run)
GOOGLE_CLOUD_PROJECT_ID=kukbuk-tf
FIRESTORE_PROJECT_ID=kukbuk-tf
FIRESTORE_ENABLED=true
```

## Legacy/Manual Deployment

For manual deployment using scripts (not recommended for production):

See [extractor/scripts/README.md](extractor/scripts/README.md) for legacy deployment instructions.

## Project Structure

```
.
├── .github/workflows/     # CI/CD workflows
│   ├── deploy.yml        # Main deployment pipeline
│   └── pr-validation.yml # PR checks
├── extractor/            # Main application (renamed to 'cookbook' in Gradle)
│   ├── src/             # Java source code
│   ├── build.gradle.kts # Build configuration
│   ├── Dockerfile.native # Native image Docker build
│   └── scripts/         # Legacy deployment scripts
├── recipe-sdk/          # Recipe data models
├── terraform/           # Infrastructure as Code
│   ├── main.tf         # Cloud Run configuration
│   ├── firestore.tf    # Database configuration
│   └── token-broker.tf # OAuth function
└── token-broker/        # Node.js OAuth helper
```

## Architecture

- **Native Image**: GraalVM AOT compilation for fast startup (<1s) and low memory (~200MB)
- **Stateless API**: Horizontally scalable, auto-scaling 0→N instances
- **Firestore**: NoSQL database for recipe storage
- **Cloud Run**: Serverless container platform with built-in load balancing
- **GitHub App**: Automated version management with branch protection bypass

## Documentation

- [CI/CD Workflow Guide](docs/CI_CD_WORKFLOW.md) - Detailed pipeline documentation
- [Testing Strategy](docs/TESTING_WORKFLOW.md) - Test automation guide
- [Deployment Strategy](docs/CD.md) - Full deployment process
- [GitHub App Setup](.github/GITHUB_APP_SETUP.md) - Bot configuration for automation
- [Branch Protection](.github/BRANCH_PROTECTION.md) - Repository security setup

## Contributing

1. Create feature branch from `main`
2. Make changes and commit
3. Push and create Pull Request
4. Wait for automated checks to pass
5. Request review from `@khisamutdinov` (code owner)
6. Merge PR (triggers automatic deployment)

All merges to `main` automatically deploy to production.