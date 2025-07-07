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

# Service account for Firestore access from backend
resource "google_service_account" "firestore_service" {
  account_id   = "firestore-cookbook"
  display_name = "Firestore Service Account for Cookbook"
  description  = "Service account for Firestore access from cookbook backend"
}

# Firestore Data Editor role for the service account
resource "google_project_iam_binding" "firestore_data_editor" {
  project = var.project_id
  role    = "roles/datastore.user"

  members = [
    "serviceAccount:${google_service_account.firestore_service.email}",
  ]
}

# Service account key for the backend
resource "google_service_account_key" "firestore_service_key" {
  service_account_id = google_service_account.firestore_service.name
  public_key_type    = "TYPE_X509_PEM_FILE"
}

# Store the service account key in Secret Manager
resource "google_secret_manager_secret" "firestore_service_key" {
  secret_id = "firestore-service-key"
  
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "firestore_service_key" {
  secret      = google_secret_manager_secret.firestore_service_key.id
  secret_data = base64decode(google_service_account_key.firestore_service_key.private_key)
}

# Outputs for Firestore configuration
output "firestore_database_name" {
  description = "Firestore database name"
  value       = google_firestore_database.default.name
}

output "firestore_service_account_email" {
  description = "Firestore service account email"
  value       = google_service_account.firestore_service.email
}