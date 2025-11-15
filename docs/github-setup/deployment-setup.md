# GitHub Actions Deployment Setup

This document explains how to set up automated deployment using GitHub Actions.

## Overview

The deployment workflow (`.github/workflows/deploy.yml`) automatically:
1. Builds a native GraalVM image
2. Pushes the image to Google Container Registry (GCR)
3. Deploys to Cloud Run using OpenTofu/Terraform
4. Creates a GitHub release with the version tag

## Trigger

The workflow triggers on:
- **Merge to main branch** (after PR approval)
- **Manual trigger** via GitHub Actions UI (workflow_dispatch)

## Prerequisites

### 1. Google Cloud Service Account

Create a service account with the necessary permissions:

```bash
# Set your project ID
export PROJECT_ID="kukbuk-tf"

# Create service account
gcloud iam service-accounts create github-actions-deploy \
    --display-name="GitHub Actions Deployment" \
    --project=$PROJECT_ID

# Grant necessary roles
gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:github-actions-deploy@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/run.admin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:github-actions-deploy@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/storage.admin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:github-actions-deploy@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/iam.serviceAccountUser"

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:github-actions-deploy@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/secretmanager.admin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:github-actions-deploy@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/cloudfunctions.admin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:github-actions-deploy@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/datastore.owner"

# Create and download key
gcloud iam service-accounts keys create github-actions-key.json \
    --iam-account=github-actions-deploy@${PROJECT_ID}.iam.gserviceaccount.com

# Display key (copy this for GitHub secret)
cat github-actions-key.json
```

### 2. GitHub Repository Secrets

Add the following secrets to your GitHub repository:

**Go to**: Repository Settings → Secrets and variables → Actions → New repository secret

#### Required Secrets:

1. **`GCP_SA_KEY`** (Required)
   - The entire JSON content of the service account key file
   - Used for authentication with Google Cloud
   - Copy the entire content of `github-actions-key.json`

2. **`CODECOV_TOKEN`** (Optional)
   - For code coverage reporting in PR validation
   - Only needed if using Codecov

#### How to Add Secrets:

```bash
# Method 1: Via GitHub CLI
gh secret set GCP_SA_KEY < github-actions-key.json

# Method 2: Via GitHub UI
# 1. Copy the JSON content: cat github-actions-key.json | pbcopy
# 2. Go to: https://github.com/YOUR_ORG/YOUR_REPO/settings/secrets/actions
# 3. Click "New repository secret"
# 4. Name: GCP_SA_KEY
# 5. Value: Paste the JSON content
# 6. Click "Add secret"
```

### 3. Google Cloud Secrets (Secret Manager)

Ensure these secrets exist in Google Secret Manager:

```bash
# Check existing secrets
gcloud secrets list --project=$PROJECT_ID

# Create missing secrets
echo -n "YOUR_GEMINI_API_KEY" | gcloud secrets create gemini-api-key --data-file=- --project=$PROJECT_ID
echo -n "YOUR_OAUTH_CLIENT_ID" | gcloud secrets create google-oauth-id --data-file=- --project=$PROJECT_ID
```

## Workflow Details

### Build Step
- Uses the same `extractor/scripts/build.sh` script
- Builds native image with GraalVM (takes ~10-15 minutes)
- Auto-increments version number
- Pushes image to `gcr.io/kukbuk-tf/cookbook:VERSION`

### Deploy Step
- Uses OpenTofu (Terraform fork)
- Applies infrastructure changes
- Deploys new image to Cloud Run
- Updates all associated resources

### Post-Deployment
- Tests the health endpoint
- Creates a Git tag with the version
- Creates a GitHub release with deployment details

## Workflow Configuration

### Environment Variables

Edit `.github/workflows/deploy.yml` to customize:

```yaml
env:
  GCP_PROJECT_ID: kukbuk-tf          # Your GCP project ID
  GCR_REGISTRY: gcr.io/kukbuk-tf     # Your GCR registry
  SERVICE_NAME: cookbook             # Cloud Run service name
  REGION: us-west1                   # GCP region
```

### Build Memory

Native image builds require substantial memory. The workflow uses:
- Default GitHub runner: 7GB RAM
- Build memory limit: 12GB (set in build.sh call)

For faster builds, consider using larger runners:
```yaml
runs-on: ubuntu-latest-8-cores  # 8 cores, 32GB RAM
```

**Note**: Larger runners cost more. Check GitHub Actions pricing.

## Testing the Workflow

### Test Locally First

Before pushing to main, test the build locally:

```bash
cd extractor/scripts
./build.sh local --native --memory=12g
```

### Manual Trigger

You can manually trigger the deployment:

1. Go to: **Actions** → **Build and Deploy** → **Run workflow**
2. Select branch: `main`
3. Click **Run workflow**

### Monitor Progress

1. Go to: **Actions** tab in GitHub
2. Click on the running workflow
3. Expand each step to see logs
4. Native build step takes ~10-15 minutes

## Troubleshooting

### Authentication Errors

```
Error: google: could not find default credentials
```

**Solution**: Verify `GCP_SA_KEY` secret is correctly set with valid JSON.

### Build Timeout

```
Error: The job running on runner has exceeded the maximum execution time of 360 minutes.
```

**Solution**:
- Use larger runner with more cores
- Consider building JVM image instead of native for faster builds
- Check if build is stuck (increase --debug flag)

### Terraform Errors

```
Error: Error creating Service: googleapi: Error 403: Permission denied
```

**Solution**: Verify service account has all required roles (see Prerequisites).

### Image Not Found

```
Error: Image 'gcr.io/kukbuk-tf/cookbook:X.Y.Z' not found
```

**Solution**:
- Check build step completed successfully
- Verify GCR authentication worked
- Check `PROJECT_ID` in `build.sh` matches workflow config

### Version Conflicts

```
Error: Tag 'v0.0.55' already exists
```

**Solution**:
- Delete the existing tag: `git push --delete origin v0.0.55`
- Manually increment version in `extractor/build.gradle.kts`

## Cost Considerations

### GitHub Actions

Native builds use significant compute time:
- ~10-15 minutes per build
- Standard runner: Included in free tier (2000 minutes/month for public repos)
- Larger runners: Additional cost ($0.008/minute for 8-core)

### Google Cloud

Deployment costs (monthly estimates):
- Cloud Run: ~$5-20 depending on traffic
- Container Registry: ~$0.26/GB storage
- Secret Manager: ~$0.06/secret/month
- Cloud Functions: ~$0-5 depending on usage

## Security Best Practices

1. **Never commit service account keys** to the repository
2. **Rotate service account keys** periodically (every 90 days)
3. **Use minimum required permissions** for service accounts
4. **Enable branch protection** on main branch
5. **Require PR reviews** before merging
6. **Use Dependabot** to keep dependencies updated

## Rollback Procedure

If deployment fails or has issues:

### Method 1: Revert via GitHub

```bash
# Revert the problematic commit
git revert <commit-hash>
git push origin main
# This triggers a new deployment with the reverted code
```

### Method 2: Manual Rollback via gcloud

```bash
# List revisions
gcloud run revisions list --service=cookbook --region=us-west1

# Roll back to specific revision
gcloud run services update-traffic cookbook \
    --to-revisions=cookbook-00042-abc=100 \
    --region=us-west1
```

### Method 3: Redeploy Previous Version via Terraform

```bash
# Locally deploy a previous version
cd terraform
make deploy TAG=0.0.50  # Use a known good version
```

## Monitoring

### View Deployment Status

```bash
# Via gcloud
gcloud run services describe cookbook --region=us-west1

# Via GitHub Actions
# Check the Actions tab in your repository

# Via Terraform
cd terraform && make output
```

### View Logs

```bash
# Cloud Run logs
gcloud run services logs read cookbook --region=us-west1 --limit=100

# Build logs
# Check GitHub Actions workflow logs
```

## Notifications

To get notified about deployment status:

1. **GitHub Email Notifications**: Settings → Notifications → Actions
2. **Slack Integration**: Install GitHub app in Slack
3. **Custom Webhooks**: Add notification steps to workflow

Example Slack notification step:

```yaml
- name: Notify Slack
  if: always()
  uses: 8398a7/action-slack@v3
  with:
    status: ${{ job.status }}
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

## Next Steps

1. ✅ Set up GCP service account
2. ✅ Add `GCP_SA_KEY` secret to GitHub
3. ✅ Verify GCP secrets exist in Secret Manager
4. ✅ Test workflow with manual trigger
5. ✅ Merge a PR to test automated deployment
6. ✅ Monitor the first deployment
7. ✅ Set up notifications (optional)
8. ✅ Configure branch protection rules

## Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Google Cloud Run Documentation](https://cloud.google.com/run/docs)
- [OpenTofu Documentation](https://opentofu.org/docs/)
- [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/)
