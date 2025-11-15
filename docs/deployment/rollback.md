# Rollback Procedures

This document describes how to roll back a deployment when issues are discovered in production.

## Quick Reference

| Scenario | Method | Time | Risk |
|----------|--------|------|------|
| Code bug found immediately | Git revert | 15-20 min | Low |
| Critical security issue | GCloud rollback | < 1 min | Low |
| Need specific old version | Terraform redeploy | 3-5 min | Medium |
| Database schema change | Manual investigation | Varies | High |

## Method 1: Revert via Git (Preferred)

**When to use**: Code defect discovered after deployment

### Steps

```bash
# Find the problematic commit
git log --oneline -10

# Revert the commit (creates new commit)
git revert <commit-hash>

# Push to trigger new deployment
git push origin main
```

### Advantages
- ✅ Full audit trail
- ✅ Automatic deployment via CI/CD
- ✅ Tests run before rollback deploys
- ✅ Git history shows what was reverted and why

### Disadvantages
- ⚠️ Takes 15-20 minutes (full build and deploy)
- ⚠️ Not suitable for critical emergencies

### Example

```bash
# Scenario: Version 0.6.5 introduced a bug
$ git log --oneline -5
a1b2c3d (HEAD -> main, tag: v0.6.5) feat: add new recipe parser
e4f5g6h (tag: v0.6.4) fix: handle missing ingredients
i7j8k9l (tag: v0.6.3) chore: update dependencies

# Revert the problematic commit
$ git revert a1b2c3d

# Edit the commit message to explain why
# Default message: "Revert "feat: add new recipe parser""
# Update to: "Revert "feat: add new recipe parser" - causes NPE in production"

# Push to trigger deployment
$ git push origin main

# CI/CD will:
# 1. Run all tests
# 2. Build version 0.6.6
# 3. Deploy to Cloud Run
# 4. Tag as v0.6.6
```

## Method 2: Manual GCloud Rollback (Emergency)

**When to use**: Critical production issue requiring immediate rollback

### Steps

```bash
# 1. List recent revisions
gcloud run revisions list \
  --service=cookbook \
  --region=us-west1 \
  --project=kukbuk-tf \
  --limit=10

# Output example:
# REVISION              ACTIVE  SERVICE   DEPLOYED
# cookbook-v0-6-5       yes     cookbook  2025-11-14 10:30:00
# cookbook-v0-6-4       no      cookbook  2025-11-13 15:20:00
# cookbook-v0-6-3       no      cookbook  2025-11-12 09:15:00

# 2. Roll back to specific revision
gcloud run services update-traffic cookbook \
  --to-revisions=cookbook-v0-6-4=100 \
  --region=us-west1 \
  --project=kukbuk-tf

# 3. Verify the rollback
gcloud run services describe cookbook \
  --region=us-west1 \
  --project=kukbuk-tf \
  --format='get(status.traffic)'
```

### Advantages
- ✅ Immediate (< 1 minute)
- ✅ No build required
- ✅ Previous revision already tested
- ✅ Zero downtime

### Disadvantages
- ⚠️ Manual operation (not automated)
- ⚠️ Doesn't update git (main branch still has bad code)
- ⚠️ Next deployment will redeploy the bad version
- ⚠️ Need to follow up with git revert

### After Emergency Rollback

**IMPORTANT**: After using this method, you MUST follow up with Method 1 (Git revert) to prevent redeploying the bad code:

```bash
# After emergency rollback, immediately revert in git
git revert <bad-commit-hash>
git push origin main
```

## Method 3: Redeploy Previous Version via Terraform

**When to use**: Need to deploy a specific older version

### Steps

```bash
# 1. Check which versions are available in GCR
gcloud container images list-tags gcr.io/kukbuk-tf/cookbook

# Output example:
# TAGS        DIGEST        TIMESTAMP
# 0.6.5       sha256:abc... 2025-11-14T10:30:00
# 0.6.4       sha256:def... 2025-11-13T15:20:00
# 0.6.3       sha256:ghi... 2025-11-12T09:15:00

# 2. Ensure the desired image exists
gcloud container images describe gcr.io/kukbuk-tf/cookbook:0.6.4

# 3. Deploy specific version with Terraform
cd terraform
tofu init
tofu apply -var="image_tag=0.6.4" -auto-approve

# 4. Verify deployment
gcloud run services describe cookbook \
  --region=us-west1 \
  --project=kukbuk-tf \
  --format='value(spec.template.spec.containers[0].image)'
```

### Advantages
- ✅ Full control over which version to deploy
- ✅ Uses infrastructure-as-code
- ✅ Faster than full build (3-5 minutes)
- ✅ Can deploy any tagged version

### Disadvantages
- ⚠️ Manual operation
- ⚠️ Requires image to still exist in GCR
- ⚠️ Need to follow up with git changes
- ⚠️ Medium risk if deploying very old versions

### After Terraform Rollback

Similar to Method 2, you should update git to match production:

```bash
# Option A: Revert to match the deployed version
git revert <commit-range>
git push origin main

# Option B: If going back multiple versions, create a fix PR
git checkout -b hotfix/rollback-to-0.6.4
# Make necessary changes
git commit -m "fix: rollback to stable version 0.6.4"
git push origin hotfix/rollback-to-0.6.4
# Create PR and merge
```

## Gradual Traffic Shifting (Advanced)

For less risky rollbacks, you can gradually shift traffic between versions.

### Split Traffic Between Versions

```bash
# Send 90% traffic to old version, 10% to new version
gcloud run services update-traffic cookbook \
  --to-revisions=cookbook-v0-6-4=90,cookbook-v0-6-5=10 \
  --region=us-west1 \
  --project=kukbuk-tf

# Monitor metrics for 10-15 minutes

# If issues resolved, gradually increase old version traffic
gcloud run services update-traffic cookbook \
  --to-revisions=cookbook-v0-6-4=100 \
  --region=us-west1 \
  --project=kukbuk-tf
```

### Advantages
- ✅ Minimizes user impact
- ✅ Allows comparison of error rates
- ✅ Can test fix with small percentage
- ✅ Easy to adjust percentages

### Use Cases
- Testing a hotfix before full deployment
- Reducing impact of discovered issues
- A/B testing different versions

## Database Schema Changes

**Special Considerations**: Rolling back code with database schema changes requires careful planning.

### Safe Rollback Strategy

1. **Always make schema changes backward-compatible**
   - Add new columns as nullable
   - Don't remove columns until old code is gone
   - Use feature flags for new functionality

2. **Rollback with schema changes**
   ```bash
   # 1. Roll back application first (Methods 1, 2, or 3)
   # 2. Verify old code works with new schema
   # 3. If needed, manually revert schema changes
   ```

3. **If schema is incompatible**
   - DO NOT use automatic rollback
   - Create emergency hotfix PR instead
   - May need to manually fix data

### Example: Adding a Column

**Safe approach:**
```java
// Version 0.6.5 - Add nullable column
ALTER TABLE recipes ADD COLUMN prep_time INTEGER NULL;

// Deploy code that uses prep_time (optional)
// Can roll back safely - old code ignores new column
```

**Unsafe approach:**
```java
// Version 0.6.5 - Add required column
ALTER TABLE recipes ADD COLUMN prep_time INTEGER NOT NULL;

// Deploy code that requires prep_time
// CANNOT roll back - old code doesn't set prep_time!
```

## Monitoring After Rollback

After any rollback, monitor the following:

### Immediate Checks (First 5 minutes)

```bash
# 1. Health endpoint
curl https://YOUR_SERVICE_URL/actuator/health

# 2. Recent logs
gcloud run services logs read cookbook \
  --region=us-west1 \
  --project=kukbuk-tf \
  --limit=50

# 3. Error rate
gcloud logging read "resource.type=cloud_run_revision AND severity>=ERROR" \
  --project=kukbuk-tf \
  --limit=20 \
  --format=json
```

### Extended Monitoring (First 30 minutes)

- Monitor error rates in Cloud Console
- Check application metrics
- Review user reports/feedback
- Verify all endpoints working

### Post-Rollback Actions

1. **Document the incident**
   - What went wrong
   - Which rollback method was used
   - How long it took
   - Lessons learned

2. **Create follow-up tasks**
   - Fix the bug in a PR
   - Add tests to prevent regression
   - Update deployment procedures if needed

3. **Communicate with team**
   - Notify stakeholders of rollback
   - Explain root cause
   - Share timeline for fix

## Rollback Checklist

Use this checklist when performing a rollback:

### Before Rollback
- [ ] Identify the problematic version/commit
- [ ] Verify previous version is stable
- [ ] Choose appropriate rollback method
- [ ] Notify team of impending rollback

### During Rollback
- [ ] Execute rollback procedure
- [ ] Monitor deployment progress
- [ ] Verify health checks pass
- [ ] Check error logs

### After Rollback
- [ ] Confirm service is stable
- [ ] Update git if using Methods 2 or 3
- [ ] Document the incident
- [ ] Create fix PR
- [ ] Update runbooks if needed

## Common Issues and Solutions

### Issue: Old revision not available

**Problem**: The revision you want to roll back to was deleted.

**Solution**: Use Method 3 (Terraform) if the image still exists in GCR, or deploy from an older git tag:

```bash
# Check if image exists
gcloud container images list-tags gcr.io/kukbuk-tf/cookbook

# If exists, use Terraform
cd terraform && tofu apply -var="image_tag=0.6.3"

# If not, rebuild from git tag
git checkout v0.6.3
cd extractor/scripts && ./build.sh 0.6.3 --native
./push.sh 0.6.3
cd ../../terraform && tofu apply -var="image_tag=0.6.3"
```

### Issue: Health checks fail after rollback

**Problem**: Rolled back version is also unhealthy.

**Solution**: Issue may be environmental, not code-related:

```bash
# Check Cloud Run configuration
gcloud run services describe cookbook --region=us-west1

# Check secrets are accessible
gcloud secrets versions access latest --secret=gemini-api-key

# Check service account permissions
gcloud projects get-iam-policy kukbuk-tf \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:cookbook-sa@kukbuk-tf.iam.gserviceaccount.com"
```

### Issue: Database is in inconsistent state

**Problem**: Schema changes prevent rollback.

**Solution**: Create emergency hotfix:

```bash
# Create hotfix branch
git checkout -b hotfix/emergency-fix

# Make minimal fix to restore functionality
# ... code changes ...

# Fast-track deployment
git commit -m "hotfix: emergency database fix"
git push origin hotfix/emergency-fix

# Create PR and merge immediately
# CI/CD will deploy
```

## Related Documents

- [Deployment Strategy](./strategy.md)
- [Monitoring & Alerts](./monitoring.md)
- [Production Readiness](./production-readiness.md)
