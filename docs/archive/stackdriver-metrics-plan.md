# Plan: Enable Cloud Monitoring (Stackdriver) Metrics

## 📋 Table of Contents

- [ ] 1. Dependency Management
    - [ ] 1.1 Add `micrometer-registry-stackdriver` to Version Catalog
    - [ ] 1.2 Add dependency to `extractor` module
- [ ] 2. Application Configuration
    - [ ] 2.1 Configure Stackdriver export in `application.yaml`
    - [ ] 2.2 Configure step/interval for Cloud Run
- [ ] 3. Infrastructure (Terraform)
    - [ ] 3.1 Grant `roles/monitoring.metricWriter` to Service Account
- [ ] 4. Verification
    - [ ] 4.1 Verify build

## Detailed Plan

### 1. Dependency Management

**Objective:** specific Micrometer library for Google Cloud Monitoring.

**Steps:**
1.  Open `gradle/libs.versions.toml`.
2.  Add `micrometer-registry-stackdriver = { module = "io.micrometer:micrometer-registry-stackdriver" }` to the `[libraries]` section. (Note: Version is managed by Spring Boot BOM).
3.  Open `extractor/build.gradle.kts`.
4.  Add `implementation(libs.micrometer.registry.stackdriver)` to the `dependencies` block.

**Files to modify:**
- `gradle/libs.versions.toml`
- `extractor/build.gradle.kts`

### 2. Application Configuration

**Objective:** Configure Spring Boot to export metrics to GCP.

**Steps:**
1.  Open `extractor/src/main/resources/application.yaml`.
2.  Add the `management.metrics.export.stackdriver` configuration block.
3.  Set `enabled` to `true` (can use conditional on profile or env var if needed, but safe to default to true if credential check handles local dev, or disable locally).
    *   *Refinement:* Since we run locally, we might want to disable it by default or only enable it when `GOOGLE_CLOUD_PROJECT_ID` is present. However, standard practice is to enable it and let it fail gracefully or use a profile.
    *   *Better approach:* Configure it to use `${GOOGLE_CLOUD_PROJECT_ID}` and set `enabled` to `true`.
4.  Set `step` (reporting interval) to `1m` (standard for Cloud Run/GCP).

**Configuration snippet:**
```yaml
management:
  metrics:
    export:
      stackdriver:
        enabled: true
        project-id: ${GOOGLE_CLOUD_PROJECT_ID}
        step: 1m
```

**Files to modify:**
- `extractor/src/main/resources/application.yaml`

### 3. Infrastructure (Terraform)

**Objective:** Grant necessary permissions to the Cloud Run Service Account.

**Steps:**
1.  Open `terraform/iam.tf`.
2.  Add a new `google_project_iam_member` resource.
3.  Grant `roles/monitoring.metricWriter` to `google_service_account.cookbook_cloudrun_service_account.email`.

**Files to modify:**
- `terraform/iam.tf`

**Snippet:**
```hcl
# Grant Monitoring Metric Writer role (for Stackdriver metrics)
resource "google_project_iam_member" "cloudrun_metric_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.cookbook_cloudrun_service_account.email}"

  depends_on = [google_service_account.cookbook_cloudrun_service_account]
}
```

### 4. Verification

**Objective:** Ensure the project builds with the new dependencies.

**Steps:**
1.  Run `./gradlew :cookbook:build` to verify dependency resolution and compilation.

**Dependencies:**
- None

**Expected outcome:**
- Build succeeds.
- Application includes `micrometer-registry-stackdriver` jar.
