# OpenTofu Deployment

This directory contains OpenTofu (Terraform) configuration for deploying the cookbook application to Google Cloud Platform.

## Prerequisites

### Docker-based (Recommended)
1. **Docker**: Ensure Docker is installed and running
2. **Google Cloud SDK**: Install locally and authenticate with `gcloud auth login`
3. **Service Account Key**: Place your service account key in `no-git/kukbuk-sa-tf-key.json`

### Local Installation (Alternative)
1. **Install OpenTofu**: Follow the [installation guide](https://opentofu.org/docs/intro/install/)
2. **Google Cloud SDK**: Install and authenticate with `gcloud auth login`

## Setup

1. **Set your project ID**: Edit the `project_id` variable in `main.tf` or use `-var="project_id=your-project"`

2. **Initialize OpenTofu**:
   ```bash
   make init
   ```

## Deployment

### Using Makefile (Recommended)

```bash
# Initialize (first time only)
make init

# Plan deployment
make plan TAG=v1.0.0

# Deploy with confirmation
make deploy TAG=v1.0.0

# Apply without confirmation
make apply TAG=v1.0.0

# Interactive shell for debugging
make shell

# View outputs
make output

# Destroy infrastructure
make destroy
```

### Manual Docker Commands

```bash
# Build Docker image
make build

# Interactive shell
docker run --rm -it \
  -v "$(pwd)/..:/workspace" \
  -v "$(HOME)/.config/gcloud:/root/.config/gcloud:ro" \
  -w /workspace/terraform \
  cookbook-opentofu

# Inside container:
tofu init
tofu plan -var="image_tag=v1.0.0"
tofu apply -var="image_tag=v1.0.0"
```

## Resources Created

### Core Infrastructure
- **Cloud Run Service**: Main cookbook API service with auto-scaling
- **Cloud Function**: Token broker for OAuth handling  
- **Firestore Database**: Native mode database for recipe storage
- **Secret Manager**: Secure storage for API keys and service account keys
- **Storage Bucket**: Cloud Function source code storage
- **IAM Policies**: Service access permissions and roles

### API Services Enabled
- Cloud Run API
- Cloud Functions API (Gen 2)
- Cloud Build API
- Firestore API
- Secret Manager API

### Service Accounts
- **Cookbook Cloud Run Service Account**: For the main API service
- **Firestore Service Account**: For database access with `datastore.user` role

## Managing Secrets

The deployment expects these secrets in Google Secret Manager:

```bash
# Set your GCP project
gcloud config set project your-project-id

# Gemini API Key
echo -n "your-gemini-api-key" | gcloud secrets create gemini-api-key --data-file=-

# Google OAuth Client ID  
echo -n "your-oauth-client-id" | gcloud secrets create google-oauth-id --data-file=-

# Firestore service account key is created automatically by Terraform
```

## Environment Variables

The Cloud Run service is automatically configured with:
- `SPRING_PROFILES_ACTIVE=gcp`
- `COOKBOOK_GEMINI_API_KEY` (from Secret Manager)
- `COOKBOOK_GOOGLE_OAUTH_ID` (from Secret Manager)
- `FIRESTORE_SERVICE_KEY` (from Secret Manager - auto-generated)
- `FIRESTORE_PROJECT_ID` (your GCP project ID)

## Testing Setup

For testing infrastructure changes safely:

1. **Create test project**: 
   ```bash
   gcloud projects create your-test-project --name="Test Environment"
   ```

2. **Update project ID** in `main.tf` or use variable:
   ```bash
   make plan TAG=latest -var="project_id=your-test-project"
   ```

3. **Deploy to test**:
   ```bash
   make deploy TAG=latest
   ```

## State Management

### Current Setup
- Local state files for development/testing
- Production state backup: `terraform.tfstate.backup-prod`

### Remote State (Recommended for Production)
Configure remote state by editing `versions.tf`:

```hcl
backend "gcs" {
  bucket = "your-terraform-state-bucket"
  prefix = "cookbook/terraform.tfstate"
}
```

## Outputs

After successful deployment:
- `cloud_run_url`: Main API service URL
- `token_broker_url`: OAuth token broker URL
- `firestore_database_name`: Database name (usually "(default)")
- `firestore_service_account_email`: Service account for database access
- `project_id`: GCP Project ID used
- `revision_tag`: Cloud Run revision tag

## Firestore Database

### Schema
See `firestore-schema.md` for detailed database structure including:
- User recipes collection: `/users/{userId}/recipes/{recipeId}`
- Public recipes: `/public_recipes/{recipeId}`
- User profiles: `/users/{userId}`
- Templates: `/templates/{templateId}`

### Security
- Users can only access their own recipes
- Service account has `datastore.user` role for backend access
- Authentication required for all operations

## Troubleshooting

### Common Issues

1. **Archive creation error**: Ensure Docker mounts include parent directory
2. **Permission denied**: Check service account key is properly configured
3. **API not enabled**: Let Terraform enable APIs automatically or enable manually
4. **Secret not found**: Create required secrets in Secret Manager first

### Debug Commands

```bash
# Check deployment status
make output

# Interactive debugging
make shell

# View logs
gcloud run services logs read cookbook --region=us-west1

# Test endpoints
curl $(tofu output -raw cloud_run_url)/actuator/health
curl $(tofu output -raw token_broker_url)
```

## Files Structure

- `main.tf` - Core Cloud Run service and variables
- `firestore.tf` - Firestore database and service account
- `token-broker.tf` - Cloud Function for OAuth
- `versions.tf` - Provider requirements  
- `Makefile` - Docker-based automation
- `firestore-schema.md` - Database schema documentation
- `no-git/` - Service account keys (gitignored)

## Clean Up

```bash
# Destroy all resources
make destroy

# Clean Docker resources
make clean
```