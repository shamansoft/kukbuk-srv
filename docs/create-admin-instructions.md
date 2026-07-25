# Creating an Admin User

Admin users authenticate via Firebase Auth with an `"admin": true` custom claim in their JWT.
They are **not** stored in the Firestore `users/` profile collection.

---

## Step 1: Create the Firebase User

Go to [Firebase Console](https://console.firebase.google.com/) → Select project `kukbuk-tf`
→ **Authentication** → **Users** → **Add user**

Enter an email and password. Copy the **UID** shown in the user list.

---

## Step 2: Set the `admin: true` Custom Claim

Run this script from any machine authenticated to GCP.

**Prerequisites:**

```bash
gcloud auth application-default login   # authenticate to GCP
npm install firebase-admin              # install Firebase Admin SDK
```

**Script (`set-admin.mjs`):**

```js
import admin from 'firebase-admin';

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: 'kukbuk-tf'
});

const uid = 'PASTE_UID_HERE';
await admin.auth().setCustomUserClaims(uid, { admin: true });
console.log(`Done — admin:true claim set for ${uid}`);
process.exit(0);
```

```bash
node set-admin.mjs
```

**Verify the claim was set:**

```bash
node -e "
const admin = (await import('firebase-admin')).default;
admin.initializeApp({ credential: admin.credential.applicationDefault(), projectId: 'kukbuk-tf' });
const user = await admin.auth().getUser('PASTE_UID_HERE');
console.log(user.customClaims);
" --input-type=module
# Expected output: { admin: true }
```

---

## Step 3: Get a Firebase ID Token (for Postman / curl)

Custom claims are baked into the JWT at sign-in time, so you must sign in **after** setting the
claim to get a token that includes `admin: true`.

Find your project's **Web API Key** in Firebase Console → Project Settings → General.

```bash
curl -s -X POST \
  "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=YOUR_WEB_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@yourdomain.com","password":"yourpassword","returnSecureToken":true}' \
  | jq -r '.idToken'
```

The returned `idToken` is valid for **1 hour**. Re-run the command to get a fresh token.

To avoid re-entering the password, save the `refreshToken` from the same response and exchange
it for a new ID token:

```bash
curl -s -X POST \
  "https://securetoken.googleapis.com/v1/token?key=YOUR_WEB_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"refresh_token","refresh_token":PASTE_REFRESH_TOKEN_HERE}' \
  | jq -r '.id_token'
```

---

## Step 4: Call the Admin Endpoint

```bash
curl -X PUT https://YOUR_SERVICE_URL/v1/admin/users/TARGET_USER_UID/tier \
  -H "Authorization: Bearer PASTE_ID_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{"tier": "PRO"}'
```

Expected response:
```json
{ "userId": "TARGET_USER_UID", "oldTier": "FREE", "newTier": "PRO" }
```

---

## Revoking Admin Access

To remove admin privileges, clear the custom claims:

```js
await admin.auth().setCustomUserClaims(uid, {});
```

Or delete the Firebase user entirely from the Console.

---

## Summary

```
Firebase Console → create user → copy UID
        ↓
set-admin.mjs → setCustomUserClaims(uid, { admin: true })
        ↓
signInWithPassword REST → idToken (JWT with admin:true baked in)
        ↓
curl/Postman → Authorization: Bearer <idToken>
```
