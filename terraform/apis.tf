# Enable required Google Cloud APIs
#
# This file consolidates all API enablement in one place.
# Foundation APIs are enabled first, then all others depend on them.

# =========================================================================
# Foundation APIs - Must be enabled FIRST
# =========================================================================

# Cloud Resource Manager API - Required for managing project resources
resource "google_project_service" "cloudresourcemanager_api" {
  service            = "cloudresourcemanager.googleapis.com"
  disable_on_destroy = false
}

# IAM API - Required for managing service accounts and permissions
resource "google_project_service" "iam_api" {
  service            = "iam.googleapis.com"
  disable_on_destroy = false
  depends_on         = [google_project_service.cloudresourcemanager_api]
}

# Service Usage API - Required for enabling other APIs
resource "google_project_service" "serviceusage_api" {
  service            = "serviceusage.googleapis.com"
  disable_on_destroy = false
  depends_on         = [google_project_service.cloudresourcemanager_api]
}

# =========================================================================
# Application APIs - Existing resources (match current state)
# =========================================================================

resource "google_project_service" "firestore_api" {
  project            = var.project_id
  service            = "firestore.googleapis.com"
  disable_on_destroy = true
  depends_on         = [google_project_service.serviceusage_api]
}

resource "google_project_service" "run_api" {
  project            = var.project_id
  service            = "run.googleapis.com"
  disable_on_destroy = true
  depends_on         = [google_project_service.serviceusage_api]
}

resource "google_project_service" "cloudbuild_api" {
  project            = var.project_id
  service            = "cloudbuild.googleapis.com"
  disable_on_destroy = true
  depends_on         = [google_project_service.serviceusage_api]
}

resource "google_project_service" "cloudfunctions_api" {
  project            = var.project_id
  service            = "cloudfunctions.googleapis.com"
  disable_on_destroy = true
  depends_on         = [google_project_service.serviceusage_api]
}

resource "google_project_service" "secretmanager_api" {
  project            = var.project_id
  service            = "secretmanager.googleapis.com"
  disable_on_destroy = true
  depends_on         = [google_project_service.serviceusage_api]
}

# =========================================================================
# New APIs for Phase 1
# =========================================================================

resource "google_project_service" "firebase_api" {
  project            = var.project_id
  service            = "firebase.googleapis.com"
  disable_on_destroy = false
  depends_on         = [google_project_service.serviceusage_api]
}

resource "google_project_service" "firebaserules_api" {
  project            = var.project_id
  service            = "firebaserules.googleapis.com"
  disable_on_destroy = false
  depends_on         = [google_project_service.serviceusage_api]
}

resource "google_project_service" "identitytoolkit_api" {
  project            = var.project_id
  service            = "identitytoolkit.googleapis.com"
  disable_on_destroy = false
  depends_on         = [google_project_service.serviceusage_api]
}

resource "google_project_service" "iamcredentials_api" {
  project            = var.project_id
  service            = "iamcredentials.googleapis.com"
  disable_on_destroy = false
  depends_on         = [google_project_service.serviceusage_api]
}

resource "google_project_service" "cloudkms_api" {
  project            = var.project_id
  service            = "cloudkms.googleapis.com"
  disable_on_destroy = false
  depends_on         = [google_project_service.serviceusage_api]
}

# Output enabled APIs
output "enabled_apis" {
  description = "List of enabled APIs"
  value = [
    google_project_service.cloudresourcemanager_api.service,
    google_project_service.iam_api.service,
    google_project_service.serviceusage_api.service,
    google_project_service.firestore_api.service,
    google_project_service.run_api.service,
    google_project_service.cloudbuild_api.service,
    google_project_service.cloudfunctions_api.service,
    google_project_service.secretmanager_api.service,
    google_project_service.firebase_api.service,
    google_project_service.firebaserules_api.service,
    google_project_service.identitytoolkit_api.service,
    google_project_service.iamcredentials_api.service,
    google_project_service.cloudkms_api.service,
  ]
}