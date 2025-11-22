# Token Broker Cloud Function configuration

# Create a storage bucket for Cloud Function source code
resource "google_storage_bucket" "function_source" {
  name                        = "${var.project_id}-function-source"
  location                    = var.region
  uniform_bucket_level_access = true
  force_destroy               = true

  depends_on = [
    google_project_service.cloudfunctions_api,
    google_project_service.cloudbuild_api
  ]
}

# Create source code archive
data "archive_file" "token_broker_source" {
  type        = "zip"
  source_dir  = "../token-broker"
  output_path = "./build/token-broker-source.zip"
}

# Upload source code to bucket
resource "google_storage_bucket_object" "token_broker_source" {
  name   = "token-broker-source-${data.archive_file.token_broker_source.output_md5}.zip"
  bucket = google_storage_bucket.function_source.name
  source = data.archive_file.token_broker_source.output_path
}

# Cloud Function for token brokering
resource "google_cloudfunctions2_function" "token_broker" {
  name     = "token-broker"
  location = var.region

  build_config {
    runtime     = "nodejs20"
    entry_point = "getIdToken"

    source {
      storage_source {
        bucket = google_storage_bucket.function_source.name
        object = google_storage_bucket_object.token_broker_source.name
      }
    }
  }

  service_config {
    max_instance_count = 100
    min_instance_count = 0
    available_memory   = "256M"
    timeout_seconds    = 60

    environment_variables = {
      LOG_EXECUTION_ID = "true"
      TARGET_AUDIENCE  = google_cloud_run_service.cookbook.status[0].url
    }

    ingress_settings               = "ALLOW_ALL"
    all_traffic_on_latest_revision = true
    service_account_email          = "${data.google_project.current.number}-compute@developer.gserviceaccount.com"
  }

  depends_on = [
    google_project_service.cloudfunctions_api,
    google_project_service.cloudbuild_api,
  ]
}

# Make the function publicly accessible
resource "google_cloudfunctions2_function_iam_member" "invoker" {
  project        = google_cloudfunctions2_function.token_broker.project
  location       = google_cloudfunctions2_function.token_broker.location
  cloud_function = google_cloudfunctions2_function.token_broker.name
  role           = "roles/cloudfunctions.invoker"
  member         = "allUsers"
}

# Output the function URL
output "token_broker_url" {
  description = "URL of the token broker Cloud Function"
  value       = google_cloudfunctions2_function.token_broker.service_config[0].uri
}
