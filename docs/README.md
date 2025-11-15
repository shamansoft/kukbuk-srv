# Documentation Index

This directory contains comprehensive documentation for the Save-a-Recipe server project.

## Quick Start

New to the project? Start here:
1. Read the [main README](../README.md) for project overview
2. Review [CI/CD Workflow](./CI_CD_WORKFLOW.md) to understand the development process
3. Check [GitHub Setup](./github-setup/) for configuring your development environment

## Documentation Structure

### 🚀 CI/CD & Development Workflow

**[CI_CD_WORKFLOW.md](./CI_CD_WORKFLOW.md)**
- Complete guide to the CI/CD pipeline
- Version management strategy
- Pull request workflow
- GitHub Actions integration
- **When to use**: Understanding the full development workflow

**[TESTING_WORKFLOW.md](./TESTING_WORKFLOW.md)**
- Testing CI/CD changes on feature branches
- Safe testing practices
- Rollback procedures for test changes
- **When to use**: Before making changes to GitHub Actions workflows

### 📦 Deployment

**[deployment/strategy.md](./deployment/strategy.md)**
- Comprehensive deployment strategy
- Automated deployment workflow phases
- Build and push separation
- Version management
- **When to use**: Understanding how deployments work

**[deployment/rollback.md](./deployment/rollback.md)**
- Emergency rollback procedures
- Git revert vs manual rollback
- Gradual traffic shifting
- Database schema considerations
- **When to use**: Production issues requiring rollback

**[deployment/monitoring.md](./deployment/monitoring.md)**
- Health checks and logging
- Cloud Run metrics
- Alert configuration (planned)
- Cost monitoring
- **When to use**: Monitoring production service health

**[deployment/production-readiness.md](./deployment/production-readiness.md)**
- Security improvements (Workload Identity Federation)
- Branch protection rules
- Implementation checklist
- Secret management
- **When to use**: Hardening the deployment pipeline for production

### ⚙️ GitHub Setup

**[github-setup/github-app.md](./github-setup/github-app.md)**
- Creating a GitHub App for CI/CD automation
- App permissions configuration
- Installation and authentication
- **When to use**: Setting up automated version commits

**[github-setup/branch-protection.md](./github-setup/branch-protection.md)**
- Comprehensive branch protection setup
- Required status checks
- Review requirements
- **When to use**: Configuring repository protection rules

**[github-setup/deployment-setup.md](./github-setup/deployment-setup.md)**
- GitHub Actions deployment configuration
- Workload Identity Federation setup
- Secret management
- **When to use**: Initial GitHub Actions setup

### 📝 Feature Planning

**[store.md](./store.md)**
- Firestore recipe storage implementation
- Database schema design
- Active feature development epic
- **When to use**: Understanding recipe storage architecture

## Common Tasks

### Deploying to Production
1. Create PR with your changes
2. Ensure tests pass (see [CI_CD_WORKFLOW.md](./CI_CD_WORKFLOW.md))
3. Get PR approved
4. Merge to `main`
5. Deployment happens automatically

### Rolling Back a Deployment
1. Check [deployment/rollback.md](./deployment/rollback.md)
2. Choose appropriate method (git revert, manual, or terraform)
3. Follow documented procedure

### Setting Up CI/CD
1. Follow [github-setup/deployment-setup.md](./github-setup/deployment-setup.md)
2. Configure [Workload Identity](./deployment/production-readiness.md#workload-identity-federation-recommended)
3. Set up [branch protection](./github-setup/branch-protection.md)

### Testing Workflow Changes
1. Follow [TESTING_WORKFLOW.md](./TESTING_WORKFLOW.md)
2. Use feature branches with workflow_dispatch
3. Test thoroughly before merging

## Additional Resources

### Module Documentation
- [extractor/README.md](../extractor/README.md) - Java backend service documentation
- [recipe-sdk/README.md](../recipe-sdk/README.md) - Recipe SDK library
- [terraform/README.md](../terraform/README.md) - Infrastructure as code
- [extractor/scripts/README.md](../extractor/scripts/README.md) - Legacy deployment scripts

### Infrastructure
- [terraform/firestore-schema.md](../terraform/firestore-schema.md) - Firestore database schema

### Configuration
- [CLAUDE.md](../CLAUDE.md) - Claude Code AI assistant instructions
- [README.md](../README.md) - Main project README

## Workflow Files

Active GitHub Actions workflows:
- `.github/workflows/pr-validation.yml` - PR testing and validation
- `.github/workflows/deploy.yml` - Production deployment
- `.github/workflows/prod-ready.md` - Production readiness guide

## Documentation Standards

When creating or updating documentation:

### ✅ Do
- Keep docs concise and actionable
- Include code examples
- Add "When to use" sections
- Cross-reference related docs
- Update index when adding new docs
- Use clear headings and structure

### ❌ Don't
- Write documentation without examples
- Create duplicate content across files
- Leave outdated information
- Write docs longer than necessary
- Forget to update related docs

## Need Help?

1. **Can't find what you need?** Check the [main README](../README.md)
2. **Workflow issues?** See [CI_CD_WORKFLOW.md](./CI_CD_WORKFLOW.md)
3. **Deployment problems?** Check [deployment/rollback.md](./deployment/rollback.md)
4. **Infrastructure questions?** See [terraform/README.md](../terraform/README.md)

## Recent Changes

This documentation structure was consolidated on 2025-11-14 to improve clarity and organization. Previous scattered documentation has been:
- Consolidated into logical groups
- Updated to reflect current practices (Firestore vs Google Drive)
- Split large documents into focused guides
- Organized with clear navigation

## Contributing to Documentation

When updating documentation:
1. Keep it current with code changes
2. Update this index if adding new docs
3. Remove outdated information
4. Test all commands and examples
5. Get documentation reviewed with code PRs
