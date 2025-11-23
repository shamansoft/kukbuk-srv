# Variables for Save-a-Recipe Terraform configuration

variable "project_id" {
  description = "GCP Project ID"
  type        = string
  default     = "kukbuk-tf"
}

variable "region" {
  description = "GCP region for resources"
  type        = string
  default     = "us-west1"
}

variable "service_name" {
  description = "Cloud Run service name"
  type        = string
  default     = "cookbook"
}

variable "image_tag" {
  description = "Docker image tag for Cloud Run"
  type        = string
  default     = "latest"
}

variable "revision_tag" {
  description = "Cloud Run revision tag (auto-generated from image_tag if not provided)"
  type        = string
  default     = ""
}

variable "container_image" {
  description = "Full container image path"
  type        = string
  default     = "gcr.io/kukbuk-tf/cookbook"
}

variable "repo_base" {
  description = "Container registry repository base path"
  type        = string
  default     = "gcr.io/kukbuk-tf/cookbook"
}

variable "firestore_location" {
  description = "Firestore database location"
  type        = string
  default     = "us-west1"
}
