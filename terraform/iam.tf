# Service account for Cloud Run

resource "google_service_account" "cookbook_cloudrun_service_account" {
  account_id   = "cookbook-cloudrun-sa"
  display_name = "Kukbuk Cloud Run Service Account"
  description  = "Service account for Cookbook Cloud Run service"
  project      = var.project_id
}

# Grant Firebase Admin role (for token verification)
resource "google_project_iam_member" "cookbook_firebase_admin" {
  project = var.project_id
  role    = "roles/firebase.admin"
  member  = "serviceAccount:${google_service_account.cookbook_cloudrun_service_account.email}"

  depends_on = [google_service_account.cookbook_cloudrun_service_account]
}

# Grant Firestore User role (for data access)
resource "google_project_iam_member" "cloudrun_firestore_user" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.cookbook_cloudrun_service_account.email}"

  depends_on = [google_service_account.cookbook_cloudrun_service_account]
}

# Grant Firestore Viewer role (for read operations)
resource "google_project_iam_member" "cloudrun_firestore_viewer" {
  project = var.project_id
  role    = "roles/datastore.viewer"
  member  = "serviceAccount:${google_service_account.cookbook_cloudrun_service_account.email}"

  depends_on = [google_service_account.cookbook_cloudrun_service_account]
}

# Grant Secret Manager Secret Accessor (for secrets)
resource "google_project_iam_member" "cloudrun_secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.cookbook_cloudrun_service_account.email}"

  depends_on = [google_service_account.cookbook_cloudrun_service_account]
}

# Grant Cloud Run Invoker (allow service to call itself if needed)
resource "google_project_iam_member" "cookbook_run_invoker" {
  project = var.project_id
  role    = "roles/run.invoker"
  member  = "serviceAccount:${google_service_account.cookbook_cloudrun_service_account.email}"

  depends_on = [google_service_account.cookbook_cloudrun_service_account]
}

# Output service account email
output "service_account_email" {
  description = "Service account email for Cookbook service"
  value       = google_service_account.cookbook_cloudrun_service_account.email
}