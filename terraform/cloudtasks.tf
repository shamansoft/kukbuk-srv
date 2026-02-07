# Cloud Tasks queue for YouTube recipe processing

resource "google_cloud_tasks_queue" "youtube_recipes" {
  name     = "youtube-recipes"
  location = var.region
  project  = var.project_id

  retry_config {
    max_attempts       = 3
    max_backoff        = "300s"
    min_backoff        = "10s"
    max_doublings      = 4
    max_retry_duration = "0s"
  }

  rate_limits {
    max_concurrent_dispatches = 10
    max_dispatches_per_second = 5
  }

  depends_on = [
    google_project_service.cloudtasks_api,
    google_service_account.cookbook_cloudrun_service_account,
  ]
}

output "cloud_tasks_queue_name" {
  description = "Name of the YouTube recipes Cloud Tasks queue"
  value       = google_cloud_tasks_queue.youtube_recipes.name
}
