# Running Cookbook Locally with Firestore

## Issues Fixed

### 1. Missing `uid` field in UserProfile
**Problem**: Firestore documents contain a `uid` field (created by Firebase Auth), but the Java `UserProfile` record didn't have it, causing deserialization warnings.

**Solution**: Added `uid` field to `UserProfile` record in `extractor/src/main/java/net/shamansoft/cookbook/repository/firestore/model/UserProfile.java`

### 2. Java Record incompatibility with Firestore `toObject()`
**Problem**: Firestore's automatic deserialization (`doc.toObject(UserProfile.class)`) doesn't work reliably with Java 17+ records because:
- Records don't have no-arg constructors
- Records use `fieldName()` accessors instead of `getFieldName()`

**Solution**: Changed `StorageService.getStorageInfo()` to manually deserialize the `storage` field from a `Map` instead of using `toObject()`:

```java
// Old (broken with records):
UserProfile userProfile = doc.toObject(UserProfile.class);
StorageEntity storageEntity = userProfile.storage();

// New (works with records):
Map<String, Object> storageMap = (Map<String, Object>) doc.get("storage");
StorageEntity storageEntity = StorageEntity.builder()
    .type((String) storageMap.get("type"))
    .connected(Boolean.TRUE.equals(storageMap.get("connected")))
    // ... etc
    .build();
```

### 3. Missing folderId handling in RecipeService
**Problem**: When `folderId` was null in the user profile, it was passed directly to Google Drive API, causing 404 errors.

**Solution**: Added null-check in `RecipeService.listRecipes()` to call `getOrCreateFolder()` if `folderId` is null.

## Local Development Setup

### Prerequisites
1. **Google Cloud SDK** installed (`gcloud` command available)
2. **Google Cloud credentials** configured
3. **Firestore enabled** in your GCP project (`kukbuk-tf`)

### Option 1: Application Default Credentials (Recommended)

```bash
# Authenticate with Google Cloud
gcloud auth application-default login

# Set project ID
export GOOGLE_CLOUD_PROJECT_ID=kukbuk-tf

# Run the application
./gradlew :cookbook:bootRun
```

### Option 2: Service Account Key

```bash
# Create a service account key (if you don't have one)
gcloud iam service-accounts keys create ~/firebase-key.json \
  --iam-account=YOUR_SERVICE_ACCOUNT@kukbuk-tf.iam.gserviceaccount.com

# Set environment variables
export GOOGLE_APPLICATION_CREDENTIALS=~/firebase-key.json
export GOOGLE_CLOUD_PROJECT_ID=kukbuk-tf

# Run the application
./gradlew :cookbook:bootRun
```

### Required Environment Variables

For the application to run locally, you need:

```bash
# GCP / Firestore
export GOOGLE_CLOUD_PROJECT_ID=kukbuk-tf

# Gemini AI
export COOKBOOK_GEMINI_API_KEY=your_gemini_api_key

# Google OAuth (for Drive integration)
export SAR_SRV_GOOGLE_OAUTH_SECRET=your_oauth_secret
```

## Verifying Your Setup

### 1. Check Firestore Connection

```bash
# List Firestore documents to verify access
gcloud firestore databases documents list \
  --project=kukbuk-tf \
  --database='(default)'
```

### 2. Check Credentials

```bash
# Verify ADC is configured
if [ -f "$HOME/.config/gcloud/application_default_credentials.json" ]; then
  echo "✓ ADC configured"
else
  echo "✗ Run: gcloud auth application-default login"
fi

# Verify project ID
echo "Project ID: $GOOGLE_CLOUD_PROJECT_ID"
```

### 3. Check Application Logs

When running `./gradlew :cookbook:bootRun`, look for:

```
[main] INFO ...FirestoreConfig - Initializing Firestore with project ID: kukbuk-tf
[main] INFO ...FirestoreConfig - Using default credentials (Application Default Credentials)
[main] INFO ...FirestoreConfig - Firestore initialized successfully
```

## Troubleshooting

### Error: "No accessor for uid found"
This is just a warning and can be ignored. The manual deserialization handles it correctly.

### Error: "Failed to initialize Firestore"
**Causes**:
1. No credentials configured → Run `gcloud auth application-default login`
2. Wrong project ID → Check `GOOGLE_CLOUD_PROJECT_ID` environment variable
3. Missing Firestore permissions → Ensure your account has `roles/datastore.user`

**Check permissions**:
```bash
gcloud projects get-iam-policy kukbuk-tf \
  --flatten="bindings[].members" \
  --filter="bindings.members:user:$(gcloud config get-value account)"
```

### Error: "404 Not Found from GET https://www.googleapis.com/drive/v3/files"
**Cause**: User profile doesn't have a `folderId` configured.

**Solution**: This is now handled automatically by calling `getOrCreateFolder()` if `folderId` is null. Make sure the user has connected their Google Drive storage first via `/storage/connect` endpoint.

### Error: "No storage configuration found for user"
**Cause**: User hasn't connected Google Drive yet.

**Solution**: User needs to call `/storage/connect` endpoint to authorize Google Drive access and store credentials in Firestore.

## Firestore Data Structure

Your Firestore database should have documents in the `users` collection with this structure:

```
users/{userId}
  - uid: string (Firebase UID)
  - userId: string (same as uid, legacy field)
  - email: string
  - displayName: string
  - createdAt: timestamp
  - storage: map
      - type: "googleDrive"
      - connected: boolean
      - accessToken: string (encrypted)
      - refreshToken: string (encrypted)
      - expiresAt: timestamp
      - connectedAt: timestamp
      - folderId: string (optional)
      - folderName: string (optional)
```

## Next Steps

1. ✅ Tests all pass
2. ✅ Firestore deserialization fixed
3. ✅ Null folderId handling added
4. ✅ Better error logging added

You can now run the application locally and it should properly connect to Firestore and handle user storage configurations correctly!
