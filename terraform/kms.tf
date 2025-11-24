# Cloud KMS configuration for OAuth token encryption
#
# Purpose: Encrypt Google OAuth tokens before storing them in Firestore user profiles
# Security: Tokens encrypted at rest using Google-managed encryption keys with automatic 90-day rotation

# Cloud KMS Key Ring for Cookbook service
resource "google_kms_key_ring" "cookbook_keyring" {
  name     = "cookbook-keyring"
  location = var.region
  project  = var.project_id

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.cloudkms_api]
}

# Crypto Key for OAuth token encryption
resource "google_kms_crypto_key" "oauth_token_key" {
  name     = "oauth-token-key"
  key_ring = google_kms_key_ring.cookbook_keyring.id
  purpose  = "ENCRYPT_DECRYPT"

  # Automatic rotation every 90 days
  rotation_period = "7776000s" # 90 days in seconds

  lifecycle {
    prevent_destroy = true
  }
}

# Grant service account permission to encrypt/decrypt with this key
resource "google_kms_crypto_key_iam_member" "cookbook_encrypter_decrypter" {
  crypto_key_id = google_kms_crypto_key.oauth_token_key.id
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = "serviceAccount:${google_service_account.cookbook_cloudrun_service_account.email}"

  depends_on = [
    google_kms_crypto_key.oauth_token_key,
    google_service_account.cookbook_cloudrun_service_account
  ]
}

# Outputs
output "kms_keyring_name" {
  description = "KMS Key Ring name"
  value       = google_kms_key_ring.cookbook_keyring.name
}

output "kms_crypto_key_name" {
  description = "KMS Crypto Key name for OAuth token encryption"
  value       = google_kms_crypto_key.oauth_token_key.name
}

output "kms_crypto_key_id" {
  description = "Full KMS Crypto Key ID"
  value       = google_kms_crypto_key.oauth_token_key.id
}