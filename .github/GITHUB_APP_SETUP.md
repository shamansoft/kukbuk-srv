# GitHub App Setup for CI/CD Automation

This guide shows how to create a GitHub App that can bypass branch protection to push version bump commits and tags.

## Why GitHub App?

✅ **More secure than PAT** - Granular permissions, auditable
✅ **Can bypass branch protection** - Works where GITHUB_TOKEN fails
✅ **Scoped to specific repos** - Not tied to a personal account
✅ **Best practice** - Recommended by GitHub for automation

---

## Step 1: Create GitHub App

### 1.1 Navigate to GitHub App Settings

**Personal Account**:
- Go to: https://github.com/settings/apps
- Click **New GitHub App**

**Organization** (if applicable):
- Go to: https://github.com/organizations/shamansoft/settings/apps
- Click **New GitHub App**

### 1.2 Configure GitHub App

Fill in the form:

**GitHub App name**: `sar-ci-bot` (must be unique across GitHub)

**Homepage URL**: `https://github.com/shamansoft/kukbuk-srv`

**Webhook**:
- ☐ Uncheck "Active" (we don't need webhooks)

**Permissions** - Click "Repository permissions":
- **Contents**: `Read and write` (allows push commits and tags)
- **Metadata**: `Read-only` (automatically selected)
- **Pull requests**: `Read and write` (optional, for auto-merge)

Leave all other permissions as "No access"

**Where can this GitHub App be installed?**:
- ☑ Only on this account

Click **Create GitHub App**

### 1.3 Save App ID

After creation, you'll see:
```
App ID: 123456
```

**Save this number** - you'll need it later.

---

## Step 2: Generate Private Key

1. Scroll down to **Private keys** section
2. Click **Generate a private key**
3. A `.pem` file will download automatically
4. **Save this file securely** - you'll need it in next step

⚠️ **Important**: This key is like a password. Don't commit it to git!

---

## Step 3: Install App on Repository

1. In your GitHub App settings, click **Install App** (left sidebar)
2. Click **Install** next to your account/organization
3. Choose repository access:
   - ☑ **Only select repositories**
   - Select: `kukbuk-srv`
4. Click **Install**

---

## Step 4: Add Secrets to Repository

### 4.1 Add Private Key as Secret

```bash
# Read the private key file
cat ~/Downloads/sar-ci-bot.2025-XX-XX.private-key.pem

# Copy the entire contents including:
# -----BEGIN RSA PRIVATE KEY-----
# ...
# -----END RSA PRIVATE KEY-----
```

Add to GitHub:
```bash
# Using gh CLI
gh secret set APP_PRIVATE_KEY --repo shamansoft/kukbuk-srv
# Paste the entire .pem file contents when prompted
```

Or via UI:
1. Go to: https://github.com/shamansoft/kukbuk-srv/settings/secrets/actions
2. Click **New repository secret**
3. Name: `APP_PRIVATE_KEY`
4. Value: Paste entire contents of `.pem` file
5. Click **Add secret**

### 4.2 Add App ID as Variable

```bash
# Using gh CLI
gh variable set APP_ID --body "123456" --repo shamansoft/kukbuk-srv
```

Or via UI:
1. Go to: https://github.com/shamansoft/kukbuk-srv/settings/variables/actions
2. Click **New repository variable**
3. Name: `APP_ID`
4. Value: Your App ID (e.g., `123456`)
5. Click **Add variable**

---

## Step 5: Allow App to Bypass Branch Protection

### Option A: Via UI

1. Go to: https://github.com/shamansoft/kukbuk-srv/settings/branches
2. Click **Edit** on `main` branch protection rule
3. Find: **"Allow specified actors to bypass required pull requests"**
4. Click **Add**
5. Search for your app: `sar-ci-bot`
6. Select it from the dropdown
7. Click **Save changes**

### Option B: Via CLI

```bash
# Get your app slug (usually lowercase name with hyphens)
APP_SLUG="sar-ci-bot"

gh api \
  --method POST \
  -H "Accept: application/vnd.github+json" \
  "/repos/shamansoft/kukbuk-srv/branches/main/protection/required_pull_request_reviews" \
  -f dismiss_stale_reviews=true \
  -f require_code_owner_reviews=false \
  -f required_approving_review_count=0 \
  -f "bypass_pull_request_allowances[apps][]=$APP_SLUG"
```

---

## Step 6: Update GitHub Actions Workflow

The workflow has been updated to use the GitHub App token instead of GITHUB_TOKEN.

Key changes in `.github/workflows/deploy.yml`:

```yaml
finalize:
  steps:
    # Generate token from GitHub App
    - uses: actions/create-github-app-token@v1
      id: app-token
      with:
        app-id: ${{ vars.APP_ID }}
        private-key: ${{ secrets.APP_PRIVATE_KEY }}

    # Use app token for checkout
    - name: Checkout code
      uses: actions/checkout@v4
      with:
        token: ${{ steps.app-token.outputs.token }}
        fetch-depth: 0

    # Git operations will now use app token
    - name: Configure Git
      run: |
        git config user.name "sar-ci-bot[bot]"
        git config user.email "sar-ci-bot[bot]@users.noreply.github.com"
```

---

## Step 7: Test the Setup

### 7.1 Verify App Installation

```bash
# Check installed apps on repo
gh api /repos/shamansoft/kukbuk-srv/installation --jq '.app_slug'
# Should output: sar-ci-bot
```

### 7.2 Verify Secrets

```bash
# Check secrets exist
gh secret list --repo shamansoft/kukbuk-srv | grep APP_PRIVATE_KEY

# Check variables
gh variable list --repo shamansoft/kukbuk-srv | grep APP_ID
```

### 7.3 Trigger Workflow

```bash
# Manually trigger workflow with skip_build to test finalize step
gh workflow run deploy.yml \
  --repo shamansoft/kukbuk-srv \
  --ref main \
  -f skip_build=true \
  -f skip_deploy=true \
  -f skip_finalize=false
```

Watch the "Commit version bump" step - it should succeed!

---

## Troubleshooting

### Issue: "Resource not accessible by integration"

**Cause**: App doesn't have required permissions

**Fix**:
1. Go to app settings: https://github.com/settings/apps/sar-ci-bot
2. Check **Permissions & events**
3. Ensure **Contents** is set to **Read and write**
4. Click **Save changes**
5. Go to **Install App** and re-install

### Issue: "App is not installed on this repository"

**Fix**:
```bash
# Check installation
gh api /repos/shamansoft/kukbuk-srv/installation

# If not found, reinstall:
# Go to: https://github.com/settings/apps/sar-ci-bot
# Click "Install App" → Install on shamansoft/kukbuk-srv
```

### Issue: Still getting "Protected branch update failed"

**Cause**: App not added to bypass list

**Fix**:
1. Go to: https://github.com/shamansoft/kukbuk-srv/settings/branches
2. Edit `main` protection rule
3. Under "Allow specified actors to bypass", add your app
4. Save changes

### Issue: "Bad credentials"

**Cause**: Private key is incorrect or corrupted

**Fix**:
1. Generate new private key in app settings
2. Update `APP_PRIVATE_KEY` secret with new key
3. Ensure entire key is copied (including BEGIN/END lines)

---

## Security Notes

✅ **Private key is secret** - Never commit to git, never share
✅ **App is scoped** - Only has access to repos where installed
✅ **Auditable** - All commits show as from the app, not a user
✅ **Revocable** - Can uninstall app or regenerate key anytime

## Summary

After setup, your workflow will:
- ✅ Use GitHub App token (not GITHUB_TOKEN)
- ✅ Bypass branch protection (push version bumps)
- ✅ Create tags on protected branches
- ✅ Show commits as `sar-ci-bot[bot]`
- ✅ Maintain security and auditability

**Next**: Run the deploy workflow and verify the finalize step succeeds!
