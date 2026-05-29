# Admin Endpoint to Update User Tier

## Overview

Add a `PUT /v1/admin/users/{userId}/tier` endpoint allowing admin users to change any user's
subscription tier (FREE → PRO → ENTERPRISE and back). Admins authenticate via Firebase Auth
with an `"admin": true` custom claim — they are **not** stored in the Firestore `users/`
collection. When the tier is updated, both the Firestore `UserProfile` and the Firebase custom
claim are written so the `EntitlementService` fast-path picks up the change on next token
refresh.

See `docs/create-admin-instructions.md` for how to create and credential an admin user.

## Context

- **Auth filter:** `extractor/src/main/java/net/shamansoft/cookbook/security/FirebaseAuthFilter.java` — extracts `userId`, `userEmail`, `userTier` from JWT; needs `isAdmin` extraction added
- **Entitlement fast-path:** `extractor/src/main/java/net/shamansoft/cookbook/entitlement/EntitlementService.java` — reads `tier` claim from JWT to skip Firestore profile read for paid users
- **Entitlement AOP pattern:** `extractor/src/main/java/net/shamansoft/cookbook/entitlement/EntitlementAspect.java` — reference pattern for `AdminAccessAspect`
- **UserProfile model:** `extractor/src/main/java/net/shamansoft/cookbook/repository/firestore/model/UserProfile.java` — has `tier` (UserTier) and `credits` fields
- **UserProfileRepository:** `extractor/src/main/java/net/shamansoft/cookbook/repository/UserProfileRepository.java` — needs `updateTier()` added
- **UserTier enum:** `extractor/src/main/java/net/shamansoft/cookbook/entitlement/UserTier.java` — FREE, PRO, ENTERPRISE

## Development Approach

- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- Make small, focused changes
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**
- Run `./gradlew :cookbook:test` after each task

## Testing Strategy

- **Unit tests**: JUnit 5 + Mockito; required for every task
- No e2e / integration tests required for this change (no WireMock stubs needed for Firebase Admin SDK calls — mock `FirebaseAuth` directly)

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix

## What Goes Where

- **Implementation Steps**: all code changes, tests, and config in this repo
- **Post-Completion**: manual admin claim setup, Postman/curl verification, token propagation note

## Implementation Steps

### Task 1: Extend `FirebaseAuthFilter` to detect admin claim

- [x] in `doFilterInternal()`, after existing `tier` extraction, read `claims.get("admin")`
- [x] set `request.setAttribute("isAdmin", true)` when claim is `Boolean.TRUE`
- [x] write unit test: token with `admin:true` sets attribute; token without does not
- [x] run tests — must pass before Task 2

### Task 2: `@RequireAdmin` annotation + `AdminAccessAspect`

- [x] create `extractor/src/main/java/net/shamansoft/cookbook/security/RequireAdmin.java` — `@Target(METHOD) @Retention(RUNTIME)` annotation
- [x] create `extractor/src/main/java/net/shamansoft/cookbook/security/AdminAccessAspect.java` — `@Around` on `@RequireAdmin`; reads `isAdmin` from `RequestContextHolder`; throws `AccessDeniedException` (→ 403) when absent or false
- [x] write unit tests for `AdminAccessAspect`: absent attribute → 403, `false` → 403, `true` → proceeds
- [x] run tests — must pass before Task 3

### Task 3: DTOs

- [x] create `extractor/src/main/java/net/shamansoft/cookbook/dto/admin/UpdateTierRequest.java` — `record UpdateTierRequest(@NotNull UserTier tier)`
- [x] create `extractor/src/main/java/net/shamansoft/cookbook/dto/admin/UpdateTierResponse.java` — `record UpdateTierResponse(String userId, UserTier oldTier, UserTier newTier)`
- [x] write unit tests: `UpdateTierRequest` deserialization from JSON (valid tier, invalid tier → 400)
- [x] run tests — must pass before Task 4

### Task 4: `UserProfileRepository.updateTier()`

- [x] add `void updateTier(String userId, UserTier tier)` to `UserProfileRepository` interface
- [x] implement in `FirestoreUserProfileRepository`: `docRef.set(Map.of("tier", tier.name(), "updatedAt", Timestamp.now()), SetOptions.merge()).get(SAVE_TIMEOUT_MS, MILLISECONDS)`
- [x] write unit test: mock Firestore, assert merge-write called with correct map; assert no full document overwrite
- [x] run tests — must pass before Task 5

### Task 5: `AdminUserService`

- [x] create `extractor/src/main/java/net/shamansoft/cookbook/service/AdminUserService.java`
- [x] implement `updateUserTier(adminUserId, targetUserId, newTier)`: load profile → updateTier (Firestore) → `firebaseAuth.setCustomUserClaims(targetUserId, Map.of("tier", newTier.name()))` → audit log → return `UpdateTierResponse`
- [x] throw `UserNotFoundException` (404) if profile not found
- [x] emit structured audit log line: `admin={} updated tier for user={} from={} to={}`
- [x] write unit tests: successful update calls both Firestore write and Firebase claim write; unknown userId → 404; audit log is emitted
- [x] run tests — must pass before Task 6

### Task 6: `AdminController`

- [x] create `extractor/src/main/java/net/shamansoft/cookbook/controller/AdminController.java`
- [x] implement `@PutMapping("/v1/admin/users/{userId}/tier")` annotated with `@RequireAdmin`
- [x] extract `adminUserId` from `request.getAttribute("userId")`; delegate to `AdminUserService`
- [x] write unit tests: non-admin → 403, valid admin request → 200 with response body, unknown userId → 404, invalid tier in body → 400
- [x] run tests — must pass before Task 7

### Task 7: Verify acceptance criteria

- [x] verify `PUT /v1/admin/users/{userId}/tier` returns 200 with `oldTier` and `newTier`
- [x] verify non-admin Firebase token → 403
- [x] verify unknown `userId` → 404
- [x] verify invalid tier string in body → 400
- [x] run full test suite: `./gradlew :cookbook:test`
- [x] run linter / checkstyle if configured

### Task 8: Update documentation

- [ ] add `/v1/admin/users/{userId}/tier` to the API Endpoints section in `CLAUDE.md`
- [ ] update `docs/plans/update-tier-endpoint.md` or remove the old file (superseded by this one)

## Technical Details

**Admin claim check (JWT):**
```java
Object adminClaim = claims != null ? claims.get("admin") : null;
if (Boolean.TRUE.equals(adminClaim)) {
    request.setAttribute("isAdmin", true);
}
```

**Firestore merge-write (targeted update):**
```java
docRef.set(
    Map.of("tier", tier.name(), "updatedAt", Timestamp.now()),
    SetOptions.merge()
).get(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
```

**Firebase custom claim write (enables EntitlementService fast-path on next token refresh):**
```java
firebaseAuth.setCustomUserClaims(targetUserId, Map.of("tier", newTier.name()));
```

**Endpoint contract:**
```
PUT /v1/admin/users/{userId}/tier
Authorization: Bearer <admin-id-token>
Content-Type: application/json
{"tier": "PRO"}

200 → {"userId": "...", "oldTier": "FREE", "newTier": "PRO"}
400 → invalid tier value
403 → caller does not have admin:true claim
404 → target user not found in Firestore
```

## Post-Completion

**Admin setup** (out-of-band, see `docs/create-admin-instructions.md`):
- Create Firebase user in Console
- Run `set-admin.mjs` to set `admin:true` custom claim
- Sign in via REST to get a fresh ID token that includes the claim

**Manual verification via curl:**
```bash
curl -X PUT https://YOUR_SERVICE/v1/admin/users/TARGET_UID/tier \
  -H "Authorization: Bearer ADMIN_ID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tier": "PRO"}'
```

**Token propagation delay:** Firestore update takes effect immediately. Firebase custom claim
(used by `EntitlementService` fast-path) takes effect on the target user's next token refresh
— up to 1 hour. This is standard Firebase behavior and expected.
