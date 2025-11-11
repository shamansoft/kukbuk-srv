# Foundation APIs that must be enabled FIRST
# These APIs are required by Terraform itself to manage the project

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
