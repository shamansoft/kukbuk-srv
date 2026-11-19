# SAR Server — Coding Agent Guide

Read [AGENT_GUIDE.md](AGENT_GUIDE.md) in full before changing code. It preserves
the complete architecture, configuration, deployment, development rules, and documentation
index. Start at `STATUS.md` for current project state.

Spring Boot 4 / Java 25 multi-module Gradle backend for Save-A-Recipe.
The `extractor/` directory is Gradle module `:cookbook`; `recipe-sdk/` is `:recipe-sdk`.
Run commands from the checkout root, including when working in a worktree:

```sh
./gradlew build
./gradlew :cookbook:test
./gradlew :cookbook:intTest
./gradlew :cookbook:checkCoverage
```

- Follow Controllers → Services → Repositories/Clients and existing validation/security.
- Preserve entitlement/quota enforcement, Firebase authentication, and secure secrets handling.
- Read the guide's GraalVM reflection guidance before changing native-image behavior.
- Features described in the full guide may be branch-specific; check the active code.
- Deployment uses GitHub Actions and the sibling sar-infra repository. Follow the
  guide's deployment and branch-protection rules; merging/deploying is separate work.

The detailed guide was moved out of this entrypoint to avoid instruction truncation.

## Product context

Read `../AGENTS.md` when the sibling SAR umbrella exists. It describes the product,
shared contracts, and cross-repository documentation. A standalone checkout may lack it.
