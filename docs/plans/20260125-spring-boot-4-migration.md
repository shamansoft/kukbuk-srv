# Plan: Spring Boot 4 Migration

## Overview
Migrate the sar-srv cookbook application from Spring Boot 3.5.9 to Spring Boot 4.0, including:
- Update to Java 25 toolchain with Spring Framework 7.x
- Migrate to modular starters (spring-boot-starter-webmvc)
- Upgrade Jackson 2.18.2 → Jackson 3.x (new group IDs, annotations, properties)
- Update GraalVM build tools for GraalVM 25+ native image support
- Replace deprecated @MockBean/@SpyBean with @MockitoBean/@MockitoSpyBean
- Fix javax.annotation.PostConstruct → jakarta.annotation.PostConstruct
- Verify Google Cloud library compatibility (Firestore, Firebase, Storage)
- Test native image compilation and deployment to GCP Cloud Run

This migration modernizes the application while maintaining GraalVM native image production builds.

## Context (from discovery)

**Current Setup:**
- Spring Boot: 3.5.9
- Java: 21 (toolchain 25 for builds)
- GraalVM Build Tools: 0.10.5
- Jackson: 2.18.2
- Spring Dependency Management Plugin: 1.1.7
- Testing: JUnit 5, Mockito 5.18.0, Testcontainers 1.19.3, WireMock 3.3.1
- Google Cloud: Firestore 3.33.4, Firebase Admin 9.7.0, Cloud Storage 2.60.0

**Key Spring Features Used:**
- RestClient (Spring Boot 3.2+ blocking HTTP client)
- Virtual threads (enabled via spring.threads.virtual.enabled)
- Spring Boot Actuator (health, httpexchanges, custom endpoints)
- Custom security filter (FirebaseAuthFilter extends OncePerRequestFilter)
- Conditional beans (FirestoreConfig, FirebaseConfig)
- Multi-module Gradle project (extractor → cookbook, recipe-sdk)

**GraalVM Native Image:**
- Production builds use GraalVM native compilation (10-15 min build)
- Reflection config for Recipe models and Gemini request classes
- SLF4J Simple chosen for native image compatibility
- Deployed to GCP Cloud Run (us-west1 region)

**Files/Components Involved:**
- `gradle/libs.versions.toml` - Version catalog (Spring Boot, Jackson, GraalVM)
- `extractor/build.gradle.kts` - Cookbook module build configuration
- `recipe-sdk/build.gradle.kts` - SDK library (no Spring Boot)
- `extractor/src/main/java/net/shamansoft/cookbook/` - Application code
  - `controller/` - REST endpoints
  - `service/` - Business logic and external API clients
  - `repository/` - Firestore data access
  - `security/FirebaseAuthFilter.java` - Authentication filter
  - `config/` - Spring configuration classes
- `extractor/src/main/resources/` - Configuration and prompts
  - `application.yaml` - Default config
  - `application-gcp.yaml` - GCP profile
  - `reflect-config.json` - GraalVM reflection metadata
- `extractor/src/test/java/` - Unit tests
- `extractor/src/intTest/java/` - Integration tests (Testcontainers + WireMock)
- `extractor/scripts/build.sh` - Docker image builder with native image support
- `.github/workflows/deploy.yml` - CI/CD deployment pipeline (4 phases)

**Related Patterns Discovered:**
- Layered architecture: Controllers → Services → Repositories/Clients
- Conditional bean loading: @ConditionalOnProperty for Firestore/Firebase
- RestClient bean configuration: Multiple RestClient instances (geminiRestClient, authRestClient, driveRestClient)
- Integration testing: Separate source set with Testcontainers
- Native image preparation: Resource autodetection, SLF4J initialization at build time

**Dependencies:**
- CI/CD pipeline depends on successful native image build
- GCP deployment requires GraalVM 25+ for Spring Boot 4
- Integration tests require Docker for Testcontainers

## Development Approach
- **Testing approach**: TDD (tests first) - Update test configurations and fix test failures BEFORE migrating application code
- Complete each task fully before moving to the next
- Make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - tests are not optional - they are a required part of the checklist
  - write unit tests for new functions/methods
  - write unit tests for modified functions/methods
  - add new test cases for new code paths
  - update existing test cases if behavior changes
  - tests cover both success and error scenarios
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- Run tests after each change
- Maintain backward compatibility where possible (API contracts, Firestore schema)

## Testing Strategy
- **Unit tests**: Required for every task involving code changes
  - Update Mockito annotations (@MockBean → @MockitoBean)
  - Test all new Spring Boot 4 configurations
  - Verify RestClient beans still work
  - Test conditional beans (Firestore, Firebase)
- **Integration tests**: Required for testing external integrations
  - Verify Testcontainers compatibility with Spring Boot 4
  - Test WireMock mocks for Gemini API, Google Auth, Drive API
  - Full Spring context testing with @SpringBootTest
- **Native image build**: Required before declaring migration complete
  - Build with GraalVM 25+
  - Verify reflection configs still work
  - Test startup time and memory footprint
  - Validate GCP Cloud Run deployment
- **Regression testing**: Validate existing functionality
  - Recipe extraction from HTML via Gemini AI
  - Firestore CRUD operations
  - Google Drive integration
  - Firebase authentication
  - Actuator endpoints

## Progress Tracking
- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix
- Update plan if implementation deviates from original scope
- Keep plan in sync with actual work done

## What Goes Where
- **Implementation Steps** (`[ ]` checkboxes): tasks achievable within this codebase - code changes, tests, documentation updates
- **Post-Completion** (no checkboxes): items requiring external action - manual testing, GCP deployment verification, performance monitoring, team communication

---

## Deprecation Audit Findings (Task 1)

**Build Status**: Clean build successful with Spring Boot 3.5.9

**Deprecation Warnings Found**:
1. javax.annotation.PostConstruct usage:
   - File: `extractor/src/main/java/net/shamansoft/cookbook/service/gemini/GeminiClient.java:13`
   - Action Required: Replace `import javax.annotation.PostConstruct` with `import jakarta.annotation.PostConstruct`
   - Impact: Low - Single file, simple import change

2. Testing Annotations:
   - Status: ✅ No @MockBean/@SpyBean usage found
   - Tests use plain @Mock from Mockito (no Spring Boot test annotations)
   - Action Required: None for existing code, will use @MockitoBean/@MockitoSpyBean for future tests

3. Compiler Warnings:
   - Unchecked operations in StorageService.java (not a deprecation)
   - Unchecked operations in StorageServiceTest.java (not a deprecation)
   - Action Required: None - these are generic type warnings, not deprecations

**Migration Impact**: Minimal - only 1 import statement needs updating

---

## 📋 Table of Contents

### Phase 1: Pre-Migration Preparation
- [x] 1. Audit current Spring Boot 3.5.9 deprecation warnings
  - [x] 1.1 Run build with deprecation warnings enabled
  - [x] 1.2 Document all deprecation warnings in this plan
  - [x] 1.3 Verify javax.annotation.PostConstruct usage (1 file)
- [x] 2. Create feature branch for migration
  - [x] 2.1 Create branch: `spring-4` (equivalent to spring-boot-4-migration)
  - [x] 2.2 Document rollback strategy in this plan (lines 396-421)

### Phase 2: Dependency Version Updates
- [ ] 3. Update Spring Boot and core dependencies in version catalog
  - [ ] 3.1 Update Spring Boot: 3.5.9 → 4.0.x (latest stable)
  - [ ] 3.2 Update Spring Dependency Management: 1.1.7 → 1.2.x
  - [ ] 3.3 Update GraalVM Build Tools: 0.10.5 → 0.11.x (for GraalVM 25+)
  - [ ] 3.4 Update Jackson: 2.18.2 → 3.x.x (latest Jackson 3)
  - [ ] 3.5 Verify Google Cloud library versions (Firestore, Firebase, Storage)
  - [ ] 3.6 Sync Gradle wrapper and refresh dependencies
  - [ ] 3.7 Run `./gradlew dependencies` - document conflicts

### Phase 3: Modular Starter Migration
- [ ] 4. Replace spring-boot-starter-web with spring-boot-starter-webmvc
  - [ ] 4.1 Update extractor/build.gradle.kts starter dependency
  - [ ] 4.2 Verify Tomcat embedded dependency still included
  - [ ] 4.3 Check for missing transitive dependencies
  - [ ] 4.4 Update build and resolve any compilation errors

### Phase 4: Jackson 3 Migration
- [ ] 5. Update Jackson group IDs and package names
  - [ ] 5.1 Update build.gradle.kts: com.fasterxml.jackson → tools.jackson
  - [ ] 5.2 Search and replace imports in all Java files
  - [ ] 5.3 Update Jackson property names in application.yaml files
  - [ ] 5.4 Run build and fix compilation errors
- [ ] 6. Update Jackson annotations and serialization code
  - [ ] 6.1 Replace @JsonComponent → @JacksonComponent (if used)
  - [ ] 6.2 Replace JsonObjectSerializer → ObjectValueSerializer (if used)
  - [ ] 6.3 Replace JsonValueDeserializer → ObjectValueDeserializer (if used)
  - [ ] 6.4 Test JSON serialization/deserialization with Gemini API requests
  - [ ] 6.5 Write tests for Recipe YAML serialization (recipe-sdk)
  - [ ] 6.6 Write tests for RecipeDto JSON serialization (cookbook)
  - [ ] 6.7 Run tests - must pass before Phase 5

### Phase 5: Testing Framework Migration (TDD - Tests First)
- [ ] 7. Update test dependencies for Spring Boot 4
  - [ ] 7.1 Add spring-boot-resttestclient test dependency
  - [ ] 7.2 Add spring-boot-restclient test dependency
  - [ ] 7.3 Verify Mockito, JUnit 5, Testcontainers versions
  - [ ] 7.4 Run `./gradlew test` - document failures
- [ ] 8. Migrate unit test annotations and configurations
  - [ ] 8.1 Replace @MockBean → @MockitoBean in all test files
  - [ ] 8.2 Replace @SpyBean → @MockitoSpyBean in all test files
  - [ ] 8.3 Add @AutoConfigureMockMvc to tests using MockMVC
  - [ ] 8.4 Add @AutoConfigureRestTestClient to tests using RestTestClient
  - [ ] 8.5 Update @PropertyMapping imports (package relocation)
  - [ ] 8.6 Run `./gradlew :cookbook:test` - fix failures
  - [ ] 8.7 Write tests for updated test configurations
  - [ ] 8.8 All unit tests must pass before Phase 6
- [ ] 9. Migrate integration tests
  - [ ] 9.1 Verify Testcontainers 1.19.3 compatibility with Spring Boot 4
  - [ ] 9.2 Update WireMock test configurations if needed
  - [ ] 9.3 Test Gemini API mock behavior
  - [ ] 9.4 Test Google Auth mock behavior
  - [ ] 9.5 Test Drive API mock behavior
  - [ ] 9.6 Run `./gradlew :cookbook:intTest` - fix failures
  - [ ] 9.7 Write tests for integration test configurations
  - [ ] 9.8 All integration tests must pass before Phase 6

### Phase 6: Application Code Migration
- [ ] 10. Fix deprecated javax.annotation imports
  - [ ] 10.1 Update GeminiClient.java: javax.annotation.PostConstruct → jakarta.annotation.PostConstruct
  - [ ] 10.2 Search for other javax.annotation usages
  - [ ] 10.3 Write tests for @PostConstruct bean initialization
  - [ ] 10.4 Run tests - must pass
- [ ] 11. Update package relocations
  - [ ] 11.1 Check for BootstrapRegistry usage (org.springframework.boot → org.springframework.boot.bootstrap)
  - [ ] 11.2 Check for EnvironmentPostProcessor usage (org.springframework.boot.env → org.springframework.boot)
  - [ ] 11.3 Update spring.factories files if present
  - [ ] 11.4 Write tests for custom EnvironmentPostProcessor if present
  - [ ] 11.5 Run tests - must pass
- [ ] 12. Update Spring configuration properties
  - [ ] 12.1 Review application.yaml for Jackson property changes
  - [ ] 12.2 Review application-gcp.yaml for property changes
  - [ ] 12.3 Verify spring.threads.virtual.enabled still supported
  - [ ] 12.4 Check Actuator health probe defaults (now enabled by default)
  - [ ] 12.5 Write tests for configuration property bindings
  - [ ] 12.6 Run tests - must pass
- [ ] 13. Verify RestClient configuration
  - [ ] 13.1 Test geminiRestClient bean initialization
  - [ ] 13.2 Test authRestClient bean initialization
  - [ ] 13.3 Test driveRestClient bean initialization
  - [ ] 13.4 Test uploadRestClient bean initialization
  - [ ] 13.5 Test genericRestClient bean initialization
  - [ ] 13.6 Write tests for RestClient configurations (success + error cases)
  - [ ] 13.7 Run tests - must pass
- [ ] 14. Verify conditional beans and custom configurations
  - [ ] 14.1 Test FirestoreConfig conditional bean loading
  - [ ] 14.2 Test FirebaseConfig conditional bean loading
  - [ ] 14.3 Test WebConfig CORS configuration
  - [ ] 14.4 Test ServiceConfig Actuator beans
  - [ ] 14.5 Write tests for conditional bean scenarios
  - [ ] 14.6 Run tests - must pass
- [ ] 15. Update security filter for Spring Boot 4
  - [ ] 15.1 Verify FirebaseAuthFilter extends OncePerRequestFilter
  - [ ] 15.2 Test authentication filter with valid token
  - [ ] 15.3 Test authentication filter with invalid token
  - [ ] 15.4 Test public path exclusions (/actuator/**)
  - [ ] 15.5 Write tests for filter chain integration
  - [ ] 15.6 Run tests - must pass

### Phase 7: GraalVM Native Image Update
- [ ] 16. Update GraalVM build configuration
  - [ ] 16.1 Update graalvmNative block in extractor/build.gradle.kts
  - [ ] 16.2 Verify reflection config files still valid
  - [ ] 16.3 Update SLF4J initialization settings if needed
  - [ ] 16.4 Test resource autodetection
  - [ ] 16.5 Write tests for native image configuration validation
  - [ ] 16.6 Run tests - must pass
- [ ] 17. Test native image compilation (JVM build first)
  - [ ] 17.1 Run `./gradlew :cookbook:build` - must succeed
  - [ ] 17.2 Run `./gradlew :cookbook:bootRun` locally - verify startup
  - [ ] 17.3 Test recipe extraction endpoint locally
  - [ ] 17.4 Test Firestore CRUD operations locally
  - [ ] 17.5 Test Actuator endpoints locally
  - [ ] 17.6 All JVM tests must pass before native build
- [ ] 18. Build and test GraalVM 25+ native image
  - [ ] 18.1 Run `./gradlew :cookbook:nativeCompile` (expect 10-15 min)
  - [ ] 18.2 Verify native image binary created
  - [ ] 18.3 Run native image locally with test config
  - [ ] 18.4 Measure startup time (expect <1 second)
  - [ ] 18.5 Measure memory footprint (expect ~200MB)
  - [ ] 18.6 Test recipe extraction with native image
  - [ ] 18.7 Test Firestore operations with native image
  - [ ] 18.8 Native image tests must pass before Docker build

### Phase 8: Docker and Deployment
- [ ] 19. Update Docker build scripts for GraalVM 25+
  - [ ] 19.1 Review extractor/scripts/build.sh for GraalVM version references
  - [ ] 19.2 Update Dockerfile base image if needed
  - [ ] 19.3 Build Docker image with native flag: `./build.sh --native`
  - [ ] 19.4 Test Docker image locally with test config
  - [ ] 19.5 Verify environment variable passing (COOKBOOK_GEMINI_API_KEY)
  - [ ] 19.6 Docker build must succeed before GCP deployment
- [ ] 20. Update CI/CD pipeline for Spring Boot 4
  - [ ] 20.1 Review .github/workflows/deploy.yml for Java version
  - [ ] 20.2 Verify GraalVM 25+ available in GitHub Actions
  - [ ] 20.3 Update workflow if needed for new dependencies
  - [ ] 20.4 Test workflow in feature branch (manual trigger)
  - [ ] 20.5 Verify all 4 phases pass (Test, Build, Deploy, Finalize)

### Phase 9: Final Verification
- [ ] 21. Run full test suite
  - [ ] 21.1 Run `./gradlew test` - all unit tests must pass
  - [ ] 21.2 Run `./gradlew :cookbook:intTest` - all integration tests must pass
  - [ ] 21.3 Run `./gradlew :cookbook:checkCoverage` - verify 40% coverage maintained
  - [ ] 21.4 Run `./gradlew :cookbook:dependencyCheck` - verify no high-severity vulnerabilities
  - [ ] 21.5 All tests and checks must pass
- [ ] 22. Verify native image and Docker image
  - [ ] 22.1 Build native image: `./gradlew :cookbook:nativeCompile`
  - [ ] 22.2 Build Docker image: `cd extractor/scripts && ./build.sh --native`
  - [ ] 22.3 Run Docker container locally and test all endpoints
  - [ ] 22.4 Verify startup time, memory usage, and response times
  - [ ] 22.5 Compare metrics to Spring Boot 3.5.9 baseline
- [ ] 23. Document migration changes
  - [ ] 23.1 Update extractor/README.md with Spring Boot 4 notes
  - [ ] 23.2 Update CLAUDE.md with new versions
  - [ ] 23.3 Document any breaking changes or new patterns
  - [ ] 23.4 Update deployment documentation if needed

### Phase 10: Merge and Deploy
- [ ] 24. Prepare for merge
  - [ ] 24.1 Rebase feature branch on main
  - [ ] 24.2 Resolve any conflicts
  - [ ] 24.3 Run full test suite again
  - [ ] 24.4 Update version in extractor/build.gradle.kts (bump minor version)
  - [ ] 24.5 Create PR with detailed description
- [ ] 25. Monitor deployment
  - [ ] 25.1 Merge PR to main (triggers automated deployment)
  - [ ] 25.2 Monitor GitHub Actions deployment pipeline
  - [ ] 25.3 Verify Cloud Run deployment succeeds
  - [ ] 25.4 Check Cloud Run logs for errors
  - [ ] 25.5 Migration complete when deployment succeeds

---

## Technical Details

### Spring Boot 4 Key Changes

**Minimum Requirements:**
- Java 17+ (using Java 25 ✅)
- GraalVM 25+ for native images (upgrade required)
- Jakarta EE 11 baseline with Servlet 6.1
- Spring Framework 7.x

**Modular Starters:**
- `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- Smaller, focused JARs for better modularity
- Convention: `spring-boot-<technology>` modules, `org.springframework.boot.<technology>` packages

**Jackson 3 Upgrade:**
- Group IDs: `com.fasterxml.jackson` → `tools.jackson`
- Annotations: `@JsonComponent` → `@JacksonComponent`, `@JsonMixin` → `@JacksonMixin`
- Class renames: `JsonObjectSerializer` → `ObjectValueSerializer`, `JsonValueDeserializer` → `ObjectValueDeserializer`
- Properties: `spring.jackson.read.*` → `spring.jackson.json.read.*`, `spring.jackson.write.*` → `spring.jackson.json.write.*`

**Testing Changes:**
- `@MockBean`/`@SpyBean` deprecated → `@MockitoBean`/`@MockitoSpyBean`
- `@SpringBootTest` no longer provides MockMVC automatically → add `@AutoConfigureMockMvc`
- TestRestTemplate/WebClient require `@AutoConfigureTestRestTemplate` or `@AutoConfigureRestTestClient`
- `@PropertyMapping` relocated: `org.springframework.boot.test.autoconfigure.properties` → `org.springframework.boot.test.context`

**Package Relocations:**
- `BootstrapRegistry`: `org.springframework.boot` → `org.springframework.boot.bootstrap`
- `EnvironmentPostProcessor`: `org.springframework.boot.env` → `org.springframework.boot`

**Actuator Changes:**
- Health probes (liveness/readiness) now enabled by default
- Disable if unnecessary: `management.endpoint.health.probes.enabled=false`

**Nullability Annotations:**
- Spring Boot now uses JSpecify annotations (`@org.jspecify.annotations.Nullable`)
- Migrate from `org.springframework.lang` package

### GraalVM Native Image

**Current Configuration:**
```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("cookbook")
            resources.autodetect()
            buildArgs.add("--initialize-at-build-time=org.slf4j")
        }
    }
}
```

**Reflection Configs:**
- `extractor/src/main/resources/META-INF/native-image/reflect-config.json` - Gemini API classes, Recipe model
- `recipe-sdk/src/main/resources/META-INF/native-image/net.shamansoft.recipe/recipe-sdk/reflect-config.json` - Recipe model classes

**Expected Metrics (Spring Boot 4 + GraalVM 25):**
- Startup time: <1 second (compared to ~5 seconds JVM)
- Memory footprint: ~200MB (compared to ~500MB JVM)
- Build time: 10-15 minutes (similar to current)
- Docker image size: ~100-150MB (similar to current)

### Rollback Strategy

**If migration fails or issues discovered:**

1. **Immediate rollback** (during feature branch testing):
   - Switch back to main branch
   - Discard spring-boot-4-migration branch if needed
   - No impact on production

2. **Post-merge rollback** (if deployed to production):
   - Revert merge commit on main branch
   - Trigger deployment pipeline to redeploy Spring Boot 3.5.9 version
   - Cloud Run will roll back to previous revision
   - Estimated rollback time: 15-20 minutes

3. **Partial rollback** (if specific feature broken):
   - Fix issue in new branch
   - Create hotfix PR
   - Deploy fix via normal pipeline

**Rollback decision criteria:**
- Native image build fails repeatedly (>3 attempts)
- Performance regression >20% (startup time, memory, latency)
- Critical bug affecting recipe extraction or Firestore operations
- Google Cloud library incompatibility causing data loss

---

## Post-Completion

*Items requiring manual intervention or external systems - no checkboxes, informational only*

### Manual Verification

**Production Smoke Testing** (after deployment to GCP Cloud Run):
- Test recipe extraction from real HTML pages (5-10 samples)
- Verify Firestore CRUD operations in production database
- Test Google Drive integration with real OAuth tokens
- Verify Firebase authentication with real user tokens
- Check Actuator endpoints (health, metrics) in production
- Monitor Cloud Run logs for errors or warnings
- Compare production metrics to Spring Boot 3.5.9 baseline:
  - Startup time
  - Memory usage
  - Request latency (p50, p95, p99)
  - Cold start time
  - Error rate

**Performance Baseline Comparison:**
- Document Spring Boot 3.5.9 metrics before migration
- Compare Spring Boot 4 metrics after migration
- Acceptable ranges:
  - Startup time: ±20%
  - Memory usage: ±10%
  - Request latency: ±10%
  - Cold start time: ±20%
- If degradation exceeds thresholds, investigate and optimize

### External System Updates

**Team Communication:**
- Notify team of Spring Boot 4 migration completion
- Share migration lessons learned (document in this plan or separate doc)
- Update team documentation with new best practices

**Monitoring and Alerting:**
- Verify GCP Cloud Monitoring dashboards still work
- Update alert thresholds if needed (memory, latency)
- Monitor for 48 hours post-deployment for anomalies

**Dependency Updates for Consuming Projects:**
- If other services depend on cookbook API, verify compatibility
- Check if recipe-sdk consumers need updates (unlikely, no Spring Boot dependency)

**Documentation Updates:**
- Update public API documentation if breaking changes
- Update deployment runbooks if process changed
- Share migration guide with team for future reference

---

## Notes

- This migration is significant but low-risk due to TDD approach and feature branch testing
- Most breaking changes are in testing framework (already addressed in Phase 5)
- Application code changes are minimal (javax → jakarta, package relocations)
- Jackson 3 migration is the most impactful change (group IDs, annotations, properties)
- GraalVM 25+ update is required for Spring Boot 4 but should be straightforward
- Native image build time remains the bottleneck (10-15 min) but unchanged
- CI/CD pipeline should require minimal changes (Java version already 25)
- Rollback strategy is well-defined and fast (<20 min if needed)

**Estimated Total Effort:**
- Phase 1-4: 2-4 hours (dependency updates, starter migration)
- Phase 5: 4-6 hours (testing framework migration, TDD)
- Phase 6: 2-4 hours (application code updates)
- Phase 7-8: 4-6 hours (GraalVM, Docker, CI/CD)
- Phase 9-10: 2-4 hours (verification, deployment)
- **Total: 14-24 hours** (spread over 3-5 days for thorough testing)

**Migration Confidence Level: High**
- Java version already compatible (Java 25)
- Jakarta EE packages already used
- RestClient is Spring Boot 4 native feature
- Virtual threads support continues
- Testing framework migration well-documented
- GraalVM native image support enhanced in Spring Boot 4
