# Phase 1: Terraform/OpenTofu Infrastructure

**Repository:** `sar-srv/terraform/`
**Duration:** 1-2 days
**Phase:** Can be done during Phase 1 or Phase 2
**Note:** This codifies the manual GCP setup for reproducibility

---

## Overview

Create Terraform/OpenTofu configuration to manage all GCP resources as code. This allows reproducible deployments and proper infrastructure version control.

**What will be managed:**
- GCP APIs enablement
- Firestore database
- Firestore indexes
- Service account
- IAM role bindings
- Cloud Run service

**What must be manual:**
- Firebase project initialization (one-time)
- Firebase Auth provider configuration
- Firestore security rules (deployed via Firebase CLI)
- Secret values (API keys)

---

## Ticket TF-1: Setup Terraform Configuration

**Files:** `terraform/versions.tf`, `terraform/variables.tf`, `terraform/main.tf` (NEW/UPDATE)

### Task
Create base Terraform configuration with providers and variables.

### Implementation

**Create: `terraform/versions.tf`**

```hcl
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.9.0"
    }
  }

  # Optional: Remote state (recommended for teams)
  # backend "gcs" {
  #   bucket  = "kukbuk-tf-terraform-state"
  #   prefix  = "terraform/state"
  # }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
```

**Create: `terraform/variables.tf`**

```hcl
variable "project_id" {
  description = "GCP Project ID"
  type        = string
  default     = "kukbuk-tf"
}

variable "region" {
  description = "GCP region for resources"
  type        = string
  default     = "us-west1"
}

variable "service_name" {
  description = "Cloud Run service name"
  type        = string
  default     = "cookbook"
}

variable "image_tag" {
  description = "Docker image tag for Cloud Run"
  type        = string
  default     = "latest"
}

variable "container_image" {
  description = "Full container image path"
  type        = string
  default     = "gcr.io/kukbuk-tf/cookbook"
}
```

**Update: `terraform/main.tf`**

```hcl
# Main Terraform configuration for kukbuk-tf

# Local values for common references
locals {
  project_id = var.project_id
  region     = var.region
  labels = {
    environment = "production"
    managed_by  = "terraform"
    project     = "save-a-recipe"
  }
}

# Data sources
data "google_client_config" "current" {}

# Project reference
data "google_project" "project" {
  project_id = var.project_id
}
```

### Verification

```bash
cd terraform

# Initialize Terraform
terraform init

# Validate configuration
terraform validate

# Expected output: Success! The configuration is valid.
```

### Acceptance Criteria
- [ ] versions.tf created with provider config
- [ ] variables.tf created with project variables
- [ ] main.tf created with locals
- [ ] `terraform init` succeeds
- [ ] `terraform validate` succeeds

---

## Ticket TF-2: Enable Required APIs

**File:** `terraform/apis.tf` (NEW)

### Task
Enable all required GCP APIs via Terraform.

### Implementation

```hcl
# Enable required Google Cloud APIs

resource "google_project_service" "required_apis" {
  for_each = toset([
    "firebase.googleapis.com",
    "firestore.googleapis.com",
    "identitytoolkit.googleapis.com",
    "run.googleapis.com",
    "cloudbuild.googleapis.com",
    "secretmanager.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "cloudkms.googleapis.com",  # For OAuth token encryption
  ])

  project = var.project_id
  service = each.value

  # Don't disable on destroy to avoid breaking dependencies
  disable_on_destroy = false
}

# Output enabled APIs
output "enabled_apis" {
  description = "List of enabled APIs"
  value       = [for api in google_project_service.required_apis : api.service]
}
```

### Verification

```bash
terraform plan

# Should show 10 APIs to be enabled (or already enabled)
```

### Acceptance Criteria
- [ ] apis.tf created
- [ ] All 10 APIs included (including cloudkms.googleapis.com)
- [ ] `terraform plan` shows API resources
- [ ] disable_on_destroy is false

---

## Ticket TF-3: Manage Firestore Database

**File:** `terraform/firestore.tf` (NEW)

### Task
Create/import Firestore database and indexes.

### Implementation

```hcl
# Firestore database configuration

resource "google_firestore_database" "database" {
  project     = var.project_id
  name        = "(default)"
  location_id = var.region
  type        = "FIRESTORE_NATIVE"

  # Prevent accidental deletion
  lifecycle {
    prevent_destroy = true
  }

  depends_on = [
    google_project_service.required_apis
  ]
}

# Composite index for recipes by user and creation time
resource "google_firestore_index" "recipes_by_user_and_created" {
  project    = var.project_id
  database   = google_firestore_database.database.name
  collection = "recipes"

  fields {
    field_path = "userId"
    order      = "ASCENDING"
  }

  fields {
    field_path = "createdAt"
    order      = "DESCENDING"
  }

  depends_on = [google_firestore_database.database]
}

# Composite index for recipes by user and title
resource "google_firestore_index" "recipes_by_user_and_title" {
  project    = var.project_id
  database   = google_firestore_database.database.name
  collection = "recipes"

  fields {
    field_path = "userId"
    order      = "ASCENDING"
  }

  fields {
    field_path = "title"
    order      = "ASCENDING"
  }

  depends_on = [google_firestore_database.database]
}

# Outputs
output "firestore_database_name" {
  description = "Firestore database name"
  value       = google_firestore_database.database.name
}

output "firestore_location" {
  description = "Firestore database location"
  value       = google_firestore_database.database.location_id
}
```

### Notes

If Firestore database already exists from manual setup, import it:

```bash
terraform import google_firestore_database.database "(default)"
```

### Verification

```bash
terraform plan

# If database exists: should show 0 changes for database, 2 new indexes
# If database doesn't exist: should show 1 database + 2 indexes to create
```

### Acceptance Criteria
- [ ] firestore.tf created
- [ ] Database resource defined with prevent_destroy
- [ ] Two composite indexes defined
- [ ] Database imported if already exists
- [ ] Indexes will be created on apply

---

## Ticket TF-4: Manage Service Account and IAM

**File:** `terraform/iam.tf` (NEW)

### Task
Create service account and grant required IAM roles.

### Implementation

```hcl
# Service account for Cloud Run

resource "google_service_account" "cookbook" {
  account_id   = "cookbook-service"
  display_name = "Cookbook Cloud Run Service Account"
  description  = "Service account for Cookbook Cloud Run service"
  project      = var.project_id
}

# Grant Firebase Admin role (for token verification)
resource "google_project_iam_member" "cookbook_firebase_admin" {
  project = var.project_id
  role    = "roles/firebase.admin"
  member  = "serviceAccount:${google_service_account.cookbook.email}"

  depends_on = [google_service_account.cookbook]
}

# Grant Firestore User role (for data access)
resource "google_project_iam_member" "cookbook_firestore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.cookbook.email}"

  depends_on = [google_service_account.cookbook]
}

# Grant Secret Manager Secret Accessor (for secrets)
resource "google_project_iam_member" "cookbook_secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.cookbook.email}"

  depends_on = [google_service_account.cookbook]
}

# Grant Cloud Run Invoker (allow service to call itself if needed)
resource "google_project_iam_member" "cookbook_run_invoker" {
  project = var.project_id
  role    = "roles/run.invoker"
  member  = "serviceAccount:${google_service_account.cookbook.email}"

  depends_on = [google_service_account.cookbook]
}

# Output service account email
output "service_account_email" {
  description = "Service account email for Cookbook service"
  value       = google_service_account.cookbook.email
}
```

### Notes

If service account already exists, import it:

```bash
terraform import google_service_account.cookbook \
  projects/kukbuk-tf/serviceAccounts/cookbook-service@kukbuk-tf.iam.gserviceaccount.com
```

### Verification

```bash
terraform plan

# Should show service account + 4 IAM bindings
```

### Acceptance Criteria
- [ ] iam.tf created
- [ ] Service account resource defined
- [ ] Four IAM role bindings defined
- [ ] Service account imported if already exists
- [ ] Output shows service account email

---

## Ticket TF-4a: Setup Cloud KMS for OAuth Token Encryption

**File:** `terraform/kms.tf` (NEW)

### Task
Create Cloud KMS resources to encrypt OAuth tokens stored in Firestore.

### Implementation

```hcl
# Cloud KMS Key Ring for Cookbook service
resource "google_kms_key_ring" "cookbook_keyring" {
  name     = "cookbook-keyring"
  location = var.region
  project  = var.project_id

  depends_on = [google_project_service.required_apis]
}

# Crypto Key for OAuth token encryption
resource "google_kms_crypto_key" "oauth_token_key" {
  name     = "oauth-token-key"
  key_ring = google_kms_key_ring.cookbook_keyring.id
  purpose  = "ENCRYPT_DECRYPT"

  # Automatic rotation every 90 days
  rotation_period = "7776000s" # 90 days in seconds

  lifecycle {
    prevent_destroy = true
  }
}

# Grant service account permission to encrypt/decrypt with this key
resource "google_kms_crypto_key_iam_member" "cookbook_encrypter_decrypter" {
  crypto_key_id = google_kms_crypto_key.oauth_token_key.id
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = "serviceAccount:${google_service_account.cookbook.email}"

  depends_on = [
    google_kms_crypto_key.oauth_token_key,
    google_service_account.cookbook
  ]
}

# Outputs
output "kms_keyring_name" {
  description = "KMS Key Ring name"
  value       = google_kms_key_ring.cookbook_keyring.name
}

output "kms_crypto_key_name" {
  description = "KMS Crypto Key name for OAuth token encryption"
  value       = google_kms_crypto_key.oauth_token_key.name
}

output "kms_crypto_key_id" {
  description = "Full KMS Crypto Key ID"
  value       = google_kms_crypto_key.oauth_token_key.id
}
```

### Notes

**Purpose:**
Encrypt Google OAuth tokens before storing them in Firestore user profiles. Backend's `TokenEncryptionService` uses this key to:
- Encrypt OAuth tokens when users sign in
- Decrypt OAuth tokens when making Drive API calls
- Automatic key rotation every 90 days for enhanced security

**Security Benefits:**
- Tokens encrypted at rest using Google-managed encryption keys
- Automatic key rotation
- Access logs and audit trails via Cloud Logging
- Centralized key management with IAM controls

### Verification

```bash
terraform plan

# Should show:
# - google_kms_key_ring.cookbook_keyring
# - google_kms_crypto_key.oauth_token_key
# - google_kms_crypto_key_iam_member.cookbook_encrypter_decrypter

# After apply, verify:
gcloud kms keyrings list --location=us-west1 --project=kukbuk-tf
gcloud kms keys list --location=us-west1 --keyring=cookbook-keyring --project=kukbuk-tf
```

### Acceptance Criteria
- [ ] kms.tf created
- [ ] Key ring resource defined with correct location (us-west1)
- [ ] Crypto key resource defined with ENCRYPT_DECRYPT purpose
- [ ] Automatic rotation configured (90 days)
- [ ] IAM member resource grants service account encrypt/decrypt permissions
- [ ] Outputs defined for keyring and key names
- [ ] prevent_destroy lifecycle rule on crypto key
- [ ] Verification commands show resources exist

---

## Ticket TF-5: Manage Cloud Run Service

**File:** `terraform/cloudrun.tf` (NEW)

### Task
Define Cloud Run service configuration.

### Implementation

```hcl
# Cloud Run service configuration

resource "google_cloud_run_service" "cookbook" {
  name     = var.service_name
  location = var.region
  project  = var.project_id

  template {
    spec {
      service_account_name = google_service_account.cookbook.email

      # Container configuration
      containers {
        image = "${var.container_image}:${var.image_tag}"

        ports {
          container_port = 8080
        }

        # Environment variables
        env {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "gcp"
        }

        env {
          name  = "GCP_PROJECT_ID"
          value = var.project_id
        }

        # Resource limits
        resources {
          limits = {
            cpu    = "1000m"
            memory = "512Mi"
          }
        }
      }
    }

    metadata {
      annotations = {
        "autoscaling.knative.dev/maxScale"         = "10"
        "run.googleapis.com/startup-cpu-boost"     = "true"
        "run.googleapis.com/execution-environment" = "gen2"
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }

  # Allow external image updates without terraform
  lifecycle {
    ignore_changes = [
      template[0].spec[0].containers[0].image,
    ]
  }

  depends_on = [
    google_project_service.required_apis,
    google_service_account.cookbook,
    google_firestore_database.database
  ]
}

# Allow unauthenticated invocations (Firebase handles auth)
resource "google_cloud_run_service_iam_member" "public_access" {
  service  = google_cloud_run_service.cookbook.name
  location = google_cloud_run_service.cookbook.location
  role     = "roles/run.invoker"
  member   = "allUsers"

  depends_on = [google_cloud_run_service.cookbook]
}

# Output Cloud Run URL
output "cloud_run_url" {
  description = "URL of the Cloud Run service"
  value       = google_cloud_run_service.cookbook.status[0].url
}
```

### Notes

If Cloud Run service already exists, import it:

```bash
terraform import google_cloud_run_service.cookbook \
  locations/us-west1/namespaces/kukbuk-tf/services/cookbook
```

### Verification

```bash
terraform plan

# Should show Cloud Run service + public access policy
```

### Acceptance Criteria
- [ ] cloudrun.tf created
- [ ] Service uses cookbook service account
- [ ] Image from GCR with configurable tag
- [ ] Environment variables set
- [ ] Resource limits defined
- [ ] Public access allowed
- [ ] lifecycle ignore_changes for image

---

## Ticket TF-6: Create Deployment Script

**File:** `terraform/deploy.sh` (NEW)

### Task
Create script to simplify Terraform deployments.

### Implementation

```bash
#!/bin/bash
set -e

# Deploy infrastructure with Terraform/OpenTofu
# Usage: ./deploy.sh [image_tag]

IMAGE_TAG=${1:-latest}

echo "================================================"
echo "Deploying Save-a-Recipe Infrastructure"
echo "Project: kukbuk-tf"
echo "Image Tag: $IMAGE_TAG"
echo "================================================"

# Initialize Terraform if needed
if [ ! -d ".terraform" ]; then
    echo "Initializing Terraform..."
    terraform init
fi

# Validate configuration
echo "Validating configuration..."
terraform validate

# Plan changes
echo "Planning changes..."
terraform plan \
    -var="image_tag=$IMAGE_TAG" \
    -out=tfplan

# Ask for confirmation
echo ""
read -p "Apply these changes? (yes/no): " -r
if [[ ! $REPLY =~ ^[Yy]es$ ]]; then
    echo "Deployment cancelled"
    rm -f tfplan
    exit 0
fi

# Apply changes
echo "Applying changes..."
terraform apply tfplan

# Clean up plan file
rm -f tfplan

# Show outputs
echo ""
echo "================================================"
echo "Deployment Complete!"
echo "================================================"
terraform output

echo ""
echo "Cloud Run URL:"
terraform output -raw cloud_run_url
echo ""
```

Make it executable:

```bash
chmod +x terraform/deploy.sh
```

### Verification

```bash
# Dry run
cd terraform
./deploy.sh v1.0.0

# Should show plan and ask for confirmation
```

### Acceptance Criteria
- [ ] deploy.sh created
- [ ] Script initializes Terraform
- [ ] Script validates config
- [ ] Script creates plan
- [ ] Script asks for confirmation
- [ ] Script applies plan
- [ ] Script shows outputs
- [ ] Script is executable

---

## Ticket TF-7: Create Documentation

**File:** `terraform/README.md` (NEW)

### Task
Document Terraform setup and usage.

### Implementation

```markdown
# Terraform Infrastructure for Save-a-Recipe

## Overview

This directory contains Terraform configuration to manage all GCP infrastructure for the Save-a-Recipe project.

## Prerequisites

- Terraform >= 1.5.0 or OpenTofu >= 1.5.0
- GCP project: `kukbuk-tf`
- Authenticated with GCP: `gcloud auth application-default login`

## Managed Resources

✅ Managed by Terraform:
- GCP APIs enablement
- Firestore database
- Firestore composite indexes
- Service account (cookbook-service)
- IAM role bindings
- Cloud Run service
- Public access policy

❌ NOT managed by Terraform (manual setup required):
- Firebase project initialization (one-time via Console)
- Firebase Authentication providers (via Firebase Console)
- Firestore security rules (deployed via Firebase CLI)
- Secret values (API keys stored in Secret Manager)

## Quick Start

### First-Time Setup

1. **Initialize Terraform:**
   ```bash
   cd terraform
   terraform init
   ```

2. **Import existing resources** (if they exist):
   ```bash
   # Firestore database
   terraform import google_firestore_database.database "(default)"

   # Service account (if exists)
   terraform import google_service_account.cookbook \
     projects/kukbuk-tf/serviceAccounts/cookbook-service@kukbuk-tf.iam.gserviceaccount.com

   # Cloud Run service (if exists)
   terraform import google_cloud_run_service.cookbook \
     locations/us-west1/namespaces/kukbuk-tf/services/cookbook
   ```

3. **Deploy infrastructure:**
   ```bash
   ./deploy.sh latest
   ```

### Regular Deployments

```bash
# Deploy with specific image tag
./deploy.sh v1.2.3

# Or manually
terraform plan -var="image_tag=v1.2.3"
terraform apply -var="image_tag=v1.2.3"
```

## Deployment Script

`./deploy.sh [image_tag]` automates:
1. Terraform initialization
2. Configuration validation
3. Plan generation
4. Confirmation prompt
5. Apply changes
6. Output display

## Outputs

After deployment, Terraform outputs:
- `cloud_run_url` - Cloud Run service URL
- `service_account_email` - Service account email
- `firestore_database_name` - Firestore database name
- `firestore_location` - Firestore location

View outputs:
```bash
terraform output
terraform output -raw cloud_run_url
```

## State Management

**Current:** Local state file (`terraform.tfstate`)

**Recommended for teams:** Remote state in GCS

To enable remote state, uncomment in `versions.tf`:
```hcl
backend "gcs" {
  bucket  = "kukbuk-tf-terraform-state"
  prefix  = "terraform/state"
}
```

Create bucket first:
```bash
gsutil mb -p kukbuk-tf -l us-west1 gs://kukbuk-tf-terraform-state
gsutil versioning set on gs://kukbuk-tf-terraform-state
```

## Manual Steps Required

### 1. Firebase Initialization
See: `../tickets/phase-1-gcp-instructions.md`

### 2. Firestore Security Rules
Deploy via Firebase CLI:
```bash
cd ../extractor
firebase deploy --only firestore:rules --project kukbuk-tf
```

### 3. Firebase Auth Providers
Configure in Firebase Console:
- Enable Google Sign-In
- Add authorized domains

## Troubleshooting

### Error: Resource already exists
**Solution:** Import the resource:
```bash
terraform import <resource_type>.<name> <resource_id>
```

### Error: Permission denied
**Solution:** Ensure you have Owner or Editor role:
```bash
gcloud projects get-iam-policy kukbuk-tf
```

### Error: State drift detected
**Solution:** Refresh state:
```bash
terraform refresh
terraform plan
```

## File Structure

```
terraform/
├── main.tf           # Main config with locals
├── versions.tf       # Provider and Terraform versions
├── variables.tf      # Input variables
├── apis.tf           # GCP APIs enablement
├── firestore.tf      # Firestore database and indexes
├── iam.tf            # Service account and IAM roles
├── cloudrun.tf       # Cloud Run service
├── deploy.sh         # Deployment script
└── README.md         # This file
```

## Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `project_id` | GCP Project ID | `kukbuk-tf` |
| `region` | GCP region | `us-west1` |
| `service_name` | Cloud Run service name | `cookbook` |
| `image_tag` | Docker image tag | `latest` |
| `container_image` | Container image path | `gcr.io/kukbuk-tf/cookbook` |

Override variables:
```bash
terraform apply -var="image_tag=v1.2.3" -var="region=us-central1"
```

## Cost Estimate

Estimated monthly costs (low traffic):
- Cloud Run: ~$5-10
- Firestore: ~$1-5
- Total: **< $20/month**

Use Cloud Billing Reports to monitor actual costs.
```

### Acceptance Criteria
- [ ] README.md created
- [ ] Documents all managed resources
- [ ] Includes quick start guide
- [ ] Explains manual steps
- [ ] Troubleshooting section included
- [ ] File structure documented

---

## Ticket TF-8: Test and Deploy

### Task
Test the complete Terraform configuration and deploy infrastructure.

### Implementation

**Step 1: Validate**

```bash
cd terraform

# Initialize
terraform init

# Validate
terraform validate

# Format check
terraform fmt -check -recursive
```

**Step 2: Plan**

```bash
# Create plan
terraform plan -var="image_tag=v1.0.0" -out=tfplan

# Review plan output
# Verify all expected resources
```

**Step 3: Import Existing Resources (if any)**

```bash
# If database exists
terraform import google_firestore_database.database "(default)"

# If service account exists
terraform import google_service_account.cookbook \
  projects/kukbuk-tf/serviceAccounts/cookbook-service@kukbuk-tf.iam.gserviceaccount.com

# If Cloud Run exists
terraform import google_cloud_run_service.cookbook \
  locations/us-west1/namespaces/kukbuk-tf/services/cookbook
```

**Step 4: Apply**

```bash
# Apply the plan
terraform apply tfplan

# Or use deployment script
./deploy.sh v1.0.0
```

**Step 5: Verify**

```bash
# Check outputs
terraform output

# Verify in GCP Console
# - Cloud Run service running
# - Firestore database exists
# - Service account has roles
# - APIs enabled
```

**Step 6: Test Updates**

```bash
# Change image tag
./deploy.sh v1.0.1

# Verify only image tag changed (due to lifecycle ignore_changes)
```

### Acceptance Criteria
- [ ] All Terraform files validate
- [ ] Existing resources imported
- [ ] Plan shows expected changes
- [ ] Apply succeeds without errors
- [ ] All outputs display correctly
- [ ] Resources visible in GCP Console
- [ ] Deployment script works
- [ ] Can update image tag without changing other resources

---

## Testing Checklist

- [ ] `terraform init` succeeds
- [ ] `terraform validate` passes
- [ ] `terraform fmt -check` passes (formatting correct)
- [ ] `terraform plan` shows expected resources
- [ ] Existing resources imported successfully
- [ ] `terraform apply` succeeds
- [ ] All outputs correct
- [ ] Cloud Run service accessible
- [ ] Service account has all roles
- [ ] Firestore database operational
- [ ] `./deploy.sh` script works
- [ ] Can deploy with different image tags
- [ ] State file created

---

## Completion Criteria

✅ All Terraform files created
✅ Configuration validates
✅ Existing resources imported
✅ Successfully deployed via Terraform
✅ Deployment script functional
✅ Documentation complete
✅ No manual resources remain (except Firebase auth config)
✅ Infrastructure reproducible

---

## Estimated Time

- TF-1: Setup - 30 minutes
- TF-2: APIs - 15 minutes
- TF-3: Firestore - 30 minutes
- TF-4: IAM - 30 minutes
- TF-5: Cloud Run - 45 minutes
- TF-6: Deploy script - 30 minutes
- TF-7: Documentation - 30 minutes
- TF-8: Test & deploy - 1 hour

**Total: 1-2 days** (including testing and import)

---

## Dependencies

- GCP project exists (`kukbuk-tf`)
- Manual Firebase setup may be done first (or import resources after)
- Backend code ready to deploy

---

## Notes

### When to Use

- **Phase 1:** Can be done in parallel with backend development
- **Phase 2:** Recommended to complete for production readiness

### Why Terraform?

- **Reproducibility:** Can rebuild infrastructure from scratch
- **Version Control:** Infrastructure changes tracked in git
- **Documentation:** Code documents the actual infrastructure
- **Team Collaboration:** Everyone uses same configuration
- **Disaster Recovery:** Can restore infrastructure quickly

### Limitations

- Firebase project initialization still manual (no Terraform provider support yet)
- Firebase Auth providers configured in Console
- Firestore rules deployed via Firebase CLI (better dev experience)
- Secret values set manually (security best practice)
