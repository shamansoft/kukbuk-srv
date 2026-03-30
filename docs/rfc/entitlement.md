# RFC: Entitlement Service

**Status:** Draft v4 — revised after third review (client UX + extensibility)
**Date:** 2026-03-08
**Author:** Engineering

> **v2 Change Summary:** Addressed five principal engineer concerns.
> Accepted: virtual threads (#2), JWT hybrid tier (#3), CIRCUIT_OPEN outcome (#4).
> Modified: AOP userId extraction via `RequestContextHolder` instead of `@EntitlementIdentity` (#1).
> Partially rejected with reasoning: credits collection placement (#5).
>
> **v3 Change Summary:** Addressed four follow-up concerns.
> Accepted: single profile load eliminating double-read (#1/#2), Micrometer outcome counters (#3), enum reflection registration (#4).
>
> **v4 Change Summary:** Addressed client UX and extensibility gaps.
> Changed HTTP status 403 → 429 for quota denial (RFC 6585 semantics, Retry-After support).
> Added `resetsAt` to error response and `Retry-After` header — client can display "try again in Xh".
> Added startup validation to `EntitlementPlanConfig` — silent zero-quota on missing config becomes a loud startup failure.
> Added missing `ResponseBodyAdvice` implementation sketch (§4.10).
> Explicitly documented that `RECIPE_EXTRACTION` covers both HTML and custom-description operations (product decision).
> Added `remainingCredits` nullability note — `null` (not `-1`) for paid users.
> Added credits expiry, admin quota reset, and quota status endpoint to deferred table.

---

## Table of Contents

1. [Agency Blueprint — Critical Review](#1-agency-blueprint--critical-review)
2. [Our Architecture](#2-our-architecture)
3. [Data Model](#3-data-model)
4. [Spring Component Design](#4-spring-component-design)
5. [API Contract](#5-api-contract)
6. [Infrastructure (OpenTofu)](#6-infrastructure-opentofu)
7. [Firebase → Redis Migration Path](#7-firebase--redis-migration-path)
8. [Microservice Extraction Guide](#8-microservice-extraction-guide)
9. [What We Defer (Explicitly)](#9-what-we-defer-explicitly)
10. [Principal Engineer Review — Point-by-Point Response](#10-principal-engineer-review--point-by-point-response)

---

## 1. Agency Blueprint — Critical Review

The agency proposal demonstrates familiarity with Firestore and GCP but was written without knowledge of the existing codebase. Several decisions conflict with our established patterns, and a few contain technical bugs. We accept the **concept** (quota counters in Firestore with TTL, credits as a fallback, deterministic document IDs) but replace the implementation wholesale.

### 1.1 What the Blueprint Gets Right

| Item | Why It's Correct |
|------|-----------------|
| Deterministic document IDs (date-embedded) | Correct — avoids expensive queries on Firestore |
| TTL via `expireAt` field | Correct — right way to auto-expire Firestore documents |
| Per-user document sharding | Correct — sidesteps Firestore's 1-write/sec per-document limit |
| Credits as a fallback | Sound product logic |
| Idempotency key warning | Valid concern for mobile clients on flaky connections |
| GraalVM reflection warning | Directly applicable to our native-image build |

### 1.2 Architectural Problems

**No abstraction layer.** `EntitlementService` depends directly on `Firestore`. This violates the repository pattern used everywhere else in the codebase (`RecipeRepository`, `UserProfileRepository` — both are interfaces with Firestore implementations). Switching to Redis later means rewriting the service, not swapping an implementation bean.

**No `Operation` concept.** The method `canCreateRecipe()` already leaks domain logic (`recipe`) into an infrastructure service. We have three quota-consuming operations today: HTML recipe extraction, custom recipe creation, and YouTube extraction. Naming these individually produces `canCreateRecipe()`, `canCreateYouTubeRecipe()`, `canCreateCustomRecipe()` — a proliferation anti-pattern. An `Operation` enum is the right model.

**`boolean` return type is too coarse.** The mobile client needs to know *why* it was denied: quota exhausted vs. no credits. It also needs to display "3 of 5 remaining today." A `boolean` encodes none of this.

**Tier in JWT claims is aspirational, not a V1 design.** Firebase custom claims require the Admin SDK to write them server-side (on subscribe/upgrade). After writing, the change only takes effect when the user's ID token refreshes (up to 1 hour). The blueprint assumes `tier` is already in the JWT with no discussion of how it gets there or the propagation delay. See Section 4.5 for our hybrid approach.

**The integration point is unspecified.** The blueprint says the service "sits between controllers and business logic" but shows no Spring integration mechanism. Given our existing filter chain (`FirebaseAuthFilter`) and service patterns, the natural integration is an AOP aspect on the service layer, not a new filter.

### 1.3 Technical Bugs and Issues

**`FieldValue.increment()` inside `transaction.set()`.** The blueprint code does:
```java
transaction.set(dailyRef, Map.of(
    "count", FieldValue.increment(1),   // ← server-side transform
    "expireAt", ...
), SetOptions.merge());
```
The Firestore Java SDK does not support `FieldValue.increment()` inside `set()` in a transaction — you must use `transaction.update()` for field transforms on an existing document, or set explicit computed values after reading. Since the transaction already reads the document, compute `currentCount + 1` explicitly.

**Blocking calls in an async system.** `firestore.runTransaction(...).get()` blocks the calling thread. Our entire repository layer returns `CompletableFuture`. A blocking call here defeats virtual thread scheduling and is inconsistent with every other repository in the project.

**OpenTofu snippet conflicts with existing infrastructure.** The blueprint defines `google_cloud_run_v2_service.recipe_api` — but Cloud Run is already defined in `terraform/cloudrun.tf`. Applying this would either conflict or create a duplicate service. The Firestore TTL configuration (`google_firestore_field.usage_ttl`) is the only piece worth keeping.

### 1.4 Good Tips to Incorporate

- **Idempotency key** — We'll design for it at the repository layer (deduplication window).
- **GraalVM reflect-config.json** — Every new DTO must be registered.
- **`min-instances = 1`** — Already a concern in `cloudrun.tf`; this is a separate Terraform decision, not part of this RFC.
- **Spring Cloud Function** tip — Reject. We use GraalVM native image which achieves <1s cold start without SCF's added complexity.

---

## 2. Our Architecture

### 2.1 Guiding Principles

1. **Repository pattern** — `EntitlementRepository` is an interface. V1 = Firestore. V2 = Redis. The service doesn't change.
2. **Decoupled domain** — `EntitlementService` knows about `userId` and `Operation`. It knows nothing about recipes, YouTube, or Drive.
3. **AOP integration** — A `@CheckEntitlement` annotation on service methods is the seam. Today it calls a local bean. Tomorrow it calls an HTTP client to a separate microservice. The `userId` is read from the active HTTP request context (set by `FirebaseAuthFilter`), not from method parameter reflection.
4. **Rich result, not boolean** — `EntitlementResult` carries outcome, remaining quota, and remaining credits for client display.
5. **Async + virtual threads** — `CompletableFuture` with `newVirtualThreadPerTaskExecutor()`, consistent with the app's virtual-thread configuration.
6. **Configurable limits** — Plan limits live in `application.yaml`, not hardcoded.
7. **Hybrid tier lookup** — Tier from JWT custom claim on the happy path (zero DB cost); Firestore fallback for users without a claim (MVP, all-free).

### 2.2 Component Diagram

```
HTTP Request
    │
    ▼
FirebaseAuthFilter           (existing — validates token, sets userId, userEmail)
    │                        (v2: also reads tier custom claim if present → sets tierHint)
    ▼
DispatcherServlet → RecipeController / YouTubeController
    │
    ▼  @RequestAttribute("userId") flows as method param
RecipeService.createRecipe(String userId, ...)
    │   ← @CheckEntitlement(Operation.RECIPE_EXTRACTION)
    │
    ▼
EntitlementAspect            (NEW — reads userId from RequestContextHolder, not reflection)
    │
    ▼
EntitlementService           (NEW — pure business logic)
    │  1. read tierHint from request context (JWT claim, zero DB cost)
    │  2. if paid tier → return ALLOWED_PAID (no DB read)
    │  3. lookup quota limit from EntitlementPlanConfig
    │  4. EntitlementRepository.checkAndIncrement(userId, op, limit)
    │  5. if DENIED_QUOTA → EntitlementRepository.deductCredit(userId)
    │  6. on DB timeout → CIRCUIT_OPEN (fail open)
    │
    ▼
EntitlementRepository        (NEW — interface)
    │
    ├── FirestoreEntitlementRepository  (V1, @Primary)
    │       quota_windows/{userId}::{operation}::{YYYYMMDD}
    │       credits/tier read from users/{userId} (existing collection)
    │
    └── RedisEntitlementRepository      (V2, future)
            INCR quota:{userId}:{operation}:{YYYYMMDD}
            EXPIREAT at midnight UTC
```

### 2.3 Request Flow

```
POST /v1/recipes  (FREE user, quota available)
  FirebaseAuthFilter: userId=abc, tierHint=FREE (JWT claim absent → FREE)
  EntitlementAspect: userId from RequestContextHolder
  EntitlementService:
    tierHint=FREE → proceed to quota check
    checkAndIncrement: count 2→3, limit=5 → ALLOWED_FREE_QUOTA
  → proceed, header X-Quota-Remaining: 2

POST /v1/recipes  (FREE user, quota exhausted, credits available)
  EntitlementService:
    checkAndIncrement: count=5, limit=5 → DENIED_QUOTA
    deductCredit: credits 2→1 → ALLOWED_CREDIT
  → proceed, header X-Credits-Remaining: 1

POST /v1/recipes  (FREE user, quota exhausted, no credits)
  EntitlementService → DENIED_QUOTA
  EntitlementAspect → throws EntitlementException
  GlobalExceptionHandler → 429 { "error": "QUOTA_EXCEEDED", "resetsAt": "...", ... }
                         → Retry-After: 43200

POST /v1/recipes  (PRO user, tier in JWT)
  FirebaseAuthFilter: tierHint=PRO (from JWT custom claim)
  EntitlementService: tierHint=PRO → return ALLOWED_PAID immediately (0 DB reads)

POST /v1/recipes  (Firestore timeout)
  EntitlementService: orTimeout fires → CIRCUIT_OPEN (fail open, allow request)
  → proceed, header X-Quota-Outcome: CIRCUIT_OPEN
```

### 2.4 Operation Mapping (Product Decision)

`RECIPE_EXTRACTION` covers **both** HTML recipe extraction (`POST /v1/recipes`) and free-text custom recipe creation (`POST /v1/recipes/custom`). They share a single daily quota pool.

**Rationale:** Both operations consume Gemini AI calls of roughly equal cost. Splitting them (`RECIPE_EXTRACTION` + `CUSTOM_RECIPE_CREATION`) would double the user's effective free quota with no cost justification, and add operational complexity. If product decides custom recipes warrant a separate lower/higher limit, adding a new `Operation` enum value and config entry is the only change needed — the architecture already supports it cleanly.

---

## 3. Data Model

### 3.1 Firestore Collections

We add **one new top-level collection** for quota windows. Tier and credits are stored in the existing `users/{userId}` document — no new entitlement collection needed for MVP (see Section 10, Point 5 for reasoning).

#### `users/{userId}` — additions to existing document

Add two fields to the existing `UserProfile`:

```json
{
  "uid":    "firebase-uid-abc123",
  "email":  "user@example.com",
  "...":    "existing fields unchanged",
  "tier":   "FREE",
  "credits": 0
}
```

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `tier` | String | `"FREE"` | Enum: `FREE`, `PRO`, `ENTERPRISE`. Written by admin/webhook on subscription change. |
| `credits` | Integer | `0` | Purchased bypass tokens. Decremented atomically via transaction. |

When neither field is present (existing users), the service defaults to `FREE` + `0 credits`. No migration needed.

#### `quota_windows/{windowId}` — new collection

One document per user × operation × time window.

**Document ID format:** `{userId}::{operation}::{YYYYMMDD}`

- Separator `::` is safe — Firebase UIDs are alphanumeric + hyphens only
- Example: `abc123::RECIPE_EXTRACTION::20260308`

```json
{
  "userId":    "abc123",
  "operation": "RECIPE_EXTRACTION",
  "windowKey": "20260308",
  "count":     3,
  "limit":     5,
  "resetAt":   "2026-03-09T00:00:00Z",
  "expireAt":  "2026-03-10T00:00:00Z",
  "createdAt": "2026-03-08T09:15:00Z"
}
```

**TTL:** Firestore auto-deletes after `expireAt` (may lag up to 24h, but stale documents are never read because the document ID encodes the date).

### 3.2 UserProfile Record Change

Add `tier` and `credits` to the existing Java record:

```java
// UserProfile.java — add two fields
@Builder
public record UserProfile(
    String uid,
    String userId,
    String email,
    String displayName,
    Timestamp createdAt,
    Timestamp updatedAt,
    String googleOAuthToken,
    String googleRefreshToken,
    Timestamp tokenExpiresAt,
    StorageEntity storage,
    // NEW
    UserTier tier,        // defaults to FREE if absent in Firestore doc
    int credits           // defaults to 0 if absent
) {}
```

Update `UserProfile.fromMap()` to read these with null-safe defaults:
```java
UserTier tier = Optional.ofNullable((String) data.get("tier"))
    .map(UserTier::valueOf).orElse(UserTier.FREE);
int credits = Optional.ofNullable((Long) data.get("credits"))
    .map(Long::intValue).orElse(0);
```

### 3.3 Configuration (application.yaml)

```yaml
entitlement:
  plans:
    FREE:
      RECIPE_EXTRACTION:
        daily: 5
      YOUTUBE_EXTRACTION:
        daily: 1
    PRO:
      RECIPE_EXTRACTION:
        daily: -1   # -1 = unlimited
      YOUTUBE_EXTRACTION:
        daily: -1
    ENTERPRISE:
      RECIPE_EXTRACTION:
        daily: -1
      YOUTUBE_EXTRACTION:
        daily: -1
  window:
    timezone: UTC
  timeouts:
    check-ms: 500
    increment-ms: 1000
```

---

## 4. Spring Component Design

### 4.1 Domain Objects

```java
// extractor/src/main/java/net/shamansoft/cookbook/entitlement/

public enum Operation {
    RECIPE_EXTRACTION,
    YOUTUBE_EXTRACTION
}

public enum UserTier {
    FREE, PRO, ENTERPRISE
}

public enum EntitlementOutcome {
    ALLOWED_PAID,        // non-free tier — no limit applied, 0 DB reads
    ALLOWED_FREE_QUOTA,  // free tier, quota available
    ALLOWED_CREDIT,      // free tier quota exhausted, credit deducted
    DENIED_QUOTA,        // quota exhausted, no credits
    CIRCUIT_OPEN         // DB timeout — failed open, quota info is stale
}

public record EntitlementResult(
    boolean allowed,
    EntitlementOutcome outcome,
    int remainingQuota,         // -1 if unlimited, 0 if exhausted
    Integer remainingCredits,   // null for paid/unlimited users (not applicable)
    Instant resetsAt            // when the quota window resets; null if unlimited
) {
    public static EntitlementResult paid() {
        return new EntitlementResult(true, EntitlementOutcome.ALLOWED_PAID, -1, null, null);
    }

    public static EntitlementResult circuitOpen() {
        return new EntitlementResult(true, EntitlementOutcome.CIRCUIT_OPEN, -1, null, null);
    }
}

// Note on remainingCredits nullability:
// Integer (nullable) is used instead of int to distinguish "paid user — credits not applicable"
// (null) from "free user with zero credits" (0). The mobile client should only display the
// credits balance when the value is non-null.

public record QuotaWindow(
    String userId,
    Operation operation,
    String windowKey,
    int count,
    int limit,
    Instant resetAt,
    boolean withinLimit
) {}
```

### 4.2 Repository Interface

```java
public interface EntitlementRepository {

    /**
     * Atomically check and increment the quota counter.
     * Returns the window state after the attempt (count reflects post-increment
     * value if allowed, pre-attempt value if denied).
     *
     * @param limit -1 for unlimited
     */
    CompletableFuture<QuotaWindow> checkAndIncrement(
        String userId, Operation operation, Instant windowStart, int limit);

    /**
     * Atomically decrement credits by 1 from users/{userId}.
     * Returns true if a credit was available and deducted.
     */
    CompletableFuture<Boolean> deductCredit(String userId);

    /**
     * Admin: set tier and/or credits on users/{userId}.
     */
    CompletableFuture<Void> updateTierAndCredits(
        String userId, UserTier tier, int credits);
}
```

Note: tier and credits are now read via the existing `UserProfileRepository` — no separate method needed here.

### 4.3 Firestore Implementation (V1)

```java
@Repository
@Primary
@Slf4j
public class FirestoreEntitlementRepository implements EntitlementRepository {

    private static final String QUOTA_WINDOWS = "quota_windows";
    private static final String USERS = "users";

    // v2: Virtual threads — consistent with spring.threads.virtual.enabled=true
    private static final Executor EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final Firestore firestore;

    public FirestoreEntitlementRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public CompletableFuture<QuotaWindow> checkAndIncrement(
            String userId, Operation operation, Instant windowStart, int limit) {

        String windowKey = DateTimeFormatter.BASIC_ISO_DATE
            .withZone(ZoneOffset.UTC).format(windowStart);
        String docId = userId + "::" + operation.name() + "::" + windowKey;
        DocumentReference ref = firestore.collection(QUOTA_WINDOWS).document(docId);

        Instant resetAt = windowStart.truncatedTo(ChronoUnit.DAYS)
            .plus(1, ChronoUnit.DAYS);
        Instant expireAt = resetAt.plus(2, ChronoUnit.DAYS);

        // Repo-level timeout: if Firestore hangs, don't pin the virtual thread forever
        return CompletableFuture
            .supplyAsync(() -> runCheckAndIncrement(
                userId, operation, windowKey, ref, resetAt, expireAt, limit), EXECUTOR)
            .orTimeout(800, TimeUnit.MILLISECONDS)
            .exceptionally(t -> {
                log.warn("checkAndIncrement timed out or failed for {}/{}: {}",
                    userId, operation, t.getMessage());
                // Return a sentinel: count=0, withinLimit=true → service interprets as CIRCUIT_OPEN
                return new QuotaWindow(userId, operation, windowKey, 0, limit, resetAt, true);
            });
    }

    private QuotaWindow runCheckAndIncrement(
            String userId, Operation operation, String windowKey,
            DocumentReference ref, Instant resetAt, Instant expireAt, int limit) {
        try {
            return firestore.runTransaction(tx -> {
                DocumentSnapshot snap = tx.get(ref).get();  // blocks inside transaction lambda — correct Firestore SDK usage
                int currentCount = snap.exists()
                    ? ((Long) snap.get("count")).intValue()
                    : 0;

                boolean unlimited = limit < 0;
                boolean withinQuota = unlimited || currentCount < limit;

                if (withinQuota) {
                    int newCount = currentCount + 1;
                    // Use explicit value (not FieldValue.increment) — we already read the document
                    Map<String, Object> data = new HashMap<>();
                    data.put("userId", userId);
                    data.put("operation", operation.name());
                    data.put("windowKey", windowKey);
                    data.put("count", newCount);
                    data.put("limit", limit);
                    data.put("resetAt", Timestamp.ofTimeSecondsAndNanos(resetAt.getEpochSecond(), 0));
                    data.put("expireAt", Timestamp.ofTimeSecondsAndNanos(expireAt.getEpochSecond(), 0));
                    if (!snap.exists()) {
                        data.put("createdAt", FieldValue.serverTimestamp());
                    }
                    tx.set(ref, data);  // full set, no merge needed — we own all fields
                    return new QuotaWindow(userId, operation, windowKey,
                        newCount, limit, resetAt, true);
                } else {
                    return new QuotaWindow(userId, operation, windowKey,
                        currentCount, limit, resetAt, false);
                }
            }).get();  // virtual thread handles blocking fine
        } catch (Exception e) {
            throw new RuntimeException("Firestore transaction failed", e);
        }
    }

    @Override
    public CompletableFuture<Boolean> deductCredit(String userId) {
        DocumentReference ref = firestore.collection(USERS).document(userId);
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    return firestore.runTransaction(tx -> {
                        DocumentSnapshot snap = tx.get(ref).get();
                        if (!snap.exists()) return false;
                        Long credits = snap.getLong("credits");
                        if (credits == null || credits <= 0) return false;
                        // FieldValue.increment is valid in transaction.update()
                        tx.update(ref, "credits", FieldValue.increment(-1));
                        return true;
                    }).get();
                } catch (Exception e) {
                    log.error("deductCredit failed for {}: {}", userId, e.getMessage());
                    return false;
                }
            }, EXECUTOR)
            .orTimeout(800, TimeUnit.MILLISECONDS)
            .exceptionally(t -> {
                log.warn("deductCredit timed out for {}", userId);
                return false;
            });
    }

    @Override
    public CompletableFuture<Void> updateTierAndCredits(
            String userId, UserTier tier, int credits) {
        return CompletableFuture.runAsync(() -> {
            try {
                firestore.collection(USERS).document(userId).update(
                    "tier", tier.name(),
                    "credits", credits,
                    "updatedAt", FieldValue.serverTimestamp()
                ).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to update tier for " + userId, e);
            }
        }, EXECUTOR);
    }
}
```

### 4.4 Plan Configuration

```java
@ConfigurationProperties(prefix = "entitlement")
public record EntitlementPlanConfig(
    Map<UserTier, Map<Operation, OperationLimit>> plans,
    WindowConfig window,
    TimeoutConfig timeouts
) {
    public int dailyLimit(UserTier tier, Operation operation) {
        return Optional.ofNullable(plans.get(tier))
            .map(ops -> ops.get(operation))
            .map(OperationLimit::daily)
            .orElse(0);
    }

    public record OperationLimit(int daily) {}
    public record WindowConfig(String timezone) {}
    public record TimeoutConfig(int checkMs, int incrementMs) {}

    /**
     * Startup validation: every UserTier × Operation combination must have an explicit
     * config entry. A missing entry silently returns 0 (deny all) from dailyLimit(),
     * which would block users on a new tier or operation without any error.
     *
     * Add @PostConstruct or call from a SmartInitializingSingleton.
     */
    @PostConstruct
    void validate() {
        for (UserTier tier : UserTier.values()) {
            for (Operation op : Operation.values()) {
                if (!plans.containsKey(tier) || !plans.get(tier).containsKey(op)) {
                    throw new IllegalStateException(
                        "Missing entitlement config for tier=%s operation=%s — add it to application.yaml under entitlement.plans"
                            .formatted(tier, op));
                }
            }
        }
    }
}
```

### 4.5 Entitlement Service

The hybrid tier approach: read from JWT custom claim (zero DB cost) when available; fall back to `UserProfile.tier` (read by existing profile machinery, or default FREE).

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class EntitlementService {

    private final EntitlementRepository entitlementRepository;
    private final UserProfileRepository userProfileRepository;
    private final EntitlementPlanConfig planConfig;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    /**
     * Main entitlement check. Called by EntitlementAspect.
     *
     * @param userId    Firebase UID from request attribute
     * @param tierHint  Tier from JWT custom claim — null if not present in token
     * @param operation The operation being attempted
     */
    public EntitlementResult check(String userId, UserTier tierHint, Operation operation) {
        // v3: load UserProfile exactly once — resolves both tier and credits in a single DB read.
        // Paid users with a JWT tier claim skip this read entirely (tierHint != null).
        UserProfile profile = null;
        UserTier tier = tierHint;

        if (tier == null) {
            profile = userProfileRepository.findByUserId(userId)
                .orTimeout(300, TimeUnit.MILLISECONDS)
                .exceptionally(t -> Optional.empty())
                .join()
                .orElse(null);
            tier = profile != null ? profile.tier() : UserTier.FREE;
        }

        if (tier != UserTier.FREE) {
            record(operation, EntitlementOutcome.ALLOWED_PAID);
            return EntitlementResult.paid();
        }

        int limit = planConfig.dailyLimit(tier, operation);
        Instant now = clock.instant();

        QuotaWindow window;
        try {
            window = entitlementRepository
                .checkAndIncrement(userId, operation, now, limit)
                .get();  // virtual thread — blocking is fine
        } catch (Exception e) {
            log.warn("Entitlement check failed for {}/{} — failing open (CIRCUIT_OPEN)", userId, operation);
            record(operation, EntitlementOutcome.CIRCUIT_OPEN);
            return EntitlementResult.circuitOpen();
        }

        if (window.withinLimit()) {
            int remaining = limit < 0 ? -1 : window.limit() - window.count();
            int credits = profile != null ? profile.credits() : 0;
            record(operation, EntitlementOutcome.ALLOWED_FREE_QUOTA);
            return new EntitlementResult(true, EntitlementOutcome.ALLOWED_FREE_QUOTA,
                Math.max(0, remaining), credits, window.resetAt());
        }

        // Quota exhausted — try credits. We already have the profile snapshot from above.
        int knownCredits = profile != null ? profile.credits() : 0;
        if (knownCredits <= 0) {
            // Fast path: no credits in the snapshot we already loaded — skip the transaction
            record(operation, EntitlementOutcome.DENIED_QUOTA);
            return new EntitlementResult(false, EntitlementOutcome.DENIED_QUOTA, 0, 0, window.resetAt());
        }

        boolean creditDeducted;
        try {
            creditDeducted = entitlementRepository.deductCredit(userId).get();
        } catch (Exception e) {
            log.warn("Credit deduction failed for {} — denying", userId);
            creditDeducted = false;
        }

        if (creditDeducted) {
            record(operation, EntitlementOutcome.ALLOWED_CREDIT);
            return new EntitlementResult(true, EntitlementOutcome.ALLOWED_CREDIT,
                0, knownCredits - 1, window.resetAt());
        }

        record(operation, EntitlementOutcome.DENIED_QUOTA);
        return new EntitlementResult(false, EntitlementOutcome.DENIED_QUOTA, 0, 0, window.resetAt());
    }

    /** Emit a tagged counter for each outcome — enables Cloud Monitoring alerts. */
    private void record(Operation operation, EntitlementOutcome outcome) {
        meterRegistry.counter("entitlement.check",
            "operation", operation.name(),
            "outcome",   outcome.name()
        ).increment();
    }
}
```

**Single-load guarantee:** `UserProfile` is fetched at most once per call. Paid users (JWT tier claim present) skip the fetch entirely. For FREE users, the profile snapshot drives both the tier check *and* the credit fast-path — the credit deduction transaction is only attempted if the snapshot shows credits > 0. If the profile was stale (user just purchased credits in the last milliseconds), the next request will pick up the updated value. This is an acceptable MVP tradeoff.

**Split-transaction consistency note:** The quota increment and the credit decrement are intentionally separate Firestore transactions. If the network drops between them, the user is denied — which is the correct safe-fail outcome. True atomic cross-document consistency would be achievable (Firestore supports it), but the added complexity is unwarranted at this scale. Revisit if we see significant credit-loss complaints in production.

### 4.6 FirebaseAuthFilter — Tier Claim Extraction

Add tier claim extraction to the existing filter. Zero new DB calls; reads directly from the already-verified JWT:

```java
// In FirebaseAuthFilter.doFilterInternal(), after verifyIdToken:
FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
request.setAttribute("userId", decodedToken.getUid());
request.setAttribute("userEmail", decodedToken.getEmail());

// NEW: extract tier custom claim if present
Object tierClaim = decodedToken.getClaims().get("tier");
if (tierClaim != null) {
    try {
        request.setAttribute("userTier", UserTier.valueOf(tierClaim.toString()));
    } catch (IllegalArgumentException e) {
        log.warn("Unknown tier claim value: {}", tierClaim);
        // Leave attribute absent — EntitlementService will default to FREE
    }
}
```

### 4.7 AOP Integration

**v2 change:** `userId` is read from `RequestContextHolder` (the HTTP request attribute set by `FirebaseAuthFilter`), not from method parameter reflection. This approach:
- Requires zero reflection on method parameters
- Works in GraalVM native image without `-parameters` compiler flag
- Is conceptually correct (userId is a request attribute, not a method concern)
- Naturally extends to `userTier` without touching any annotated method signatures

```java
// Annotation
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CheckEntitlement {
    Operation value();
}

// Aspect
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class EntitlementAspect {

    private final EntitlementService entitlementService;

    @Around("@annotation(checkEntitlement)")
    public Object enforce(ProceedingJoinPoint pjp, CheckEntitlement checkEntitlement)
            throws Throwable {

        // Read userId from request context — set by FirebaseAuthFilter, no reflection
        HttpServletRequest request = currentRequest();
        String userId = (String) request.getAttribute("userId");
        UserTier tierHint = (UserTier) request.getAttribute("userTier"); // null if not in JWT

        if (userId == null) {
            // FirebaseAuthFilter already rejected unauthenticated requests
            // This branch should never be hit in production
            throw new IllegalStateException("@CheckEntitlement on unauthenticated path");
        }

        Operation operation = checkEntitlement.value();
        EntitlementResult result = entitlementService.check(userId, tierHint, operation);

        if (!result.allowed()) {
            log.info("Entitlement denied: userId={} operation={} outcome={}",
                userId, operation, result.outcome());
            throw new EntitlementException(result);
        }

        // Store result for response header injection
        request.setAttribute("entitlementResult", result);

        log.debug("Entitlement granted: userId={} op={} outcome={} remaining={}",
            userId, operation, result.outcome(), result.remainingQuota());

        return pjp.proceed();
    }

    private static HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes)
            RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
```

**Usage — zero controller changes, zero method signature changes:**

```java
@Service
public class RecipeService {

    @CheckEntitlement(Operation.RECIPE_EXTRACTION)
    public RecipeResponse createRecipe(String userId, String userEmail, ...) {
        // unchanged — aspect reads userId from request context, not this parameter
    }

    @CheckEntitlement(Operation.RECIPE_EXTRACTION)
    public RecipeResponse createRecipeFromDescription(String userId, ...) {
        // unchanged
    }
}
```

### 4.8 GraalVM: proxy-config.json

Spring AOT generates proxy configs during `./gradlew :cookbook:nativeCompile`, but we must confirm `@Aspect`-annotated beans are handled. Add `proxy-config.json` alongside `reflect-config.json`:

```
extractor/src/main/resources/META-INF/native-image/proxy-config.json
```

```json
[
  {
    "interfaces": ["net.shamansoft.cookbook.service.RecipeService"]
  }
]
```

**Note:** If `RecipeService` does not implement an interface, Spring uses CGLIB subclassing. CGLIB at runtime is not supported in native image. Two options:
1. Extract `RecipeServicePort` interface and have `RecipeService` implement it (preferred — also good for testability and future microservice extraction)
2. Rely on Spring AOT to auto-detect and register the CGLIB proxy — verify with `./gradlew :cookbook:nativeCompile` and a full integration test

The `proxy-config.json` entry is required for JDK-proxy-based (interface) AOP. Run `nativeCompile` locally (JVM simulation mode: `./gradlew :cookbook:test -Dagent`) to verify before native build.

### 4.9 Exception Mapping

```java
public class EntitlementException extends RuntimeException {
    private final EntitlementResult result;
    public EntitlementException(EntitlementResult result) {
        super("Entitlement denied: " + result.outcome());
        this.result = result;
    }
    public EntitlementResult getResult() { return result; }
}

// In GlobalExceptionHandler (@ControllerAdvice)
@ExceptionHandler(EntitlementException.class)
public ResponseEntity<QuotaErrorResponse> handleEntitlement(
        EntitlementException e, HttpServletResponse response) {
    EntitlementResult r = e.getResult();
    // 429 Too Many Requests (RFC 6585) is the correct status for quota/rate-limit denial.
    // 403 Forbidden implies "you are not allowed to do this" (authorization failure).
    // 429 implies "try again later" — semantics the mobile client and standard HTTP clients understand.
    // Retry-After header points to the next quota reset window.
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
    if (r.resetsAt() != null) {
        long secondsUntilReset = r.resetsAt().getEpochSecond() - Instant.now().getEpochSecond();
        builder.header("Retry-After", String.valueOf(Math.max(0, secondsUntilReset)));
    }
    return builder.body(
        new QuotaErrorResponse("QUOTA_EXCEEDED", r.remainingQuota(), r.remainingCredits(), r.resetsAt())
    );
}

public record QuotaErrorResponse(
    String error,
    int remainingQuota,
    Integer remainingCredits,  // null for paid users
    Instant resetsAt           // null if unlimited; ISO-8601 UTC when the window resets
) {}
```

### 4.10 Response Headers

A `ResponseBodyAdvice` reads `entitlementResult` from the request attribute (set by the aspect) and appends headers:

```
X-Quota-Remaining: 2
X-Quota-Outcome: ALLOWED_FREE_QUOTA
X-Credits-Remaining: 0
X-Quota-Resets-At: 2026-03-09T00:00:00Z
```

When outcome is `CIRCUIT_OPEN`:
```
X-Quota-Outcome: CIRCUIT_OPEN
```

The mobile client should treat `CIRCUIT_OPEN` as "allowed, but don't cache quota state."

```java
@ControllerAdvice
public class EntitlementResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true; // apply to all responses; no-ops silently when attribute absent
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
            MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) return body;
        Object attr = servletRequest.getServletRequest().getAttribute("entitlementResult");
        if (!(attr instanceof EntitlementResult r)) return body; // no entitlement check on this path

        HttpHeaders headers = response.getHeaders();
        headers.set("X-Quota-Outcome", r.outcome().name());

        if (r.remainingQuota() >= 0) {
            headers.set("X-Quota-Remaining", String.valueOf(r.remainingQuota()));
        }
        if (r.remainingCredits() != null) {
            headers.set("X-Credits-Remaining", String.valueOf(r.remainingCredits()));
        }
        if (r.resetsAt() != null) {
            headers.set("X-Quota-Resets-At", r.resetsAt().toString());
        }

        return body;
    }
}
```

### 4.11 Observability

`MeterRegistry` is injected into `EntitlementService` (Spring Boot Actuator autoconfigures it). Every outcome emits a tagged counter:

```
entitlement.check{operation="RECIPE_EXTRACTION", outcome="ALLOWED_FREE_QUOTA"}
entitlement.check{operation="RECIPE_EXTRACTION", outcome="ALLOWED_CREDIT"}
entitlement.check{operation="RECIPE_EXTRACTION", outcome="DENIED_QUOTA"}
entitlement.check{operation="RECIPE_EXTRACTION", outcome="CIRCUIT_OPEN"}
entitlement.check{operation="RECIPE_EXTRACTION", outcome="ALLOWED_PAID"}
```

Spring Boot Actuator exports these to Google Cloud Monitoring automatically via the `spring-cloud-gcp-starter-metrics` dependency (or via Prometheus scraping on the `/actuator/prometheus` endpoint). Create an alert on:

```
metric: custom.googleapis.com/entitlement.check
filter: outcome="CIRCUIT_OPEN"
condition: rate > 0.1 (more than 10% of checks are circuit-open)
```

This detects Firestore degradation before it becomes a revenue event, not after the billing cycle.

### 4.12 GraalVM Reflect Config Additions

Add to `extractor/src/main/resources/META-INF/native-image/reflect-config.json`:

```json
{
  "name": "net.shamansoft.cookbook.entitlement.EntitlementResult",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
},
{
  "name": "net.shamansoft.cookbook.entitlement.QuotaErrorResponse",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
},
{
  "name": "net.shamansoft.cookbook.entitlement.QuotaWindow",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
},
{
  "name": "net.shamansoft.cookbook.entitlement.Operation",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
},
{
  "name": "net.shamansoft.cookbook.entitlement.UserTier",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
},
{
  "name": "net.shamansoft.cookbook.entitlement.EntitlementOutcome",
  "allDeclaredFields": true,
  "allDeclaredMethods": true,
  "allDeclaredConstructors": true
}
```

**Why enums need reflection registration:** Jackson serializes/deserializes enum values by name (e.g., `"ALLOWED_PAID"` → `EntitlementOutcome.ALLOWED_PAID`). GraalVM's static analysis does not follow this runtime lookup automatically unless the enum is explicitly registered. Spring AOT may handle this for beans it manages, but types used only in JSON bodies need explicit entries.

**Annotation values are safe:** `@CheckEntitlement(Operation.RECIPE_EXTRACTION)` annotation attributes are resolved at compile time by Spring AOT's annotation processor — they do not require a reflection entry.

---

## 5. API Contract

### 5.1 Error Response (429)

HTTP 429 Too Many Requests is the correct status for quota exhaustion (RFC 6585). It signals "try again later" — semantics the mobile client and standard HTTP tooling understand. 403 Forbidden would signal an authorization failure, not a temporary rate condition.

```
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 43200

{
  "error": "QUOTA_EXCEEDED",
  "remainingQuota": 0,
  "remainingCredits": 0,
  "resetsAt": "2026-03-09T00:00:00Z"
}
```

`Retry-After` is in seconds until the next quota window reset. The mobile client can display "Try again in 12h" without any date math. `resetsAt` provides the exact UTC timestamp for clients that prefer it.

### 5.2 Success Response Headers

```
X-Quota-Remaining: 3
X-Quota-Outcome: ALLOWED_FREE_QUOTA
X-Credits-Remaining: 2
X-Quota-Resets-At: 2026-03-09T00:00:00Z
```

`X-Quota-Resets-At` lets the mobile client display a countdown ("resets in 6h") on every successful response, not just on denial. Omitted for paid users (`ALLOWED_PAID`) and circuit-open.

`CIRCUIT_OPEN` signals the client not to cache quota state:
```
X-Quota-Remaining: -1
X-Quota-Outcome: CIRCUIT_OPEN
```

### 5.3 Idempotency (Phase 2)

Deferred. See Section 9.

### 5.4 Admin Webhook (Phase 2)

```
POST /internal/admin/entitlements/{userId}
Body: { "tier": "PRO", "credits": 10 }
```

Also triggers Firebase custom claim update via Admin SDK so subsequent JWT tokens carry the new tier.

---

## 6. Infrastructure (OpenTofu)

Add to `terraform/firestore.tf`:

```hcl
# TTL on quota_windows.expireAt — auto-delete expired counters
resource "google_firestore_field" "quota_windows_ttl" {
  project    = var.project_id
  database   = "(default)"
  collection = "quota_windows"
  field_path = "expireAt"

  ttl_config {}
}

# Index for future admin queries: list all windows for a user
resource "google_firestore_index" "quota_windows_user_op" {
  project    = var.project_id
  database   = "(default)"
  collection = "quota_windows"

  fields {
    field_path = "userId"
    order      = "ASCENDING"
  }
  fields {
    field_path = "operation"
    order      = "ASCENDING"
  }
  fields {
    field_path = "windowKey"
    order      = "DESCENDING"
  }
}
```

No changes to Cloud Run or any existing service definition.

---

## 7. Firebase → Redis Migration Path

The `EntitlementRepository` interface is the entire migration boundary.

### 7.1 What Changes

| Component | Firebase (V1) | Redis (V2) |
|-----------|--------------|-----------|
| Quota counters | `quota_windows/{docId}` | `INCR quota:{userId}:{op}:{YYYYMMDD}` |
| TTL | Firestore TTL (`expireAt`) | `EXPIREAT` at midnight UTC |
| Credits | `users/{userId}.credits` via transaction | Redis Hash + Lua script |
| Atomicity | Firestore transaction | Redis Lua script (single round-trip) |
| Latency | ~50–100ms | <1ms |
| `EntitlementRepository` impl | `FirestoreEntitlementRepository` | `RedisEntitlementRepository` |
| Service/Aspect/DTOs | **unchanged** | **unchanged** |

### 7.2 Redis Implementation Sketch

```java
@Repository
@ConditionalOnProperty(name = "entitlement.backend", havingValue = "redis")
public class RedisEntitlementRepository implements EntitlementRepository {

    private final RedisTemplate<String, String> redis;

    @Override
    public CompletableFuture<QuotaWindow> checkAndIncrement(
            String userId, Operation operation, Instant windowStart, int limit) {

        String key = "quota:" + userId + ":" + operation.name() + ":"
            + DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(windowStart);
        Instant resetAt = windowStart.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
        String windowKey = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(windowStart);

        return CompletableFuture.supplyAsync(() -> {
            // Atomic check-and-increment via Lua — single round-trip, no race conditions
            String script = """
                local current = tonumber(redis.call('GET', KEYS[1])) or 0
                local lim = tonumber(ARGV[1])
                if lim < 0 or current < lim then
                    local newval = redis.call('INCR', KEYS[1])
                    redis.call('EXPIREAT', KEYS[1], ARGV[2])
                    return {1, newval}
                end
                return {0, current}
                """;
            long midnightEpoch = resetAt.getEpochSecond();
            List<?> result = (List<?>) redis.execute(
                new DefaultRedisScript<>(script, List.class),
                List.of(key), String.valueOf(limit), String.valueOf(midnightEpoch)
            );
            boolean allowed = ((Long) result.get(0)) == 1L;
            int count = ((Long) result.get(1)).intValue();
            return new QuotaWindow(userId, operation, windowKey, count, limit, resetAt, allowed);
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
    // deductCredit: similarly via Lua DECR with floor-at-zero guard
}
```

### 7.3 Migration Steps

1. Add `spring-data-redis` dependency
2. Implement `RedisEntitlementRepository`
3. Set `entitlement.backend=redis` in `application-gcp.yaml`
4. Deploy — quota windows reset (short-lived, acceptable); credits need one-time migration script from Firestore
5. `FirestoreEntitlementRepository` remains as fallback or is deleted

---

## 8. Microservice Extraction Guide

The `@CheckEntitlement` aspect is the extraction seam. The `RequestContextHolder`-based userId extraction remains valid across extraction because Cloud Run service-to-service calls still carry the original request context.

### 8.1 Extraction Steps

1. **Create `entitlement-service/` module.** Move `EntitlementService`, `EntitlementRepository` implementations, configuration. Expose:
   - `GET /v1/entitlement/check?userId={}&operation={}` → `EntitlementResult`

2. **Create `EntitlementClient` in cookbook module:**
   ```java
   @Component
   @ConditionalOnProperty(name = "entitlement.mode", havingValue = "remote")
   public class EntitlementClient {
       private final RestClient restClient;

       public EntitlementResult check(String userId, Operation operation) {
           return restClient.get()
               .uri("/v1/entitlement/check?userId={u}&operation={o}", userId, operation)
               .retrieve().body(EntitlementResult.class);
       }
   }
   ```

3. **Update `EntitlementAspect`** to inject `EntitlementClient` when `entitlement.mode=remote`. The aspect code doesn't change — just the bean it calls.

4. **Service-to-service auth:** Use Cloud Run OIDC tokens. The entitlement service validates the incoming token is from the cookbook service account.

---

## 9. What We Defer (Explicitly)

| Feature | Reason | Trigger |
|---------|--------|---------|
| Idempotency keys | Low volume MVP; mobile retry risk is acceptable | First duplicate charge reported |
| Admin API (tier/credits webhook) | Do via Firebase console for now | First paying customer |
| Tier JWT custom claim write path | Payment system doesn't exist yet | Stripe/payment integration |
| Burst rate limiting (req/min) | Daily quota sufficient for MVP | Abuse observed |
| Monthly rolling windows | Daily is sufficient | Product decision |
| Microservice extraction | 18+ months away | Decision to split monolith |
| `RecipeServicePort` interface extraction | Only needed if CGLIB proxy fails native compile | Native image CI failure |
| Admin quota reset endpoint | Manual Firestore console deletion is sufficient at MVP | First customer support ticket requiring quota reset |
| Quota status endpoint (`GET /v1/entitlement/status`) | Client gets quota state from response headers on normal operation; proactive display not needed at MVP | Explicit product requirement for quota dashboard UI |
| Credits expiry (`creditsExpireAt` field) | No credits are sold yet; unexpiring credits are fine until payment ships | Stripe/payment integration — add before first credit sale to avoid accounting liability |
| `CUSTOM_RECIPE_CREATION` as separate operation | Intentionally shares `RECIPE_EXTRACTION` quota (see §2.4); separate limit only if product decides | Product decision to charge differently for custom vs. HTML recipes |

---

## 10. Principal Engineer Review — Point-by-Point Responses

### Review Round 1

### Point 1: AOP Reflection Risk (GraalVM) — Modified Accept

**Confirmed:** The project has no `-parameters` compiler flag configured. Parameter-name reflection would break in native image.

**Our fix is different from the suggested `@EntitlementIdentity` annotation.** We use `RequestContextHolder` to read `userId` from the request attributes set by `FirebaseAuthFilter`. This is strictly better:
- Zero reflection on method parameters
- `userId` is already in the request context by design (it's how all controllers get it)
- `@EntitlementIdentity` would add an annotation that must appear on every future service method — easy to forget
- If we ever add more context (e.g., `orgId` for enterprise), we extend `FirebaseAuthFilter`, not every method signature

The `proxy-config.json` concern is valid and addressed in Section 4.8.

### Point 2: Virtual Threads vs. `CachedThreadPool` — Accepted

Switched to `Executors.newVirtualThreadPerTaskExecutor()` in `FirestoreEntitlementRepository`. This is consistent with `spring.threads.virtual.enabled=true` already in `application.yaml`. Also added repo-level `orTimeout(800ms)` before the service-level timeout — belt-and-suspenders.

The concern about `.get()` pinning: virtual threads do not pin on `Object.wait()` or most blocking I/O. Firestore SDK's `ApiFuture.get()` uses standard Java blocking — virtual threads handle this correctly. The `orTimeout` remains as a safety net against genuinely hung transactions.

**Note:** Existing repos (`FirestoreRecipeRepository`, `FirestoreUserProfileRepository`) still use `CachedThreadPool`. That's a separate refactoring concern and out of scope for this RFC. Don't touch what isn't broken.

### Point 3: Custom Claims Hybrid — Accepted

Implemented in Section 4.5 and 4.6. `FirebaseAuthFilter` extracts `tier` from JWT claims if present; `EntitlementService.resolveTier()` uses it or falls back to `UserProfile`. For MVP (no paying users, no custom claims), the behavior is identical to a pure-Firestore approach. When payment ships, paid users get 0 DB reads on the happy path.

**Important caveat retained:** The 1-hour staleness window on JWT custom claims is a known tradeoff. If a user cancels their subscription, they retain access for up to 1 hour. This is industry-standard behavior for JWT-based auth systems and acceptable for our use case.

### Point 4: Fail-Open Double-Increment Risk — Partially Accepted

The double-increment scenario described in the review is not technically possible: each Firestore transaction commits exactly once or not at all. If the service-level `orTimeout` fires, the background transaction may still commit — but that's a single commit of +1, which is correct behavior (the request was allowed AND the counter should reflect it).

**What is valid:** the recommendation to explicitly signal `CIRCUIT_OPEN` to the mobile client so it doesn't cache stale quota data. Added `CIRCUIT_OPEN` to `EntitlementOutcome` and `X-Quota-Outcome: CIRCUIT_OPEN` response header. The mobile client should: display "quota unavailable" state rather than caching "unlimited."

### Point 5: Credits Collection Split — Rejected, with reasoning

The principal recommends keeping credits in `users/{userId}` for MVP simplicity.

**We accept this specific conclusion but not the general principle.** We DO keep credits in `users/{userId}` (Section 3.1). However, we disagree with "coupling is fine for MVP" as a blanket rule — that thinking has caused many costly rewrites.

The reason keeping credits in `users/` is acceptable here is different:
1. The Profile screen doesn't need credits — that's a subscription concern, not a profile concern
2. Credits are only read when quota is exhausted — rare path, same number of DB reads regardless of collection
3. The `UserProfile` record already has proper `fromMap`/`toMap` methods; adding two nullable fields is a one-line change

The reason we **did not** create a separate `user_entitlements` collection is simply that it wasn't needed to achieve the clean separation — the `EntitlementRepository` interface provides that boundary. When we extract to a microservice, we move ownership of the `tier` and `credits` fields in `users/` to the entitlement service, which can read/write that collection directly. The collection split (if needed) happens at extraction time, not before.

---

### Review Round 2

### Point 1 + 2: Split Transaction / Double-Read — Accepted

Both concerns trace back to the same root cause: `UserProfile` was fetched twice (`resolveTier` + `resolveCredits`). Fixed in v3 — profile is loaded once at the top of `check()`, or skipped entirely for paid users with a JWT tier claim.

The credit fast-path is a bonus: if the profile snapshot shows `credits == 0`, we skip the `deductCredit` transaction entirely — saving one round-trip in the most common quota-exceeded case.

On the split-transaction concern specifically: two separate Firestore transactions (quota increment, then credit decrement) are intentional. If the network drops between them, the user is denied — the correct safe-fail outcome. True cross-document atomicity (both in one transaction) is possible in Firestore but adds complexity that is not justified at MVP scale. Documented explicitly in the service code comment.

### Point 3: Observability / Silent Fail-Open — Accepted

Added `MeterRegistry` injection and `entitlement.check{operation, outcome}` counter in `EntitlementService.record()`. Added Section 4.11 with the counter taxonomy and a concrete Cloud Monitoring alert threshold (`CIRCUIT_OPEN` rate > 10%).

The "silent" aspect is important: without this counter, a Firestore brownout looks like normal traffic in dashboards. With it, a CIRCUIT_OPEN spike is a named, alertable signal.

Declined the offer to draft a full Micrometer/Cloud Monitoring config in this RFC — it belongs in the observability runbook alongside other service SLOs, not here.

### Point 4: Missing Enum Reflection — Accepted

Added `Operation`, `UserTier`, and `EntitlementOutcome` to the reflect-config additions in Section 4.12. Added explanatory note clarifying why annotation attribute values (`@CheckEntitlement(Operation.X)`) are safe — they are resolved by Spring AOT at compile time, not at runtime via reflection.

---

## Appendix: File Map

New files:

```
extractor/src/main/java/net/shamansoft/cookbook/entitlement/
    Operation.java
    UserTier.java
    EntitlementOutcome.java
    EntitlementResult.java
    QuotaWindow.java
    EntitlementRepository.java
    FirestoreEntitlementRepository.java
    EntitlementService.java
    EntitlementPlanConfig.java
    CheckEntitlement.java
    EntitlementAspect.java
    EntitlementException.java
    EntitlementResponseAdvice.java
    dto/QuotaErrorResponse.java

extractor/src/test/java/net/shamansoft/cookbook/entitlement/
    EntitlementServiceTest.java
    FirestoreEntitlementRepositoryTest.java
    EntitlementAspectTest.java

extractor/src/main/resources/META-INF/native-image/
    proxy-config.json                   (new)
    reflect-config.json                 (additions)

terraform/
    firestore.tf                        (add TTL + composite index)
```

Files to modify:

```
extractor/src/main/resources/application.yaml
    → add entitlement.plans config block

extractor/src/main/java/net/shamansoft/cookbook/repository/firestore/model/UserProfile.java
    → add tier (UserTier, default FREE) and credits (int, default 0) fields
    → update fromMap() and toMap()

extractor/src/main/java/net/shamansoft/cookbook/security/FirebaseAuthFilter.java
    → extract tier custom claim → request.setAttribute("userTier", ...)

extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java
    → @CheckEntitlement(Operation.RECIPE_EXTRACTION) on createRecipe(), createRecipeFromDescription()

extractor/src/main/java/net/shamansoft/cookbook/controller/YouTubeController.java (or its service)
    → @CheckEntitlement(Operation.YOUTUBE_EXTRACTION) on job creation
```
