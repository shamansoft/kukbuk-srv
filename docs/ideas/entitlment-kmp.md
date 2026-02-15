Here is the Mobile Architecture Blueprint for the Entitlements Service.

This document outlines how we will implement the entitlement logic using **Kotlin Multiplatform (KMP)** to share business rules across Android and iOS, ensuring a consistent state management strategy that aligns with the Backend Blueprint.

---

# Mobile Architecture Blueprint: Entitlements & Tiering

## 1. High-Level Strategy

We will treat **Entitlements** as a reactive state within the application, driven by the Authentication layer but decoupled from specific UI screens.

* **Architecture Pattern:** Clean Architecture with MVI (Model-View-Intent).
* **Tech Stack:** Kotlin Multiplatform (KMP) for shared logic, Koin for DI, Kotlin Flows for state management.
* **Source of Truth:** The `SessionRepository` (holding the ID Token) combined with a specific `EntitlementRepository`.

### The Client-Side Tier Model

We will map the backend claims to a strict sealed class hierarchy in the Shared Module (`commonMain`) to ensure exhaustive `when` blocks in the UI.

```kotlin
// shared/src/commonMain/kotlin/domain/models/UserTier.kt

enum class Tier {
    FREE, PAID_1, PAID_2
}

enum class AppFeature {
    MANUAL_ENTRY, YOUTUBE_SCRAPE, OCR_SCAN, BYPASS_QUOTA
}

data class EntitlementState(
    val tier: Tier,
    val enabledFeatures: Set<AppFeature>,
    val creditBalance: Int = 0, // See Backend Request section
    val quotaUsage: QuotaUsage? = null // See Backend Request section
)

```

---

## 2. Domain Layer (KMP Shared Logic)

We will implement an `EntitlementManager` in the shared domain layer. This ensures that iOS and Android decide whether to show a "Lock Icon" using the exact same logic.

### Use Cases

1. **`ObserveEntitlementsUseCase`**: Exposes a `Flow<EntitlementState>`. This parses the JWT Claims and combines them with real-time data (like credits).
2. **`CanAccessFeatureUseCase`**: A synchronous check used by UI components to determine visibility.
* *Input:* `AppFeature`
* *Output:* `Boolean` (Enabled/Disabled)



### Token Refresh Strategy

Per the backend requirement, we cannot rely on the token automatically refreshing fast enough after a purchase.

* **Trigger:** Upon successful completion of the Native IAP flow (Google Play Billing / StoreKit).
* **Action:** The `PaymentRepository` will trigger `AuthRepository.forceTokenRefresh()`.
* **Result:** The new ID Token is fetched, claims are parsed, and the `EntitlementState` Flow emits the new Tier.

---

## 3. Data Layer & Integration

### Authentication Interceptor

We will use a `Ktor` HttpClient interceptor (or Retrofit/OkHttp for Android-specific networking) to handle the global error states defined in the backend blueprint.

```kotlin
// Pseudo-code for Network Interceptor
suspend fun intercept(response: HttpResponse) {
    if (response.status == 403) {
        val errorCode = response.headers["X-Error-Code"] // Standardize this header
        when (errorCode) {
            "QUOTA_EXCEEDED" -> _events.emit(GlobalEvent.ShowQuotaUpsell)
            "FEATURE_LOCKED" -> _events.emit(GlobalEvent.ShowTierUpsell)
        }
    }
    if (response.status == 402 && errorCode == "INSUFFICIENT_CREDITS") {
        _events.emit(GlobalEvent.ShowCreditPurchase)
    }
}

```

### Local Caching

To ensure "UI snappy-ness" as requested:

* **Persistence:** We will store the last known `EntitlementState` in `DataStore` (Android) / `NSUserDefaults` (iOS) via a KMP wrapper.
* **Boot Logic:** On app launch, we load the cached state immediately while the network performs the token refresh.

---

## 4. UI/UX Implementation

### Profile Page

* **Section:** "Membership Level".
* **Display:** derived from `EntitlementState.tier`.
* **Action:** "Manage Subscription" button.
* *Free:* Opens the "Upgrade Plan" bottom sheet.
* *Paid:* Opens the native subscription management screen (Google Play / App Store settings).



### Feature Gating (The "Grayed Out" Experience)

We will not hide features entirely; we will show them as **Disabled with Intent**.

* **Component:** `PremiumFeatureWrapper` (Composable / SwiftUI View).
* **Logic:**
1. Check `CanAccessFeatureUseCase(feature)`.
2. If `true`: Render content normally.
3. If `false`: Render content with 50% opacity and a "Lock" icon overlay.
4. **Click Action (Locked):** Triggers the `UpsellBottomSheet` specific to that feature (e.g., "Unlock OCR with Paid-2").



### Error Handling & Upsells

When the Backend returns `403 QUOTA_EXCEEDED`:

1. The network layer intercepts the error.
2. A global `Snackbar` or `Dialog` appears: *"Daily limit reached. Use a Credit to continue?"*
3. **Action:** Calls `POST /v1/recipes?use_credit=true` (See Backend Request).

---

## 5. Mobile Infrastructure

* **IAP Library:** We will use **RevenueCat** (or Google Play Billing Library + StoreKit 2 wrappers if avoiding third-party fees) to abstract the purchase flow.
* *Reasoning:* It simplifies the validation handshake with our backend.


* **Analytics:** Log `view_locked_feature`, `tap_upgrade`, and `purchase_success` to Firebase Analytics to measure conversion rates for Paid-1 vs Paid-2.

---

# 6. Evaluation & Requests for Backend Team

> **⚠️ Critical Integration Gaps**
> The current Backend Blueprint relies heavily on JWT Claims. While excellent for *Access Control*, claims are poor for *Consumption Status* (Counters/Credits) because they don't update in real-time as users consume resources, and we cannot force a token refresh on every "Create Recipe" action.

### Request 1: Real-time Quota & Credit Visibility

**Problem:** The blueprint says "Limits enforced by backend." However, the UI needs to show *"You have 2 recipes left today"* or *"5 Credits Remaining"* on the Profile page. We cannot rely on the ID Token for rapidly changing counters (like credits) or daily usage counts, as the token only updates on refresh (expiry/force).

**Request:** Please expose a lightweight endpoint or a Firestore document stream that the app can observe for real-time counters.

* **Proposed Solution:** A Firestore document at `/user_profiles/{userId}/public/status`.
* **Structure:**
```json
{
  "credits_remaining": 5,
  "daily_recipes_created": 1,
  "daily_limit": 1
}

```


*Mobile will listen to this snapshot to update the UI counters in real-time without polling.*

### Request 2: "Use Credit" Mechanism

**Problem:** The blueprint mentions an `INSUFFICIENT_CREDITS` error but not how to *intentionally* use a credit.
**Request:** How does the mobile client signal that the user *wants* to burn a credit to bypass the limit?

* **Proposal:** Add a query parameter or header to the creation endpoint.
* `POST /v1/recipes?consume_credit=true`



### Request 3: Standardized Error Headers

**Problem:** Parsing HTTP Body for error codes in 403 responses can be flaky depending on the framework.
**Request:** Please return the error reason in a standard Header as well as the body.

* **Header:** `X-Entitlement-Reason: QUOTA_EXCEEDED` or `FEATURE_LOCKED`.

### Request 4: IAP Validation Webhook

**Confirmation Needed:** When we perform the purchase via Google Play/Apple, we will send the receipt to the backend (or RevenueCat will). Please confirm there is a listener (Cloud Function) established that updates the Firebase User Claims immediately upon receipt validation so our `forceRefresh` call works.

### Request 5: Feature Flags in Claims

**Refinement:** In the Claims structure:

```json
"features": ["can_ocr", "can_youtube"]

```

Please ensure these keys map 1:1 to the `AppFeature` enums we agreed upon. Additionally, please include `max_daily_recipes` in the claim if possible, so we don't have to hardcode "10" or "1" on the client side, allowing you to change tier limits remotely without app updates.