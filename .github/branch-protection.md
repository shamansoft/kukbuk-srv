# Branch Protection Configuration

To enable automatic PR merge blocking when tests fail, configure the following branch protection rules for the `main` branch in GitHub:

## Required Settings

1. **Go to Repository Settings → Branches → Add rule**

2. **Branch name pattern**: `main`

3. **Enable the following settings**:
   - ✅ **Require a pull request before merging**
     - ✅ Require approvals: 1
     - ✅ Dismiss stale PR approvals when new commits are pushed
     - ✅ Require review from code owners (if CODEOWNERS file exists)
   
   - ✅ **Require status checks to pass before merging**
     - ✅ Require branches to be up to date before merging
     - **Required status checks**:
       - `test` (GitHub Actions job)
       - `security-scan` (GitHub Actions job)
   
   - ✅ **Require conversation resolution before merging**
   
   - ✅ **Restrict pushes that create files larger than 100 MB**

## GitHub CLI Command

Alternatively, you can set this up via GitHub CLI:

```bash
gh api repos/:owner/:repo/branches/main/protection \
  --method PUT \
  --field required_status_checks='{"strict":true,"checks":[{"context":"test"},{"context":"security-scan"}]}' \
  --field enforce_admins=true \
  --field required_pull_request_reviews='{"required_approving_review_count":1,"dismiss_stale_reviews":true}' \
  --field restrictions=null
```

## What This Does

- **Blocks PR merging** if any tests fail
- **Requires code review** before merging
- **Ensures branch is up-to-date** with main before merging
- **Runs security scans** on all PRs
- **Enforces rules for all users** including admins

## Secrets Required

Add these secrets to your GitHub repository:

- `CODECOV_TOKEN` - For code coverage reporting (optional but recommended)