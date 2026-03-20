# Entitlement PR #77 — Post-Review Fixes

## Overview

Address all issues found in the code review of PR #77 (entitlement quota system):

1. **[Critical]** `@CheckEntitlement` on `RecipeService` is AOP-bypass-prone — move to `RecipeController`
2. **[Critical]** `remainingCredits` always `null` in `ALLOWED_CREDIT` — change `deductCredit()` return type to `OptionalInt`
3. **[Medium]** `deductCredit()` uses `checkMs` (500ms) timeout for a write transaction — should use `incrementMs` (1000ms)
4. **[Medium]** Unchecked cast of `userTier` request attribute — add type-safe cast to prevent `ClassCastException` → 500
5. **[Low]** Static executor in `FirestoreEntitlementRepository` leaks across tests — make it an instance field
6. **[Low]** No-write-on-deny path relies on an undocumented Firestore TTL invariant — add explanatory comment
7. **[Tests]** Three missing test cases: `InterruptedException` in credit deduction, end-to-end AOP+headers+429, `tx.set()` not called on deny

## Context (from discovery)

- **Files to modify:**
  - `extractor/src/main/java/net/shamansoft/cookbook/entitlement/EntitlementRepository.java` — change `deductCredit` return type
  - `extractor/src/main/java/net/shamansoft/cookbook/entitlement/FirestoreEntitlementRepository.java` — implement new return type, fix timeout, remove static executor, add TTL comment
  - `extractor/src/main/java/net/shamansoft/cookbook/entitlement/EntitlementService.java` — consume `OptionalInt`, propagate `remainingCredits`
  - `extractor/src/main/java/net/shamansoft/cookbook/entitlement/EntitlementAspect.java` — type-safe `userTier` cast
  - `extractor/src/main/java/net/shamansoft/cookbook/controller/RecipeController.java` — add `@CheckEntitlement`
  - `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java` — remove `@CheckEntitlement`
- **Test files to update:**
  - `extractor/src/test/java/net/shamansoft/cookbook/entitlement/EntitlementServiceTest.java`
  - `extractor/src/test/java/net/shamansoft/cookbook/entitlement/EntitlementAspectTest.java`
  - `extractor/src/test/java/net/shamansoft/cookbook/entitlement/FirestoreEntitlementRepositoryTest.java`
  - New: end-to-end slice test (new file)

## Development Approach

- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- Run `./gradlew :cookbook:test` after each task — must pass before proceeding
- **CRITICAL: all tests must pass before starting next task**

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document blockers with ⚠️ prefix

## Implementation Steps

---

### Task 1: Fix type-safe `userTier` cast in `EntitlementAspect`

Prevents `ClassCastException` → HTTP 500 if any other filter writes a non-`UserTier` value under the `"userTier"` key.

- [x] In `EntitlementAspect.java:34`, replace raw cast with an `instanceof` pattern match:
  ```java
  // before
  UserTier userTier = (UserTier) request.getAttribute("userTier");
  // after
  Object tierAttr = request.getAttribute("userTier");
  UserTier userTier = tierAttr instanceof UserTier t ? t : null;
  ```
- [x] Verify existing tests in `EntitlementAspectTest` still pass unchanged (no behaviour change for the normal path)
- [x] Add test: `around_userTierWrongType_treatedAsNull_defaultsToFree` — set `"userTier"` to a String, verify aspect proceeds (falls through to FREE-tier quota check rather than throwing `ClassCastException`)
- [x] Run `./gradlew :cookbook:test` — must pass

---

### Task 2: Fix `deductCredit` timeout budget

One-line fix: a credit deduction is a Firestore read-modify-write transaction and should share the `incrementMs` budget, not `checkMs`.

- [x] In `FirestoreEntitlementRepository.java:127`, change `.orTimeout(planConfig.timeouts().checkMs(), ...)` to `.orTimeout(planConfig.timeouts().incrementMs(), ...)`
- [x] Update `FirestoreEntitlementRepositoryTest` — add a comment noting the timeout used (no logic change to the existing tests, just verify they pass)
- [x] Run `./gradlew :cookbook:test` — must pass

---

### Task 3: Change `deductCredit()` return type to `OptionalInt`

Enables the service to return actual remaining credits in the `ALLOWED_CREDIT` result, fixing the `X-Credits-Remaining` header gap.

**Interface change:**
- [x] In `EntitlementRepository.java`, change:
  ```java
  CompletableFuture<Boolean> deductCredit(String userId);
  ```
  to:
  ```java
  // Returns OptionalInt.of(remaining) if a credit was deducted; OptionalInt.empty() if no credits available.
  CompletableFuture<OptionalInt> deductCredit(String userId);
  ```

**Repository implementation:**
- [x] In `FirestoreEntitlementRepository.deductCredit()`, update the Firestore transaction to:
  - Return `OptionalInt.of(newCredits)` (where `newCredits = currentCredits - 1`) when credits are available and deducted
  - Return `OptionalInt.empty()` when `currentCredits <= 0` or user document not found
  - Same error handling as before (exception → `OptionalInt.empty()` via catch block)

**Service update:**
- [x] In `EntitlementService.java` lines 78–95, update credit deduction block:
  ```java
  OptionalInt creditResult = OptionalInt.empty();
  try {
      creditResult = entitlementRepository.deductCredit(userId).get();
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Credit deduction interrupted for userId={}", userId);
  } catch (ExecutionException e) {
      log.warn("Credit deduction failed for userId={}: {}", userId,
              e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
  }

  if (creditResult.isPresent()) {
      record(operation, EntitlementOutcome.ALLOWED_CREDIT);
      return new EntitlementResult(true, EntitlementOutcome.ALLOWED_CREDIT, 0, creditResult.getAsInt(), window.resetAt());
  } else {
      record(operation, EntitlementOutcome.DENIED_QUOTA);
      return new EntitlementResult(false, EntitlementOutcome.DENIED_QUOTA, 0, null, window.resetAt());
  }
  ```

**Test updates:**
- [x] Update `FirestoreEntitlementRepositoryTest`: change all `deductCredit_*` tests to assert `OptionalInt` return values instead of `boolean`
  - `deductCredit_creditAvailable_returnsTrue` → assert `OptionalInt.of(remainingCount)`
  - `deductCredit_noCredits_returnsFalse` → assert `OptionalInt.empty()`
  - `deductCredit_nullCredits_returnsFalse` → assert `OptionalInt.empty()`
  - `deductCredit_userNotFound_returnsFalse` → assert `OptionalInt.empty()`
  - `deductCredit_firestoreThrows_returnsFalse` → assert `OptionalInt.empty()`
- [x] Update `EntitlementServiceTest`:
  - `check_quotaExhausted_creditAvailable_returnsAllowedCredit` — verify `remainingCredits` is the actual count (not `null`)
  - `check_allowedCredit_remainingCreditsIsNull` — rename and invert: `check_allowedCredit_remainingCreditsIsReturned`, assert non-null
  - `check_deductCreditThrowsExecutionException_returnsDeniedQuota` — no change needed (still DENIED_QUOTA)
  - Add: `check_deductCreditInterrupted_returnsDeniedQuota` — mock `deductCredit` to throw `InterruptedException` wrapped in future, assert `DENIED_QUOTA` outcome (this covers the missing InterruptedException path from the test gap list)
- [x] Run `./gradlew :cookbook:test` — must pass

---

### Task 4: Move `@CheckEntitlement` from `RecipeService` to `RecipeController`

Eliminates the AOP-bypass risk for future internal callers. The controller layer is always entered via the Spring proxy for HTTP requests.

**RecipeService — remove annotations:**
- [x] Remove `@CheckEntitlement(Operation.RECIPE_EXTRACTION)` from `RecipeService.createRecipe()` (line 50)
- [x] Remove `@CheckEntitlement(Operation.RECIPE_EXTRACTION)` from `RecipeService.createRecipeFromDescription()` (line 95)

**RecipeController — add annotations:**
- [x] Add `@CheckEntitlement(Operation.RECIPE_EXTRACTION)` to `RecipeController.createRecipe()` (the `POST /v1/recipes` handler)
- [x] Add `@CheckEntitlement(Operation.RECIPE_EXTRACTION)` to `RecipeController.createCustomRecipe()` (the `POST /v1/recipes/custom` handler)
- [x] Verify `EntitlementAspect` can still resolve `userId` and `userTier` from request attributes — both are already populated by `FirebaseAuthFilter` before the controller executes, so no change needed in the aspect

**Test updates:**
- [x] In `EntitlementAspectTest`, if any test directly targets a `RecipeService` mock with `@CheckEntitlement`, update to target a `RecipeController`-level method instead. (If tests are already aspect-generic — i.e. use a synthetic annotated method — no change needed.)
- [x] Verify `RecipeServiceTest` (if it exists) — no entitlement stubs should be needed there anymore
- [x] Run `./gradlew :cookbook:test` — must pass

---

### Task 5: Remove static executor; document TTL invariant

Two low-priority hygiene fixes in `FirestoreEntitlementRepository`.

**Static executor → instance field:**
- [x] Change `private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();` to an instance field:
  ```java
  private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
  ```
- [x] Update all usages of `EXECUTOR` → `executor` within the class
- [x] Add `@PreDestroy` shutdown if the executor implements `ExecutorService` (virtual-thread-per-task executor does implement it):
  ```java
  @PreDestroy
  public void shutdown() {
      ((ExecutorService) executor).shutdown();
  }
  ```

**Document TTL invariant:**
- [x] In `FirestoreEntitlementRepository.checkAndIncrement()`, above the `if (withinLimit)` block (lines 67–79), add:
  ```java
  // No write on the deny path: the quota window document already exists and has been
  // counted to its limit. We intentionally skip tx.set() to avoid unnecessary writes.
  //
  // TTL invariant: expireAt = resetAt + 2 days. Firestore auto-deletes expired documents
  // well after the quota window resets at midnight UTC, so a TTL-deleted document will
  // never be mistaken for a fresh window within the same quota day.
  ```

**Test:**
- [x] In `FirestoreEntitlementRepositoryTest`, add: `checkAndIncrement_atLimit_doesNotWriteDocument` — verify `transaction.set()` is **never** called when the count is at or above the limit (covers the missing assertion identified in the review)
- [x] Run `./gradlew :cookbook:test` — must pass

---

### Task 6: Add end-to-end entitlement slice test

Validates that the AOP aspect, response advice, and exception handler all work together in a real Spring context — catching proxy-bypass issues that unit tests cannot detect.

- [x] Create `extractor/src/test/java/net/shamansoft/cookbook/entitlement/EntitlementEndToEndTest.java` as a `@SpringBootTest` slice test (note: `@WebMvcTest` not available in Spring Boot 4.0.1)
- [x] Mock `EntitlementService`, `RecipeService`, and related beans
- [x] Test case: `postRecipe_quotaExhausted_returns429WithRetryAfterHeader`
  - stub `EntitlementService.check()` to return a `DENIED_QUOTA` result
  - POST to `/v1/recipes`
  - assert HTTP 429
  - assert `Retry-After` header is present
  - assert response body is `QuotaErrorResponse` with `"QUOTA_EXCEEDED"` code
- [x] Test case: `postRecipe_quotaAllowed_returnsQuotaHeaders`
  - stub `EntitlementService.check()` to return `ALLOWED_FREE_QUOTA` result with `remainingQuota=4`
  - POST to `/v1/recipes` (stub `RecipeService` to return a minimal success response)
  - assert HTTP 200
  - assert `X-Quota-Outcome: ALLOWED_FREE_QUOTA` header present
  - assert `X-Quota-Remaining: 4` header present
- [x] Run `./gradlew :cookbook:test` — must pass

---

### Task 7: Verify acceptance criteria

- [x] Confirm `@CheckEntitlement` appears on `RecipeController` methods and is absent from `RecipeService`
- [x] Confirm `X-Credits-Remaining` header is set (non-null) on `ALLOWED_CREDIT` responses
- [x] Confirm `deductCredit` uses `incrementMs` timeout
- [x] Confirm `userTier` cast uses `instanceof` pattern match
- [x] Confirm `EXECUTOR` is no longer `static`
- [x] Confirm TTL invariant comment is in place
- [x] Run full test suite: `./gradlew :cookbook:test` — all pass
- [x] Run integration tests: `./gradlew :cookbook:intTest` — all pass

---

### Task 8: [Final] Update documentation

- [ ] Update `CLAUDE.md` entitlement section: note that `@CheckEntitlement` lives on controller layer
- [ ] Update `CLAUDE.md`: update `deductCredit` description to reflect `OptionalInt` return type

*Note: ralphex automatically moves completed plans to `docs/plans/completed/`*

---

## Technical Details

### `deductCredit` return type change

```
EntitlementRepository.deductCredit():
  Before: CompletableFuture<Boolean>  — true if deducted, false if not
  After:  CompletableFuture<OptionalInt>  — OptionalInt.of(remaining) if deducted, OptionalInt.empty() if not
```

The Firestore transaction already reads `credits` from the user document. The remaining count is `currentCredits - 1` and is available atomically within the transaction — no extra read needed.

### `@CheckEntitlement` placement

```
Before: RecipeService.createRecipe()                @CheckEntitlement(RECIPE_EXTRACTION)
        RecipeService.createRecipeFromDescription()  @CheckEntitlement(RECIPE_EXTRACTION)

After:  RecipeController.createRecipe()             @CheckEntitlement(RECIPE_EXTRACTION)
        RecipeController.createCustomRecipe()        @CheckEntitlement(RECIPE_EXTRACTION)
```

`EntitlementAspect` reads `userId` and `userTier` from `HttpServletRequest` attributes — both are populated by `FirebaseAuthFilter` before any controller method executes. No changes needed to the aspect.

### Timeout budget

```
checkAndIncrement (write tx):  incrementMs = 1000ms  ✓ (unchanged)
deductCredit (write tx):       incrementMs = 1000ms  ← fix (was checkMs = 500ms)
profile load (read):           checkMs     = 500ms   ✓ (unchanged)
```

## Post-Completion

**Manual verification:**
- Test with a real FREE-tier user who has exhausted quota but has credits — verify `X-Credits-Remaining` header decrements correctly across requests
- Verify `Retry-After` header value is reasonable (seconds until midnight UTC)

**PR:**
- These fixes target the `entitlment` branch; open as a follow-up commit or amend before merge
