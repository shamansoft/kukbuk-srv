# Firestore configuration for recipe storage

# Enable Firestore API
resource "google_project_service" "firestore_api" {
  service = "firestore.googleapis.com"
}

# Firestore database for recipe storage
resource "google_firestore_database" "default" {
  project     = var.project_id
  name        = "(default)"
  location_id = var.firestore_location
  type        = "FIRESTORE_NATIVE"

  depends_on = [
    google_project_service.firestore_api,
  ]
}

# Outputs for Firestore configuration
output "firestore_database_name" {
  description = "Firestore database name"
  value       = google_firestore_database.default.name
}

output "firestore_service_account_email" {
  description = "Cloud Run service account email (used for Firestore access)"
  value       = "cookbook-cloudrun-sa@${var.project_id}.iam.gserviceaccount.com"
}