# Branch Protection Setup Guide

This guide shows how to protect the `main` branch from direct pushes and deletion, while still allowing GitHub Actions to commit version bumps.

## Quick Setup (CLI)

The fastest way is to use the provided script:

```bash
# Make sure gh CLI is installed
brew install gh

# Authenticate if needed
gh auth login

# Run the setup script
./.github/scripts/setup-branch-protection.sh
```

This script will configure all protection rules automatically.

---

## Manual Setup (GitHub UI)

If you prefer to configure manually through the GitHub UI, follow these detailed steps:

### Step 1: Navigate to Branch Settings

1. Go to https://github.com/shamansoft/kukbuk-srv
2. Click the **Settings** tab (top right, next to Insights)
3. In the left sidebar, scroll down to **Code and automation** section
4. Click **Branches**

### Step 2: Add Branch Protection Rule

1. Click the green **Add branch protection rule** button
   - If a rule already exists for `main`, click **Edit** instead
2. In **Branch name pattern** field, enter: `main`

### Step 3: Configure Protection Settings

Scroll through the settings and configure as follows:

#### ✅ Require a pull request before merging

**Check this box**, then configure sub-options:

- **Required number of approvals before merging**: Set to `0`
  - ℹ️ Since you're the only developer, 0 is fine
  - Set to 1 if you want to force self-review habit

- ☑️ **Dismiss stale pull request approvals when new commits are pushed**
  - Check this box

- ☐ **Require review from Code Owners**
  - Leave unchecked (unless you have CODEOWNERS file)

- ☐ **Restrict who can dismiss pull request reviews**
  - Leave unchecked

- ☑️ **Allow specified actors to bypass required pull requests**
  - Leave unchecked (we want to block everyone, including you)

#### ✅ Require status checks to pass before merging

**Check this box**, then configure:

- ☑️ **Require branches to be up to date before merging**
  - Check this box

- **Search for status checks**: Click in the search box
  - Type: `Test & Validate`
  - Click the suggestion that appears to add it
  - ⚠️ **DO NOT** add "Build & Push Native Image"
    - This only runs AFTER merge, not during PR

- ℹ️ If you don't see "Test & Validate" yet:
  - You need to run the pr-validation workflow at least once
  - Merge your `ci` branch first, then come back to add this check
  - Leave this section blank for now if needed

#### ✅ Require conversation resolution before merging

**Check this box**
- Ensures all PR comments are resolved before merge

#### ☐ Require signed commits

**Leave unchecked** (unless you want to set up GPG signing)

#### ☐ Require linear history

**Optional - Check if you want clean history**
- Prevents merge commits
- Forces squash or rebase merges only
- ✅ Recommended for cleaner git history

#### ☐ Require deployments to succeed before merging

**Leave unchecked** (deployment happens after merge)

#### ✅ Do not allow bypassing the above settings

**Check this box** - This is CRITICAL!
- When checked: Even admins (you) cannot push directly
- When unchecked: Admins can bypass with --force
- ✅ **Must check to protect from yourself**

#### ☐ Restrict who can push to matching branches

**Leave UNCHECKED** - This is KEY!
- When unchecked: GitHub Actions can push (version bump commits work)
- When checked: GitHub Actions would be blocked from pushing
- ℹ️ This is why the version bump commit will still work

#### ✅ Allow force pushes

**Leave UNCHECKED** (default)
- Prevents force pushes to main

#### Specify who can force push

**Leave EMPTY** (we don't want anyone to force push)

#### ✅ Allow deletions

**Leave UNCHECKED** (default)
- Prevents branch deletion

### Step 4: Save Protection Rule

1. Scroll to the bottom
2. Click the green **Create** button (or **Save changes** if editing)

### Step 5: Verify Configuration

You should see a summary like:

```
Branch protection rule: main
✓ Require a pull request before merging
✓ Require status checks to pass before merging
  - Status checks: Test & Validate
✓ Require conversation resolution before merging
✓ Require linear history (if you enabled it)
✓ Do not allow bypassing the above settings
✓ Restrict deletions
```

---

## Testing the Protection

### Test 1: Try Direct Push (Should FAIL)

```bash
# On main branch
git checkout main
git pull
echo "test" >> README.md
git add README.md
git commit -m "test: direct push"
git push origin main
```

**Expected result**:
```
remote: error: GH006: Protected branch update failed for refs/heads/main.
remote: error: Changes must be made through a pull request.
```

✅ Protection is working!

### Test 2: Try Branch Deletion (Should FAIL)

```bash
git push origin --delete main
```

**Expected result**:
```
remote: error: GH006: Protected branch update failed for refs/heads/main.
remote: error: Cannot delete this protected branch.
```

✅ Deletion protection is working!

### Test 3: GitHub Actions Push (Should SUCCEED)

1. Trigger the deploy workflow with `skip_build=true`
2. Watch the "Commit version bump" step
3. It should successfully push to the branch

✅ GitHub Actions can still commit!

---

## Workflow After Protection is Enabled

### Normal Development Flow

1. **Create feature branch**:
   ```bash
   git checkout -b feature/my-feature
   # make changes
   git push origin feature/my-feature
   ```

2. **Create Pull Request**:
   ```bash
   gh pr create --base main --head feature/my-feature
   ```
   Or use GitHub UI

3. **Wait for CI checks**:
   - "Test & Validate" must pass
   - All conversations must be resolved

4. **Merge PR**:
   - Click "Squash and merge" on GitHub
   - Or use: `gh pr merge --squash`

5. **Automatic deployment**:
   - Merge to main triggers deploy.yml
   - Build, push, deploy, and version bump happen automatically
   - GitHub Actions pushes version bump commit to main ✅

### Emergency Bypass (If Really Needed)

If you absolutely need to bypass protection:

1. Go to Settings > Branches
2. Click **Edit** on main protection rule
3. **Uncheck** "Do not allow bypassing the above settings"
4. Save changes
5. Make your emergency push
6. **Re-enable** "Do not allow bypassing" immediately
7. Save changes again

⚠️ **Use this only in true emergencies!**

---

## Troubleshooting

### Issue: Can't add "Test & Validate" status check

**Cause**: The check hasn't run yet, so GitHub doesn't know about it.

**Solution**:
1. Merge your current `ci` branch (or create a test PR)
2. Wait for pr-validation.yml to run
3. Then edit branch protection and add the check

**Workaround**: Skip this check for now, enable it later after first PR.

---

### Issue: GitHub Actions can't push version bump

**Error**:
```
refusing to allow a GitHub App to create or update workflow
```

**Cause**: "Restrict who can push" is enabled, blocking GitHub Actions.

**Solution**: Ensure "Restrict who can push to matching branches" is UNCHECKED.

---

### Issue: I need to test on main without PR

**Solution**: Use a different branch for testing:
```bash
# Create test branch from main
git checkout -b test-main main
git push origin test-main

# Test on test-main (no protection)
# When ready, create PR: test-main -> main
```

---

## Summary

With branch protection enabled:

| Action | You (Admin) | GitHub Actions | Result |
|--------|-------------|----------------|--------|
| Direct push to main | ❌ Blocked | ✅ Allowed | Must use PR |
| Force push to main | ❌ Blocked | ❌ Blocked | Never allowed |
| Delete main branch | ❌ Blocked | ❌ Blocked | Never allowed |
| Merge PR to main | ✅ Allowed | ✅ Allowed | After checks pass |
| Version bump commit | ❌ Blocked | ✅ Allowed | Automated by CI |

This gives you:
- ✅ Protection from accidental direct pushes
- ✅ Enforced code review process (via PRs)
- ✅ Required CI checks before merge
- ✅ Automated version management still works
- ✅ Clean, linear git history
- ✅ Protection from branch deletion

**Best practice**: Leave protection enabled at all times. Force yourself to use PRs - it's a good habit even for solo projects!
