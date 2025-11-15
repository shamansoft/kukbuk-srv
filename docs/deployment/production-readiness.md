# Production Readiness

This document outlines security improvements, best practices, and implementation checklists for production deployments.

## Security Improvements

### Workload Identity Federation (Recommended)

**Why Switch from Service Account Keys**:

| Service Account Keys | Workload Identity Federation |
|---------------------|------------------------------|
| ❌ Long-lived credentials | ✅ Short-lived tokens (1 hour) |
| ❌ Must rotate every 90 days | ✅ No key rotation needed |
| ❌ Stored in GitHub Secrets | ✅ No secrets to store |
| ❌ Key compromise = breach | ✅ Breach-resistant |
| ❌ Manual management | ✅ Fully automated |

### Setup Instructions

#### Step 1: Enable Required APIs

```bash
export PROJECT_ID="kukbuk-tf"
export PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")

gcloud services enable \
  iamcredentials.googleapis.com \
  cloudresourcemanager.googleapis.com \
  sts.googleapis.com \
  --project=$PROJECT_ID
```

#### Step 2: Create Workload Identity Pool

```bash
gcloud iam workload-identity-pools create "github-pool" \
  --project="$PROJECT_ID" \
  --location="global" \
  --display-name="GitHub Actions Pool"
```

#### Step 3: Create Workload Identity Provider

```bash
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --project="$PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub Provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
  --attribute-condition="assertion.repository_owner=='YOUR_GITHUB_ORG'" \
  --issuer-uri="https://token.actions.githubusercontent.com"
```

**Replace `YOUR_GITHUB_ORG`** with your GitHub organization or username.

#### Step 4: Create Service Account

```bash
gcloud iam service-accounts create github-actions-deploy \
  --display-name="GitHub Actions Deployment" \
  --project=$PROJECT_ID
```

#### Step 5: Grant Permissions

```bash
# Service account email
SA_EMAIL="github-actions-deploy@${PROJECT_ID}.iam.gserviceaccount.com"

# Grant required roles
for ROLE in \
  "roles/run.admin" \
  "roles/storage.admin" \
  "roles/iam.serviceAccountUser" \
  "roles/secretmanager.admin" \
  "roles/cloudfunctions.admin" \
  "roles/datastore.owner" \
  "roles/artifactregistry.writer"
do
  gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="$ROLE"
done
```

#### Step 6: Allow GitHub to Impersonate Service Account

```bash
gcloud iam service-accounts add-iam-policy-binding \
  "${SA_EMAIL}" \
  --project="${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/YOUR_GITHUB_ORG/YOUR_REPO"
```

**Replace**:
- `YOUR_GITHUB_ORG` - Your GitHub organization or username
- `YOUR_REPO` - Your repository name (e.g., `save-a-recipe`)

#### Step 7: Get Workload Identity Provider Resource Name

```bash
gcloud iam workload-identity-pools providers describe "github-provider" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --format="value(name)"
```

**Copy this value** - you'll use it in the GitHub Actions workflow.

#### Step 8: Update GitHub Actions Workflow

Replace the authentication step with:

```yaml
- name: Authenticate to Google Cloud
  uses: google-github-actions/auth@v2
  with:
    workload_identity_provider: 'projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider'
    service_account: 'github-actions-deploy@kukbuk-tf.iam.gserviceaccount.com'
```

#### Step 9: Remove Old Secret

```bash
# After verifying Workload Identity works, delete the old secret:
gh secret delete GCP_SA_KEY
```

## Branch Protection Rules

Configure on GitHub: **Settings → Branches → Add rule**

### Recommended Configuration

```
Branch name pattern: main

Require:
✅ Require pull request reviews before merging (1 approval)
✅ Dismiss stale pull request approvals when new commits are pushed
✅ Require status checks to pass before merging
   - Test (unit tests)
   - Integration Test
   - Code Coverage
   - Security Scan
✅ Require branches to be up to date before merging
✅ Include administrators
✅ Restrict who can push to matching branches
```

### Why Each Rule Matters

- **PR reviews**: Prevents unreviewed code from reaching production
- **Dismiss stale approvals**: Ensures new changes are reviewed
- **Status checks**: Automated quality gates
- **Up-to-date branches**: Prevents merge conflicts and integration issues
- **Include administrators**: Ensures consistency across all users
- **Restrict push**: Enforces PR workflow

## Environment Protection

Create production environment: **Settings → Environments → New environment**

### Configuration

```
Environment name: production

Protection rules:
✅ Required reviewers (select team members)
✅ Wait timer: 0 minutes (or add delay for canary deployments)
✅ Deployment branches: main only

Environment secrets:
- SLACK_WEBHOOK (optional, for notifications)
```

### Update Workflow to Use Environment

```yaml
jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    environment: production
    # ... rest of job configuration
```

### Benefits

- **Required reviewers**: Manual approval gate before production deployment
- **Wait timer**: Time for automated checks or canary testing
- **Deployment branches**: Prevents accidental deploys from feature branches
- **Environment secrets**: Isolate production credentials

## CODEOWNERS File

Create `.github/CODEOWNERS` to define code ownership:

```
# Default owners for everything in the repo
* @your-username

# Terraform infrastructure requires DevOps approval
/terraform/ @devops-team

# GitHub workflows require CI/CD approval
/.github/workflows/ @cicd-team

# Security-sensitive files require security team approval
/terraform/main.tf @security-team @devops-team
/.github/workflows/deploy.yml @security-team @cicd-team

# Documentation can be approved by anyone
*.md @your-username
```

### Benefits

- Automatic reviewer assignment
- Required approval from domain experts
- Clear ownership and responsibility
- Prevents unauthorized changes to critical files

## Audit Logging

Enable Cloud Audit Logs for deployment tracking:

```bash
# Already enabled by default for admin activity
# Verify with:
gcloud logging read "resource.type=cloud_run_revision" \
  --limit=10 \
  --project=$PROJECT_ID

# View IAM changes
gcloud logging read "protoPayload.serviceName=\"iam.googleapis.com\"" \
  --limit=10 \
  --project=$PROJECT_ID

# View Secret Manager access
gcloud logging read "protoPayload.serviceName=\"secretmanager.googleapis.com\"" \
  --limit=10 \
  --project=$PROJECT_ID
```

### Important Events to Monitor

- IAM policy changes
- Secret access and modifications
- Cloud Run deployments
- Service account key creation (should be zero!)

## Secret Management

### Current Secrets

| Secret Name | Purpose | Rotation |
|-------------|---------|----------|
| `gemini-api-key` | Google Gemini AI API key | Manual, as needed |
| `google-oauth-id` | Google OAuth client ID | Manual, rarely |

### Best Practices

1. **Never commit secrets to git**
   ```bash
   # Add to .gitignore
   *.env
   *.credentials
   *-key.json
   secrets/
   ```

2. **Use Secret Manager for all secrets**
   ```bash
   # Create secret
   echo -n "secret-value" | gcloud secrets create SECRET_NAME \
     --data-file=- \
     --replication-policy="automatic" \
     --project=$PROJECT_ID

   # Grant access to service account
   gcloud secrets add-iam-policy-binding SECRET_NAME \
     --member="serviceAccount:cookbook-sa@kukbuk-tf.iam.gserviceaccount.com" \
     --role="roles/secretmanager.secretAccessor" \
     --project=$PROJECT_ID
   ```

3. **Rotate secrets regularly**
   - API keys: Every 90 days
   - OAuth credentials: Annually
   - Service account keys: Use Workload Identity instead

4. **Audit secret access**
   ```bash
   gcloud logging read "resource.type=\"secretmanager.googleapis.com/Secret\"" \
     --limit=20 \
     --project=$PROJECT_ID
   ```

## Security Scanning

### Dependency Scanning

**Built into Gradle**:
```bash
# Check for vulnerabilities
cd extractor && ./gradlew dependencyCheck

# Fails on CVSS >= 7.0
```

**Automated in CI/CD**:
```yaml
- name: Security vulnerability scan
  run: cd extractor && ./gradlew dependencyCheck
```

### Container Image Scanning

**Enable Container Scanning in GCR**:
```bash
gcloud services enable containerscanning.googleapis.com \
  --project=$PROJECT_ID
```

**View scan results**:
```bash
gcloud artifacts docker images scan \
  gcr.io/kukbuk-tf/cookbook:latest \
  --project=$PROJECT_ID

# View vulnerabilities
gcloud artifacts docker images list-vulnerabilities \
  gcr.io/kukbuk-tf/cookbook:latest \
  --project=$PROJECT_ID
```

### GitHub Security Features

Enable in **Settings → Security**:

- ✅ **Dependabot alerts** - Automated dependency vulnerability alerts
- ✅ **Dependabot security updates** - Automated PR for security fixes
- ✅ **Code scanning** - Static analysis with CodeQL
- ✅ **Secret scanning** - Detect committed secrets

## Least Privilege Access

### Service Account Permissions

Review and minimize permissions:

```bash
# List current permissions
gcloud projects get-iam-policy kukbuk-tf \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:cookbook-sa@kukbuk-tf.iam.gserviceaccount.com"

# Remove unnecessary roles
gcloud projects remove-iam-policy-binding kukbuk-tf \
  --member="serviceAccount:cookbook-sa@kukbuk-tf.iam.gserviceaccount.com" \
  --role="roles/UNNECESSARY_ROLE"
```

### Principle of Least Privilege

- ✅ Grant only required permissions
- ✅ Use custom roles for fine-grained control
- ✅ Regular access reviews
- ❌ Avoid project-wide editor/owner roles
- ❌ Never use project-wide service account permissions

## Implementation Checklist

### Phase 1: Setup (One-time)

- [ ] **Setup Workload Identity Federation**
  - [ ] Run setup script or manual commands above
  - [ ] Test authentication works
  - [ ] Remove old `GCP_SA_KEY` secret

- [ ] **Configure Branch Protection**
  - [ ] Add protection rules to main branch
  - [ ] Require PR reviews
  - [ ] Require status checks

- [ ] **Create CODEOWNERS file**
  - [ ] Define code ownership
  - [ ] Require reviews from owners

- [ ] **Enable Security Features**
  - [ ] Enable Dependabot
  - [ ] Enable Code Scanning
  - [ ] Enable Secret Scanning
  - [ ] Enable Container Scanning

### Phase 2: Update Workflows

- [ ] **Update `.github/workflows/deploy.yml`**
  - [ ] Add test steps (unit, integration, coverage, security)
  - [ ] Split build and push steps
  - [ ] Update authentication to Workload Identity
  - [ ] Add version commit step with retry logic
  - [ ] Move git tag creation to after deployment
  - [ ] Upgrade to `ncipollo/release-action@v1`
  - [ ] Add environment protection

- [ ] **Create `.github/workflows/pr-validation.yml`**
  - [ ] Run tests on every PR
  - [ ] Don't build/deploy, just validate
  - [ ] Report test results as PR status

### Phase 3: Documentation

- [ ] **Update deployment documentation**
  - [ ] Add Workload Identity instructions
  - [ ] Remove service account key instructions
  - [ ] Update troubleshooting section

- [ ] **Create runbooks**
  - [ ] Deployment procedures
  - [ ] Rollback procedures
  - [ ] Incident response

### Phase 4: Testing

- [ ] **Test Workload Identity authentication**
  - [ ] Manually trigger workflow
  - [ ] Verify authentication works
  - [ ] Check permissions are correct

- [ ] **Test full deployment pipeline**
  - [ ] Create test PR
  - [ ] Merge to main
  - [ ] Monitor deployment
  - [ ] Verify health checks
  - [ ] Confirm version commit
  - [ ] Verify git tag created

- [ ] **Test rollback procedures**
  - [ ] Practice each rollback method
  - [ ] Document actual time taken
  - [ ] Update procedures if needed

### Phase 5: Monitoring & Alerts

- [ ] **Set up alerts**
  - [ ] High error rate alert
  - [ ] Service down alert
  - [ ] High latency alert
  - [ ] Deployment failure alert

- [ ] **Configure notifications**
  - [ ] Slack integration
  - [ ] Email notifications
  - [ ] PagerDuty (optional)

- [ ] **Create dashboards**
  - [ ] Cloud Monitoring dashboard
  - [ ] GitHub Actions status board

### Phase 6: Go Live

- [ ] **Announce to team**
  - [ ] Share documentation
  - [ ] Explain new workflow
  - [ ] Set expectations (deployment time)

- [ ] **Monitor first few deployments**
  - [ ] Watch for issues
  - [ ] Gather feedback
  - [ ] Iterate on process

- [ ] **Schedule regular reviews**
  - [ ] Weekly deployment retrospectives
  - [ ] Monthly security reviews
  - [ ] Quarterly disaster recovery drills

## Compliance Considerations

### Data Protection

- **Firestore**: Recipe data storage
  - [ ] Enable point-in-time recovery
  - [ ] Configure backup retention
  - [ ] Document data retention policy

- **Logs**: Application and audit logs
  - [ ] Set retention period (30-90 days)
  - [ ] Configure log exports for compliance
  - [ ] Ensure PII is not logged

### Regulatory Compliance

If handling user data, consider:
- GDPR (EU users)
- CCPA (California users)
- Data encryption at rest and in transit
- User data deletion procedures
- Privacy policy and terms of service

## Disaster Recovery

### Backup Strategy

**Firestore automatic backups**:
```bash
# Configure automatic backups
gcloud firestore backups schedules create \
  --database='(default)' \
  --recurrence=daily \
  --retention=7d \
  --project=$PROJECT_ID
```

**Container image retention**:
- Keep at least 10 recent versions in GCR
- Archive older versions to Cloud Storage for cost savings

### Recovery Procedures

**Restore from Firestore backup**:
```bash
# List available backups
gcloud firestore backups list --project=$PROJECT_ID

# Restore from backup
gcloud firestore import gs://BACKUP_BUCKET/BACKUP_NAME \
  --project=$PROJECT_ID
```

**Redeploy from older version**:
See [Rollback Procedures](./rollback.md)

## Production Hardening

### Resource Limits

**Set appropriate limits in Terraform**:
```hcl
resources {
  limits = {
    cpu    = "2000m"      # 2 vCPU
    memory = "1Gi"        # 1 GB RAM
  }

  requests = {
    cpu    = "1000m"      # 1 vCPU
    memory = "512Mi"      # 512 MB RAM
  }
}
```

### Concurrency

**Configure request concurrency**:
```hcl
template {
  spec {
    container_concurrency = 80  # Max concurrent requests per instance
  }
}
```

### Autoscaling

**Set min/max instances**:
```hcl
template {
  metadata {
    annotations = {
      "autoscaling.knative.dev/minScale" = "0"   # Scale to zero
      "autoscaling.knative.dev/maxScale" = "10"  # Max 10 instances
    }
  }
}
```

### Timeout Configuration

**Request timeout**:
```hcl
template {
  spec {
    timeout_seconds = 300  # 5 minutes max per request
  }
}
```

## Security Checklist

- [ ] Workload Identity Federation enabled
- [ ] No service account keys in use
- [ ] All secrets in Secret Manager
- [ ] Branch protection rules configured
- [ ] CODEOWNERS file created
- [ ] Dependabot enabled
- [ ] Code scanning enabled
- [ ] Secret scanning enabled
- [ ] Container scanning enabled
- [ ] Audit logging configured
- [ ] Least privilege IAM policies
- [ ] Regular security reviews scheduled

## Related Documents

- [Deployment Strategy](./strategy.md)
- [Rollback Procedures](./rollback.md)
- [Monitoring & Alerts](./monitoring.md)
- [GitHub Setup Guide](../github-setup/deployment-setup.md)
- [Branch Protection Guide](../github-setup/branch-protection.md)
