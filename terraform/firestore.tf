# Firestore configuration for recipe storage

# Firestore database for recipe storage
resource "google_firestore_database" "default" {
  project     = var.project_id
  name        = "(default)"
  location_id = var.firestore_location
  type        = "FIRESTORE_NATIVE"

  # Prevent accidental deletion
  lifecycle {
    prevent_destroy = true
  }

  depends_on = [
    google_project_service.firestore_api
  ]
}

# =========================================================================
# Firestore Composite Indexes
# =========================================================================

# Composite index for recipes by user and creation time
resource "google_firestore_index" "recipes_by_user_and_created" {
  project    = var.project_id
  database   = google_firestore_database.default.name
  collection = "recipes"

  fields {
    field_path = "userId"
    order      = "ASCENDING"
  }

  fields {
    field_path = "createdAt"
    order      = "DESCENDING"
  }

  depends_on = [google_firestore_database.default]
}

# Composite index for recipes by user and title
resource "google_firestore_index" "recipes_by_user_and_title" {
  project    = var.project_id
  database   = google_firestore_database.default.name
  collection = "recipes"

  fields {
    field_path = "userId"
    order      = "ASCENDING"
  }

  fields {
    field_path = "title"
    order      = "ASCENDING"
  }

  depends_on = [google_firestore_database.default]
}

# =========================================================================
# Firestore Security Rules
# =========================================================================

# Create a ruleset from the firestore.rules file
resource "google_firebaserules_ruleset" "firestore" {
  project = var.project_id

  source {
    files {
      name    = "firestore.rules"
      content = file("${path.module}/firestore.rules")
    }
  }

  depends_on = [
    google_project_service.firebaserules_api,
    google_firestore_database.default,
  ]
}

# Deploy the ruleset to the Firestore database
resource "google_firebaserules_release" "firestore" {
  project      = var.project_id
  name         = "cloud.firestore"
  ruleset_name = "projects/${var.project_id}/rulesets/${google_firebaserules_ruleset.firestore.name}"

  lifecycle {
    replace_triggered_by = [
      google_firebaserules_ruleset.firestore
    ]
  }

  depends_on = [
    google_firebaserules_ruleset.firestore,
  ]
}

# =========================================================================
# Outputs
# =========================================================================

output "firestore_database_name" {
  description = "Firestore database name"
  value       = google_firestore_database.default.name
}

output "firestore_location" {
  description = "Firestore database location"
  value       = google_firestore_database.default.location_id
}

output "firestore_service_account_email" {
  description = "Cloud Run service account email (used for Firestore access)"
  value       = google_service_account.cookbook_cloudrun_service_account.email
}