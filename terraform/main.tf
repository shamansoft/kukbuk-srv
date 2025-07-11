provider "google" {
  project = var.project_id
  region  = var.region
}

# Variables
variable "project_id" {
  description = "GCP Project ID"
  type        = string
  default     = "kukbuk-tf"
}

variable "region" {
  description = "GCP Region"
  type        = string
  default     = "us-west1"
}

variable "service_name" {
  description = "Cloud Run service name"
  type        = string
  default     = "cookbook"
}

variable "image_tag" {
  description = "Docker image tag"
  type        = string
  default     = "latest"
}

variable "revision_tag" {
  description = "Cloud Run revision tag"
  type        = string
  default     = ""
}

variable "firestore_location" {
  description = "Firestore database location"
  type        = string
  default     = "us-west1"
}

variable "repo_base" {
  type = string
  default = "gcr.io/kukbuk-tf/cookbook"
}

# Local values for processing tags similar to deploy.sh
locals {
  # Clean tag by replacing periods with hyphens
  cleaned_tag = replace(var.image_tag, ".", "-")

  # Generate revision tag (add 'v' prefix if starts with number)
  auto_revision_tag = can(regex("^[0-9]", local.cleaned_tag)) ? "v${local.cleaned_tag}" : local.cleaned_tag

  # Use provided revision_tag or auto-generate from image_tag
  final_revision_tag = var.revision_tag != "" ? var.revision_tag : local.auto_revision_tag
}

# Enable required APIs
resource "google_project_service" "run_api" {
  service = "run.googleapis.com"
}


resource "google_project_service" "secretmanager_api" {
  service = "secretmanager.googleapis.com"
}

# Secret Manager secrets
resource "google_secret_manager_secret" "gemini_api_key" {
  secret_id = "gemini-api-key"

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "google_oauth_id" {
  secret_id = "google-oauth-id"

  replication {
    auto {}
  }
}

resource "google_service_account" "cookbook_cloudrun_service_account" {
  account_id   = "cookbook-cloudrun-sa"
  display_name = "Kukbuk Cloud Run Service Account"
  project      = var.project_id
}

# Cloud Run service
resource "google_cloud_run_service" "cookbook" {
  name     = var.service_name
  location = var.region

  template {
    metadata {
      name = "${var.service_name}-${local.final_revision_tag}"
      labels = {
        "run.googleapis.com/startupProbeType" = "Custom"
      }

      annotations = {
        "autoscaling.knative.dev/maxScale" = "1"
        "run.googleapis.com/startup-cpu-boost" = "false"
        "run.googleapis.com/client-name" = "terraform"
      }
    }
    
    spec {
      container_concurrency = 80
      timeout_seconds = 300
      service_account_name = google_service_account.cookbook_cloudrun_service_account.email

      containers {
        name  = "cookbook-1"
        image = "${var.repo_base}:${var.image_tag}"

        ports {
          name           = "http1"
          container_port = 8080
        }

        env {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "gcp"
        }

        env {
          name = "COOKBOOK_GEMINI_API_KEY"
          value_from {
            secret_key_ref {
              name = google_secret_manager_secret.gemini_api_key.secret_id
              key  = "latest"
            }
          }
        }

        env {
          name = "COOKBOOK_GOOGLE_OAUTH_ID"
          value_from {
            secret_key_ref {
              name = google_secret_manager_secret.google_oauth_id.secret_id
              key  = "latest"
            }
          }
        }

        env {
          name = "GOOGLE_CLOUD_PROJECT_ID"
          value = var.project_id
        }

        env {
          name = "FIRESTORE_PROJECT_ID"
          value = var.project_id
        }

        env {
          name = "FIRESTORE_ENABLED"
          value = "true"
        }

        env {
          name = "RECIPE_CACHE_ENABLED"
          value = "true"
        }

        resources {
          limits = {
            cpu    = "1000m"
            memory = "512Mi"
          }
        }

        startup_probe {
          initial_delay_seconds = 30
          timeout_seconds      = 10
          period_seconds       = 10
          failure_threshold    = 12

          http_get {
            path = "/actuator/health"
            port = 8080
          }
        }
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }

  depends_on = [google_project_service.run_api]

  lifecycle {
    ignore_changes = [
      # Ignore gcloud-managed annotations and metadata
      template[0].metadata[0].annotations["run.googleapis.com/client-name"],
      template[0].metadata[0].annotations["run.googleapis.com/client-version"],
      template[0].metadata[0].annotations["run.googleapis.com/cpu-throttling"],
      template[0].metadata[0].annotations["run.googleapis.com/execution-environment"],
      # Ignore revision names (let gcloud manage them)
      template[0].metadata[0].name,
      # Ignore traffic splitting (let gcloud manage tagged revisions)
      traffic,
    ]
  }
}

# IAM policy for Cloud Run service (allow authenticated users)
data "google_iam_policy" "authenticated" {
  binding {
    role = "roles/run.invoker"
    members = [
      "allAuthenticatedUsers",
    ]
  }
}

resource "google_cloud_run_service_iam_policy" "authenticated" {
  location = google_cloud_run_service.cookbook.location
  project  = google_cloud_run_service.cookbook.project
  service  = google_cloud_run_service.cookbook.name

  policy_data = data.google_iam_policy.authenticated.policy_data
}


# Outputs
output "cloud_run_url" {
  description = "URL of the Cloud Run service"
  value       = google_cloud_run_service.cookbook.status[0].url
}


output "project_id" {
  description = "GCP Project ID"
  value       = var.project_id
}

output "revision_tag" {
  description = "Cloud Run revision tag used"
  value       = local.final_revision_tag
}
