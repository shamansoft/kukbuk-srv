# Main Terraform configuration for kukbuk-tf

provider "google" {
  project = var.project_id
  region  = var.region
}

# Local values for common references
locals {
  project_id = var.project_id
  region     = var.region
  labels = {
    environment = "production"
    managed_by  = "terraform"
    project     = "save-a-recipe"
  }

  # Clean tag by replacing periods with hyphens
  cleaned_tag = replace(var.image_tag, ".", "-")

  # Generate revision tag (add 'v' prefix if starts with number)
  auto_revision_tag = can(regex("^[0-9]", local.cleaned_tag)) ? "v${local.cleaned_tag}" : local.cleaned_tag

  # Use provided revision_tag or auto-generate from image_tag
  final_revision_tag = var.revision_tag != "" ? var.revision_tag : local.auto_revision_tag
}

# Data sources
data "google_client_config" "current" {}

data "google_project" "current" {
  project_id = var.project_id
}

# Outputs
output "project_id" {
  description = "GCP Project ID"
  value       = var.project_id
}

output "revision_tag" {
  description = "Cloud Run revision tag used"
  value       = local.final_revision_tag
}
