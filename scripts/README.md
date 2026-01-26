# Testing Scripts

This directory contains scripts for testing the Save-a-Recipe backend service.

## Available Scripts

### `test-user-creation.sh`

Tests the complete Firebase authentication and user creation flow.

**What it does:**
1. Authenticates with Firebase using email/password
2. Gets Firebase ID token
3. Calls backend API to create/get user profile
4. Stores OAuth tokens (simulated)
5. Provides verification instructions

**Quick Start:**

```bash
# 1. Set required environment variables
export FIREBASE_API_KEY="AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
export TEST_EMAIL="test@example.com"
export TEST_PASSWORD="your-test-password"

# 2. Optional: Set backend URL (defaults to localhost:8080)
export BACKEND_URL="http://localhost:8080"
# or for Cloud Run:
# export BACKEND_URL="https://cookbook-xxxxx-uc.a.run.app"

# 3. Run the script
./test-user-creation.sh
```

**Prerequisites:**
- `jq` installed (`brew install jq` on macOS)
- Firebase Web API Key (from Firebase Console → Project Settings)
- Test user created in Firebase Console (Authentication → Users → Add user)
- Backend service running (locally or on Cloud Run)

**Example Output:**

```
🔍 Validating prerequisites...
✅ All prerequisites met

📋 Configuration:
  Backend URL: http://localhost:8080
  Test Email:  test@example.com
  API Key:     AIzaSyXXXXXXXXXXXXXX...

🔐 Step 1: Authenticating with Firebase...
✅ Authentication successful!
   User ID:    xK9mP2nQ3rS4tU5vW6x
   Email:      test@example.com
   Token:      eyJhbGciOiJSUzI1NiIsImtpZCI6IjFhZjU...
   Expires in: 3600s (60 minutes)

👤 Step 2: Getting/Creating user profile...
✅ Profile retrieved/created successfully!
{
  "userId": "xK9mP2nQ3rS4tU5vW6x",
  "email": "test@example.com"
}

🔑 Step 3: Storing OAuth tokens (simulated)...
✅ OAuth tokens stored successfully!
{
  "status": "success",
  "message": "OAuth tokens stored successfully"
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🎉 User creation flow completed successfully!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Getting Firebase Web API Key

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project (`kukbuk-tf`)
3. Click the gear icon → Project Settings
4. Scroll down to "Your apps" section
5. Find "Web API Key" under "Project credentials"
6. Copy the key (looks like: `AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX`)

## Creating a Test User

1. Go to Firebase Console → Authentication
2. Click "Users" tab
3. Click "Add user"
4. Enter email and password
5. Click "Add user"

**Or using Firebase CLI:**

```bash
firebase auth:import users.json --project=kukbuk-tf
```

Where `users.json` contains:

```json
{
  "users": [
    {
      "localId": "test-user-123",
      "email": "test@example.com",
      "passwordHash": "...",
      "salt": "...",
      "emailVerified": true
    }
  ]
}
```

## Manual Testing with cURL

If you prefer to test manually:

### 1. Get Firebase ID Token

```bash
curl -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "your-password",
    "returnSecureToken": true
  }' | jq -r '.idToken'
```

Save the token:
```bash
ID_TOKEN="eyJhbGciOiJSUzI1NiIsImtpZCI6..."
```

### 2. Test Backend Endpoints

Get user profile:
```bash
curl -X GET "http://localhost:8080/api/user/profile" \
  -H "Authorization: Bearer ${ID_TOKEN}"
```

Store OAuth tokens:
```bash
curl -X POST "http://localhost:8080/api/user/oauth-tokens" \
  -H "Authorization: Bearer ${ID_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "ya29.a0AfH6SMBxxxxxx",
    "refreshToken": "1//0gxxxxxx",
    "expiresIn": 3600
  }'
```

Create recipe:
```bash
curl -X POST "http://localhost:8080/recipe" \
  -H "Authorization: Bearer ${ID_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/recipe",
    "html": "<html><body><h1>Test Recipe</h1></body></html>",
    "title": "Test Recipe"
  }'
```

## Verifying User Profile in Firestore

### Firebase Console
Visit: `https://console.firebase.google.com/project/kukbuk-tf/firestore/data/users/{userId}`

### gcloud CLI
```bash
gcloud firestore databases documents get \
  projects/kukbuk-tf/databases/(default)/documents/users/{userId}
```

### Firebase CLI
```bash
firebase firestore:query users --where userId==YOUR_USER_ID --project=kukbuk-tf
```

## Troubleshooting

### "jq: command not found"
Install jq:
```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get install jq

# Alpine Linux
apk add jq
```

### "Invalid API Key"
- Verify your Firebase Web API Key in Firebase Console
- Ensure no extra spaces or quotes in the environment variable
- Try regenerating the key if needed

### "EMAIL_NOT_FOUND" or "INVALID_PASSWORD"
- Create a test user in Firebase Console
- Verify the email and password are correct
- Check if email verification is required

### "Authentication service unavailable" (503)
- Backend Firebase configuration is missing
- Check `GOOGLE_APPLICATION_CREDENTIALS` environment variable
- Verify service account has necessary permissions

### "No authorization token" (401)
- Ensure you're sending `Authorization: Bearer <token>` header
- Token may have expired (valid for 1 hour)
- Re-authenticate to get a new token

### Backend connection refused
- Ensure backend is running: `./gradlew bootRun`
- Check the correct URL (localhost vs Cloud Run)
- Verify port number (default: 8080)

## Related Documentation

- [Testing User Creation with cURL](../docs/testing-user-creation-with-curl.md) - Detailed guide
- [PHASE-1-Firebase-Auth-MVP.md](../../tasks/PHASE-1-Firebase-Auth-MVP.md) - Auth implementation
- [API Documentation](../docs/API.md) - All available endpoints

## Contributing

When adding new test scripts:
1. Make them executable: `chmod +x script-name.sh`
2. Add error handling with `set -e`
3. Use colored output for better readability
4. Provide clear error messages
5. Document prerequisites and usage
6. Add entry to this README