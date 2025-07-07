terraform {
  required_version = ">= 1.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }

  backend "gcs" {
    bucket = "kukbuk-tf-tfstate-bucket" # Replace with your actual bucket name
    prefix = "terraform/state"         # Optional: organizes state files within the bucket
  }
}
