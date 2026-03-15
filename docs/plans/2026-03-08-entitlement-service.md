# Entitlement Service — Quota Management MVP

## Overview

Implement a per-user quota system with a credits fallback, enforced via AOP, backed by Firestore.

**Problem solved:** Prevent abuse on the FREE tier (unlimited extraction today). Enable future monetisation with PRO/ENTERPRISE tiers.

**Key benefits:**
- Configurable daily limits per tier/operation (no redeploy to change limits)
- Zero DB reads for paid users (JWT tier claim fast-path)
- Fails open on Firestore timeout (`CIRCUIT_OPEN`) — service availability does not depend on quota DB
- Clean `@CheckEntitlement` annotation seam: trivial to swap Firestore for Redis later

**Integration points:** `RecipeService` (RECIPE_EXTRACTION for both HTML and custom-description), `YouTubeController` (YOUTUBE_EXTRACTION), `FirebaseAuthFilter` (tier claim extraction), `CookbookExceptionHandler` (429 error), `UserProfile` (tier + credits fields).

**RFC:** `docs/rfc/entitlement.md` (Draft v4, reviewed)

---

## Context (from discovery)

- Files to create: `extractor/src/main/java/net/shamansoft/cookbook/entitlement/` (new package)
- Files to modify:
  - `extractor/src/main/java/net/shamansoft/cookbook/repository/firestore/model/UserProfile.java`
  - `extractor/src/main/java/net/shamansoft/cookbook/security/FirebaseAuthFilter.java`
  - `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java`
  - `extractor/src/main/java/net/shamansoft/cookbook/controller/YouTubeController.java`
  - `extractor/src/main/java/net/shamansoft/cookbook/CookbookExceptionHandler.java`
  - `extractor/src/main/resources/application.yaml`
  - `extractor/src/main/resources/META-INF/native-image/reflect-config.json`
  - `terraform/firestore.tf`
- GraalVM native config: `proxy-config.json` (new)

---

## Development Approach

- **Testing approach**: Regular (code first, then tests before moving to next task)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**
- Run `./gradlew :cookbook:test` after each task

---

## Testing Strategy

- **Unit tests**: required for every task
- **Integration tests** (`./gradlew :cookbook:intTest`): run after full wiring is complete (Task 7)
- **Coverage**: maintain ≥ 40% minimum enforced by JaCoCo

---

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix

---

## Implementation Steps

### Task 1: Domain model (enums + records)

- [x] create `entitlement/` package under `net.shamansoft.cookbook.entitlement`
- [x] create `Operation.java` enum: `RECIPE_EXTRACTION`, `YOUTUBE_EXTRACTION`
  - Note: `RECIPE_EXTRACTION` covers both HTML and custom-description operations (product decision, see RFC §2.4)
- [x] create `UserTier.java` enum: `FREE`, `PRO`, `ENTERPRISE`
- [x] create `EntitlementOutcome.java` enum: `ALLOWED_PAID`, `ALLOWED_FREE_QUOTA`, `ALLOWED_CREDIT`, `DENIED_QUOTA`, `CIRCUIT_OPEN`
- [x] create `EntitlementResult.java` record with `allowed`, `outcome`, `remainingQuota`, `Integer remainingCredits` (nullable — null for paid users), `Instant resetsAt` (nullable — null for unlimited/circuit-open); add static factory methods `paid()` and `circuitOpen()`
- [x] create `QuotaWindow.java` record with `userId`, `operation`, `windowKey`, `count`, `limit`, `resetAt`, `withinLimit`
- [x] write tests for `EntitlementResult` factory methods and field accessors
- [x] write tests for `QuotaWindow` record construction
- [x] run tests — must pass before task 2

### Task 2: EntitlementRepository interface + plan config + application.yaml

- [x] create `EntitlementRepository.java` interface with `checkAndIncrement()`, `deductCredit()`, `updateTierAndCredits()` as defined in RFC §4.2
- [x] create `EntitlementPlanConfig.java` `@ConfigurationProperties(prefix = "entitlement")` record with `plans`, `window`, `timeouts` nested records; add `dailyLimit()` helper and `@PostConstruct validate()` that asserts every `UserTier × Operation` pair has an explicit config entry (throws `IllegalStateException` on startup if missing — turns silent zero-quota bug into a loud startup failure)
  - Note: existing project pattern uses `@Component + @ConfigurationProperties` for classes. Since `EntitlementPlanConfig` is a record (can't use `@Component`), add `@EnableConfigurationProperties(EntitlementPlanConfig.class)` to a new `EntitlementConfig.java` `@Configuration` class (or an existing config class)
- [x] add `entitlement.plans` / `entitlement.window` / `entitlement.timeouts` block to `application.yaml` (FREE: 5 recipe/day, 1 YouTube/day; PRO/ENTERPRISE: -1 unlimited) — must include ALL tiers × operations to pass startup validation
- [x] write tests for `EntitlementPlanConfig.dailyLimit()` covering: FREE RECIPE (5), FREE YOUTUBE (1), PRO RECIPE (-1 unlimited), missing key defaults to 0
- [x] write test for `validate()`: complete config passes, missing entry throws `IllegalStateException`
- [x] run tests — must pass before task 3

### Task 3: FirestoreEntitlementRepository

- [x] create `FirestoreEntitlementRepository.java` `@Repository @Primary` implementing `EntitlementRepository`
- [x] implement `checkAndIncrement()`: deterministic doc ID `{userId}::{operation}::{YYYYMMDD}`, Firestore transaction with explicit `currentCount + 1` (not `FieldValue.increment`), set `expireAt` = resetAt + 2 days, wrap in `CompletableFuture.supplyAsync(EXECUTOR).orTimeout(800ms)`
  - ⚠️ **CIRCUIT_OPEN decision point**: RFC has an inconsistency. The repo code uses `.exceptionally()` to catch `TimeoutException` and return a sentinel `QuotaWindow(withinLimit=true)`, which means the future completes normally — the service's `try/catch` on `.get()` never fires, so `CIRCUIT_OPEN` is unreachable. But RFC §2.3 explicitly says timeout → `CIRCUIT_OPEN`. **Recommended resolution**: drop `exceptionally` from `checkAndIncrement`; let `TimeoutException` propagate to the service so its `catch` block emits `CIRCUIT_OPEN`. This matches the stated intent.
- [x] implement `deductCredit()`: transaction reads `credits`, skips if ≤ 0, uses `FieldValue.increment(-1)` in `tx.update()`, `orTimeout(800ms)`, returns `false` on timeout (`.exceptionally` here is correct — deduct failure maps to `false`, not `CIRCUIT_OPEN`)
- [x] implement `updateTierAndCredits()`: simple Firestore update on `users/{userId}`, `runAsync(EXECUTOR)`
- [x] write tests (`FirestoreEntitlementRepositoryTest`) for `checkAndIncrement`: within quota (first request), at limit (denied), timeout propagates exception
- [x] write tests for `deductCredit`: credit available, no credits, timeout returns false
- [x] write tests for `updateTierAndCredits`: verifies Firestore fields written
- [x] run tests — must pass before task 4

### Task 4: EntitlementService

- [ ] create `EntitlementService.java` `@Service @Slf4j @RequiredArgsConstructor` with constructor-injected `EntitlementRepository`, `UserProfileRepository`, `EntitlementPlanConfig`, `MeterRegistry`, `Clock` (`Clock` bean already provided by `ClockConfig.java` — no new config needed)
- [ ] implement `check(userId, tierHint, operation)` following RFC §4.5 flow:
  1. if `tierHint != null` → skip profile load, set `tier = tierHint`
  2. else → load profile via `userProfileRepository.findByUserId(userId).orTimeout(300ms)`, default to `UserTier.FREE` on empty/timeout
  3. if `tier != UserTier.FREE` → `record(ALLOWED_PAID)`, return `EntitlementResult.paid()`
  4. call `checkAndIncrement()` — catches exception → `CIRCUIT_OPEN`
  5. if `window.withinLimit()` → `ALLOWED_FREE_QUOTA`
  6. else credits fast-path using already-loaded profile snapshot → `ALLOWED_CREDIT` or `DENIED_QUOTA`
- [ ] add `private void record(Operation, EntitlementOutcome)` for tagged `meterRegistry.counter("entitlement.check", ...)` emission
- [ ] ensure `UserProfile` is loaded at most once per call; paid users (non-null `tierHint`) skip DB entirely
- [ ] ensure `resetsAt` from `QuotaWindow.resetAt()` is propagated into every `EntitlementResult` where applicable; `paid()` and `circuitOpen()` factory methods return null for `resetsAt`
- [ ] write tests (`EntitlementServiceTest`) for all 5 outcomes: `ALLOWED_PAID` (tierHint=PRO, no profile load, `resetsAt=null`), `ALLOWED_FREE_QUOTA` (tierHint=null, quota available, `resetsAt` non-null), `ALLOWED_CREDIT` (quota exhausted + profile shows credits > 0 + deduct succeeds), `DENIED_QUOTA` (quota exhausted + no credits in profile, `resetsAt` from window), `CIRCUIT_OPEN` (repo throws exception, `resetsAt=null`)
- [ ] write test verifying `UserProfileRepository.findByUserId()` is NOT called when `tierHint != null`
- [ ] write test for Micrometer counter called with correct tags for each outcome
- [ ] run tests — must pass before task 5

### Task 5: AOP — CheckEntitlement annotation + EntitlementAspect + EntitlementException

- [ ] create `CheckEntitlement.java` annotation: `@Retention(RUNTIME) @Target(METHOD)`, single `Operation value()` attribute
- [ ] create `EntitlementException.java` `RuntimeException` carrying `EntitlementResult`; add `getResult()` accessor
- [ ] create `EntitlementAspect.java` `@Aspect @Component`; `@Around("@annotation(checkEntitlement)")`; read `userId` + `userTier` from `RequestContextHolder` (set by `FirebaseAuthFilter`); call `entitlementService.check()`; throw `EntitlementException` if `!result.allowed()`; store `entitlementResult` as request attribute for header advice
- [ ] add `@EnableAspectJAutoProxy` to a `@Configuration` class if not already present (Spring Boot autoconfigures AOP via `spring-boot-starter-aop` — verify the dependency is in `build.gradle.kts`)
- [ ] write tests (`EntitlementAspectTest`): userId=null → `IllegalStateException`, denied → throws `EntitlementException`, allowed → `pjp.proceed()` called + result stored as request attribute
- [ ] run tests — must pass before task 6

### Task 6: Exception handling + response headers

- [ ] create `entitlement/dto/QuotaErrorResponse.java` record: `error` (String), `remainingQuota` (int), `remainingCredits` (Integer, nullable), `resetsAt` (Instant, nullable — when the window resets)
- [ ] add `@ExceptionHandler(EntitlementException.class)` to `CookbookExceptionHandler` returning `ResponseEntity<QuotaErrorResponse>` with **HTTP 429 Too Many Requests** (not 403); include `Retry-After` header (seconds until `resetsAt`) when `resetsAt` is non-null
- [ ] create `entitlement/EntitlementResponseAdvice.java` `@ControllerAdvice` implementing `ResponseBodyAdvice<Object>`; reads `entitlementResult` request attribute (set by `EntitlementAspect`); appends `X-Quota-Remaining`, `X-Quota-Outcome`, `X-Credits-Remaining`, `X-Quota-Resets-At` response headers; no-ops if attribute absent; omits headers for null fields (paid users, circuit-open)
- [ ] write tests for exception handler: **429 status** (not 403), `Retry-After` header present when `resetsAt` non-null, JSON body has `error="QUOTA_EXCEEDED"`, correct `remainingQuota`, `remainingCredits` (null for paid), `resetsAt`
- [ ] write tests for response advice: all headers present on `ALLOWED_FREE_QUOTA` outcome including `X-Quota-Resets-At`, `X-Quota-Outcome: CIRCUIT_OPEN` with `X-Quota-Remaining: -1` for that outcome, `X-Credits-Remaining` absent for paid users (`remainingCredits == null`), no headers appended when attribute absent
- [ ] run tests — must pass before task 7

### Task 7: Wire @CheckEntitlement into existing services

- [ ] add `@CheckEntitlement(Operation.RECIPE_EXTRACTION)` to `RecipeService.createRecipe()`
- [ ] add `@CheckEntitlement(Operation.RECIPE_EXTRACTION)` to `RecipeService.createRecipeFromDescription()`
- [ ] add `@CheckEntitlement(Operation.YOUTUBE_EXTRACTION)` to the YouTube **service** method that creates the job — RFC §2.1 says "annotation on service methods" (zero controller changes); if YouTube job creation lives only in `YouTubeController`, place the annotation on the controller method but add a note that it should be extracted to a service method for consistency
- [ ] update `RecipeService` tests to mock `EntitlementService` where needed (stub to return `ALLOWED_PAID` to avoid quota interference in non-entitlement tests)
- [ ] run unit tests — must pass
- [ ] run integration tests (`./gradlew :cookbook:intTest`) — must pass before task 8

### Task 8: UserProfile model + FirebaseAuthFilter tier extraction

- [ ] add `UserTier tier` and `int credits` fields to `UserProfile` record
- [ ] update `UserProfile.fromMap()` to read `tier` with `Optional.ofNullable((String) data.get("tier")).map(UserTier::valueOf).orElse(UserTier.FREE)` and `credits` with null-safe default `0` (cast `Long` → `int`)
- [ ] update `UserProfile.toMap()` to serialize `tier` (as `tier.name()` string) and `credits` (only write if non-default, or always write for consistency — match existing field pattern)
- [ ] add tier claim extraction to `FirebaseAuthFilter.doFilterInternal()`: read `decodedToken.getClaims().get("tier")`, parse to `UserTier`, `request.setAttribute("userTier", ...)`, log warn and skip attribute on unknown value
- [ ] update `UserProfileTest` to cover: `fromMap()` with tier present, tier absent (default FREE), invalid tier string; `toMap()` includes tier and credits
- [ ] update `FirebaseAuthFilterTest` to cover: tier claim present+valid, tier claim absent (attribute not set), tier claim invalid string (logs warn, attribute absent)
- [ ] run tests — must pass before task 9

### Task 9: GraalVM native configuration

- [ ] add 6 entries to `reflect-config.json` for: `EntitlementResult`, `QuotaErrorResponse`, `QuotaWindow`, `Operation`, `UserTier`, `EntitlementOutcome` (all with `allDeclaredFields/Methods/Constructors`)
- [ ] create `proxy-config.json` at `extractor/src/main/resources/META-INF/native-image/proxy-config.json`; add JDK-proxy entry for `RecipeService` (if it implements an interface) or document CGLIB note
- [ ] update `NativeImageConfigurationTest` (or `NativeImageConfigTest`) to assert new reflect-config entries are present
- [ ] run tests — must pass before task 10

### Task 10: Terraform — Firestore TTL + composite index

- [ ] add `google_firestore_field.quota_windows_ttl` resource to `terraform/firestore.tf` (TTL on `expireAt` field of `quota_windows` collection)
- [ ] add `google_firestore_index.quota_windows_user_op` composite index (`userId` ASC, `operation` ASC, `windowKey` DESC) to `terraform/firestore.tf`
- [ ] run `terraform validate` in `terraform/` to confirm HCL is syntactically correct

### Task 11: Verify acceptance criteria

- [ ] run full test suite: `./gradlew :cookbook:test` — all pass
- [ ] run integration tests: `./gradlew :cookbook:intTest` — all pass
- [ ] run coverage check: `./gradlew :cookbook:checkCoverage` — ≥ 40% passes
- [ ] verify all 5 `EntitlementOutcome` values are covered in tests
- [ ] verify `UserProfile` tier defaults work for users with no `tier` field in Firestore
- [ ] verify `FirebaseAuthFilter` tier claim extraction tested for present/absent/invalid

---

## Technical Details

### New package structure

```
extractor/src/main/java/net/shamansoft/cookbook/entitlement/
├── Operation.java                        (enum)
├── UserTier.java                         (enum)
├── EntitlementOutcome.java               (enum)
├── EntitlementResult.java                (record)
├── QuotaWindow.java                      (record)
├── EntitlementRepository.java            (interface)
├── FirestoreEntitlementRepository.java   (@Repository @Primary)
├── EntitlementPlanConfig.java            (@ConfigurationProperties)
├── EntitlementService.java               (@Service)
├── CheckEntitlement.java                 (annotation)
├── EntitlementAspect.java                (@Aspect @Component)
├── EntitlementException.java             (RuntimeException)
├── EntitlementResponseAdvice.java        (@ControllerAdvice implements ResponseBodyAdvice — adds X-Quota-* headers on success responses)
└── dto/
    └── QuotaErrorResponse.java           (record)

extractor/src/test/java/net/shamansoft/cookbook/entitlement/
├── EntitlementServiceTest.java           (all 5 outcomes + Micrometer)
├── FirestoreEntitlementRepositoryTest.java (checkAndIncrement, deductCredit, updateTierAndCredits)
└── EntitlementAspectTest.java            (userId=null, denied, allowed)
```

**AOP dependency**: verify `spring-boot-starter-aop` is in `extractor/build.gradle.kts`; add it if missing.

### Firestore data model

| Collection | Doc ID format | Key fields |
|---|---|---|
| `quota_windows` | `{userId}::{operation}::{YYYYMMDD}` | `count`, `limit`, `resetAt`, `expireAt` |
| `users` | `{userId}` | `tier` (new), `credits` (new) |

### Response headers

| Header | Value |
|---|---|
| `X-Quota-Remaining` | remaining daily quota (`-1` = unlimited); omitted for paid/circuit-open |
| `X-Quota-Outcome` | `EntitlementOutcome` name |
| `X-Credits-Remaining` | remaining credits; omitted for paid users (`remainingCredits == null`) |
| `X-Quota-Resets-At` | ISO-8601 UTC reset time; omitted for paid/unlimited/circuit-open |

### Error response (429)

HTTP 429 Too Many Requests — correct status for quota exhaustion (RFC 6585). Includes `Retry-After` header in seconds.

```json
HTTP/1.1 429 Too Many Requests
Retry-After: 43200

{ "error": "QUOTA_EXCEEDED", "remainingQuota": 0, "remainingCredits": 0, "resetsAt": "2026-03-09T00:00:00Z" }
```

---

## Post-Completion

**Manual verification:**
- Test FREE user exhausting quota → confirm 429 response with `Retry-After` header and `resetsAt` in body
- Test FREE user with credits → confirm request allowed after quota exhausted
- Test PRO user → confirm 0 DB calls via logs/metrics
- Check `X-Quota-Remaining` decrements correctly across requests
- Verify `CIRCUIT_OPEN` path via Firestore timeout simulation (WireMock delay)

**Terraform apply:**
- Apply `terraform/firestore.tf` changes in production environment to create TTL + index
- Verify TTL is enabled on `quota_windows.expireAt` in Firebase Console

**GraalVM native build verification:**
- Trigger CI deploy pipeline; native compile must succeed with new reflect/proxy config
- Confirm no `MissingReflectionRegistrationError` in Cloud Run logs

*Note: ralphex automatically moves completed plans to `docs/plans/completed/`*
