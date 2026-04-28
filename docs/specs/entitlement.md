# Entitlement System — As-Built Spec

Per-user quota enforcement for the FREE tier. Backed by Firestore, enforced via AOP, with a credits fallback and fail-open on timeout.

**RFC:** `docs/rfc/entitlement.md` (design rationale and review history)

---

## How It Works

```
HTTP Request
    → FirebaseAuthFilter         (extracts userId, userTier from JWT → request attributes)
    → EntitlementAspect          (intercepts @CheckEntitlement methods)
        → EntitlementService.check()
            → paid tier fast-path (no Firestore read)
            → FREE: checkAndIncrement() in Firestore
                → within quota → ALLOWED_FREE_QUOTA
                → exhausted + has credits → deductCredit() → ALLOWED_CREDIT
                → exhausted, no credits → DENIED_QUOTA
                → Firestore timeout → CIRCUIT_OPEN (fail-open)
    → EntitlementResponseAdvice  (appends X-Quota-* headers on success)
    → CookbookExceptionHandler   (EntitlementException → HTTP 429)
```

---

## Package

`net.shamansoft.cookbook.entitlement`

| Class | Role |
|---|---|
| `Operation` | enum: `RECIPE_EXTRACTION`, `YOUTUBE_EXTRACTION` |
| `UserTier` | enum: `FREE`, `PRO`, `ENTERPRISE` |
| `EntitlementOutcome` | enum: see outcomes below |
| `EntitlementResult` | record: `allowed`, `outcome`, `remainingQuota`, `remainingCredits` (null for paid), `resetsAt` (null for paid/circuit-open) |
| `QuotaWindow` | record: Firestore window snapshot |
| `EntitlementRepository` | interface |
| `FirestoreEntitlementRepository` | `@Repository @Primary` — Firestore implementation |
| `EntitlementPlanConfig` | `@ConfigurationProperties(prefix="entitlement")` — per-tier limits |
| `EntitlementService` | `@Service` — orchestrates quota check |
| `CheckEntitlement` | annotation: `@CheckEntitlement(Operation.X)` |
| `EntitlementAspect` | `@Aspect` — intercepts annotated controller methods |
| `EntitlementException` | thrown on `DENIED_QUOTA`; carries `EntitlementResult` |
| `EntitlementAuthException` | thrown when `userId` is absent/blank (→ HTTP 401) |
| `EntitlementResponseAdvice` | `@ControllerAdvice` — appends response headers |
| `dto/QuotaErrorResponse` | 429 response body |

---

## Annotation Placement

`@CheckEntitlement` is on **controller** methods, not service methods:
- `RecipeController.createRecipe()` — `POST /v1/recipes`
- `RecipeController.createCustomRecipe()` — `POST /v1/recipes/custom`

This prevents AOP bypass when the service is called internally (e.g., from tests or future internal callers).

---

## Outcomes

| Outcome | Meaning | `allowed` |
|---|---|---|
| `ALLOWED_PAID` | PRO/ENTERPRISE tier — no quota check | `true` |
| `ALLOWED_FREE_QUOTA` | FREE tier within daily quota | `true` |
| `ALLOWED_CREDIT` | FREE tier quota exhausted, credit deducted | `true` |
| `DENIED_QUOTA` | FREE tier quota exhausted, no credits | `false` → HTTP 429 |
| `CIRCUIT_OPEN` | Firestore timed out — fail-open, request allowed | `true` |

---

## Response Headers

Added to all successful `@CheckEntitlement`-gated responses:

| Header | Value | Notes |
|---|---|---|
| `X-Quota-Outcome` | `EntitlementOutcome` name | Always present |
| `X-Quota-Remaining` | daily quota remaining | Omitted for paid/circuit-open |
| `X-Credits-Remaining` | credits remaining after deduction | Only on `ALLOWED_CREDIT` |
| `X-Quota-Resets-At` | ISO-8601 UTC reset time | Omitted for paid/unlimited/circuit-open |

---

## Error Response (HTTP 429)

```
HTTP/1.1 429 Too Many Requests
Retry-After: 43200
Content-Type: application/json

{
  "error": "QUOTA_EXCEEDED",
  "remainingQuota": 0,
  "remainingCredits": 0,
  "resetsAt": "2026-03-09T00:00:00Z"
}
```

`Retry-After` is in seconds until `resetsAt`. Present only when `resetsAt` is non-null.

---

## Firestore Data Model

| Collection | Doc ID | Key fields |
|---|---|---|
| `quota_windows` | `{userId}::{operation}::{YYYYMMDD}` | `count`, `limit`, `resetAt`, `expireAt` |
| `users` | `{userId}` | `tier` (string), `credits` (int) |

**TTL invariant:** `expireAt = resetAt + 2 days`. Firestore auto-deletes expired windows. A TTL-deleted document is never mistaken for a fresh window within the same quota day.

**No-write on deny:** On the deny path, `tx.set()` is skipped. The document already exists and is at its limit — no write needed.

---

## Configuration

```yaml
entitlement:
  plans:
    FREE:
      RECIPE_EXTRACTION:
        daily: ${ENTITLEMENT_FREE_RECIPE_DAILY:5}
      YOUTUBE_EXTRACTION:
        daily: ${ENTITLEMENT_FREE_YOUTUBE_DAILY:1}
    PRO:
      RECIPE_EXTRACTION:
        daily: -1   # unlimited
      YOUTUBE_EXTRACTION:
        daily: -1
    ENTERPRISE:
      RECIPE_EXTRACTION:
        daily: -1
      YOUTUBE_EXTRACTION:
        daily: -1
  timeouts:
    check-ms: 500      # profile load (read)
    increment-ms: 1000 # checkAndIncrement + deductCredit (write transactions)
```

**Startup validation:** `EntitlementPlanConfig` asserts every `UserTier × Operation` pair has an explicit entry. Missing config throws `IllegalStateException` at startup — silent zero-quota bugs are surfaced immediately.

---

## Timeout Behavior

| Operation | Timeout | On timeout |
|---|---|---|
| Profile load | `check-ms` (500ms) | Default to `FREE` tier |
| `checkAndIncrement` | `increment-ms` (1000ms) | `CIRCUIT_OPEN` — request **allowed** |
| `deductCredit` | `increment-ms` (1000ms) | `OptionalInt.empty()` → treated as no credits → `DENIED_QUOTA` |

**Fail-open:** Firestore quota DB unavailability does not degrade API availability.

---

## Tier Extraction

`FirebaseAuthFilter` reads the `tier` JWT claim → parses to `UserTier` → stores as `"userTier"` request attribute. Unknown claim values are logged as WARN and the attribute is not set (falls through to FREE).

Paid users (non-null `userTier` attribute) skip the Firestore profile read entirely in `EntitlementService`.

---

## GraalVM Native Config

`reflect-config.json` contains entries for: `EntitlementResult`, `QuotaErrorResponse`, `QuotaWindow`, `Operation`, `UserTier`, `EntitlementOutcome`.

`proxy-config.json` is an empty array — `RecipeService` is a class (CGLIB proxy, not JDK proxy).
