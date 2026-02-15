# Architecture Blueprint: Recipe App Tiering & Entitlements

## 1. High-Level Strategy

We are moving away from hardcoded logic toward an **Entitlement-Based Architecture**. Users do not "have a tier"; they "have entitlements" (capabilities and quotas).

### The Tiers

* **Free:** 1 recipe/day, 10 recipes/month. Access to basic manual entry.
* **Paid 1:** Unlimited manual recipe creation.
* **Paid 2:** Unlimited creation + Premium Features (YouTube Scraping, OCR).
* **Credits:** Consumable tokens that allow Free users to bypass their daily/monthly quota.

---

## 2. Backend Implementation Strategy (for Java Team)

### Entitlement Abstraction

We will use a **Gatekeeper Pattern**. The business logic should not know *how* the count is stored, only *if* the action is permitted.

* **Logic:**
1. Check **JWT Custom Claims** for Tier (Fastest).
2. If Tier is "Free", check the `EntitlementGatekeeper`.
3. If Quota is reached, check for available **Credits** in the User Profile.



### MVP Persistence: Firestore (Adapter 1)

To keep costs low and infra simple, we use Firestore documents as atomic counters.

* **Path:** `/usage_counters/{userId}_QUOTA_{PERIOD}`
* **Mechanism:** `FieldValue.increment(1)` inside a transaction.
* **TTL Policy:** Documents will include an `expireAt` timestamp to allow Firestore's native TTL to auto-delete old counters.

### Scale Path: Redis (Adapter 2)

When traffic grows, we swap the Firestore implementation for **Google Cloud Memorystore (Redis)** using a Lua-based sliding window algorithm to reduce latency to <2ms.

---

## 3. Infrastructure (OpenTofu/GCP)

We must ensure the infrastructure supports the TTL policy for Firestore to prevent "data bloat."

```hcl
# Firestore TTL Policy for usage counters
resource "google_firestore_field" "usage_ttl" {
  project    = var.project_id
  database   = "(default)"
  collection = "usage_counters"
  field_path = "expireAt"

  ttl_config {}
}

```

---

## 4. Mobile & API Contract (for Mobile Principal)

To ensure a smooth UX, the mobile client needs to know the user's status without constant polling.

### Identity & Claims

* **Method:** We use **Firebase Custom Claims**.
* **Contract:** Upon login or token refresh, the ID Token will contain:
```json
{
  "tier": "FREE | PAID_1 | PAID_2",
  "features": ["can_ocr", "can_youtube"]
}

```


* **Sync:** When a user buys a subscription or credits, the Mobile app **must** call a `forceRefresh` on the Firebase User token to get the updated claims.

### The Quota API

Before a user opens the "Create Recipe" flow, the app should optionally pre-flight the status or handle the following 403 response gracefully.

**Endpoint:** `POST /v1/recipes`
**Error Responses:**
| Status Code | Error Code | Logic |
| :--- | :--- | :--- |
| **403 Forbidden** | `QUOTA_EXCEEDED` | Show "Daily/Monthly Limit Reached" popup + "Buy Credits" CTA. |
| **403 Forbidden** | `FEATURE_LOCKED` | Show "Upgrade to Paid 2" upsell for OCR/YouTube. |
| **402 Payment Required** | `INSUFFICIENT_CREDITS` | Specific to the "Use Credit" flow if the user has 0 credits. |

### Offline Considerations

* Since quotas are enforced server-side (to prevent clock-tampering), recipe creation requires an active connection.
* The Mobile app should cache the `tier` claim to locally hide/show premium buttons (OCR/YouTube) to improve UI snappy-ness.

---

## 5. Summary of Next Steps

1. **Backend:** Implement the `EntitlementGatekeeper` interface and Firestore adapter.
2. **Infra:** Deploy the OpenTofu script to enable TTL on the `usage_counters` collection.
3. **Mobile:** Update the Auth listener to check for custom claims and update the UI state.
4. **DevOps:** Set up a Cloud Function or Hook to update Firebase Claims whenever a Google Play/App Store purchase is verified.
