# User Stories and Technical Tasks

## Google Authentication

### As a user, I want to sign in to the extension with my Google account
- Add Google OAuth integration to Chrome extension
- Implement sign-in UI element in the extension popup
- Store authentication token securely in extension storage
- Add sign-out functionality

#### Extension Tasks

1. **Add Google OAuth Integration**
    * Implement OAuth 2.0 flow using Chrome Identity API
    * Configure extension permissions for identity in manifest.json
    * Create authorization request with appropriate scopes for Google Sign-In
    * Handle OAuth callbacks and token processing

2. **Implement Sign-in UI**
    * Add sign-in button to the extension popup
    * Create loading/processing state for authentication flow
    * Design user profile display after successful authentication
    * Implement error handling for failed authentication attempts

3. **Secure Token Storage**
    * Store authentication tokens in chrome.storage.local with encryption
    * Implement token refresh mechanism
    * Create helper functions to retrieve and validate tokens
    * Handle token expiration gracefully

4. **Sign-out Functionality**
    * Add sign-out button to the UI
    * Implement token revocation with Google
    * Clear local storage on sign-out
    * Reset UI state after sign-out

#### Backend Tasks

1. **Token Verification**
    * Implement Google token verification endpoint
    * Validate JWT tokens from Google
    * Extract and store relevant user information
    * Return session tokens for subsequent API calls

2. **User Management**
    * Create user database schema for Google accounts
    * Implement user creation/lookup based on Google profile
    * Add user role/permission management
    * Create API for user profile information

3. **Authentication Middleware**
    * Update API endpoints to require authentication
    * Implement token validation for all protected routes
    * Add proper error responses for unauthenticated requests
    * Create middleware for request logging with user context

4. **Token Refresh Handling**
    * Implement endpoint for token refresh
    * Configure token expiration policies
    * Add session invalidation mechanism
    * Handle cross-device sign-out

#### GCP Infrastructure Tasks

1. **Identity Platform Setup**
    * Enable Google Identity Platform in GCP
    * Configure OAuth client credentials
    * Set up proper redirect URIs for Chrome extension
    * Configure allowed origins for CORS

2. **IAM Configuration**
    * Create service accounts for token verification
    * Configure IAM roles and permissions
    * Set up audit logging for authentication events
    * Implement least privilege access principles

3. **Secret Management**
    * Store OAuth client secrets in Secret Manager
    * Configure access to secrets for Cloud Run service
    * Implement secret rotation policy
    * Add monitoring for secret access

4. **Security Enhancements**
    * Implement rate limiting for authentication endpoints
    * Configure Cloud Armor for additional protection
    * Set up monitoring for suspicious authentication activities
    * Create alerting for authentication failures

### As a user, I want the app to only access a specific folder in my Google Drive
- Request minimal scoped Drive permissions
- Allow user to select or create a designated "Recipes" folder
- Store folder ID in user preferences

## Recipe Storage

### As a user, I want my extracted recipes saved to Google Drive automatically
- Create service to upload YAML content to Google Drive
- Generate unique filenames based on recipe title
- Handle file conflicts by appending version numbers
- Implement error handling for upload failures

### As a user, I want to access my saved recipe from the extension
- Return Google Drive link in extension popup
- Add "Open in Drive" button after extraction
- Provide option to view recipe history
- Show thumbnail/preview if available

## Recipe Extraction Improvements

### As a user, I want more accurate recipe extraction
- Move Gemini prompt to dedicated file for easier maintenance
- Enhance prompt with additional examples and instructions
- Implement content pre-processing to clean HTML more effectively
- Add validation for YAML output against schema

### As a user, I want to see progress during extraction
- Add loading states with percentage/stages
- Show intelligent feedback based on current processing step
- Implement timeout handling with friendly error messages

## Technical Debt & Infrastructure

### Backend Improvements
- Migrate Gemini prompt to resource file
- Implement proper compression handling
- Add comprehensive error handling
- Refactor controller to support Google Drive integration
- Implement proper token validation for Google Auth

### CI/CD Pipeline
- Set up GitHub Actions workflow for Java backend
- Configure Chrome extension packaging in CI
- Automate deployment to GCP Cloud Run
- Implement version tagging

### Code Quality
- Add ESLint for JavaScript code
- Configure CheckStyle for Java code
- Set up JaCoCo for Java test coverage
- Implement SonarQube analysis
- Update CORS configuration for proper security

### Security Enhancements
- Implement proper token handling
- Configure CSP for Chrome extension
- Add rate limiting
- Secure API key management
- Implement request validation

## Additional Suggestions

### As a user, I want to edit the extracted recipe before saving
- Add simple YAML editor in extension popup
- Provide validation against schema
- Allow adding custom tags

### As a user, I want to categorize and organize my recipes
- Add metadata fields for categories
- Create folder structure in Google Drive
- Implement simple tagging system

### As a user, I want to share my recipe with others
- Add "Share" button that configures Drive sharing
- Generate public/private links
- Option to export as PDF

## Storage Strategy
### User Authentication Storage:
Store OAuth tokens in Chrome's extension storage (chrome.storage.local)
Use the token's refresh mechanism to maintain authentication

### Configuration Storage:
Keep user preferences in extension storage
Store the designated Google Drive folder ID after initial setup

### Recipe Storage:
Use Google Drive as your primary data store for YAML files
Implement search/filter by querying Drive's API directly
Use Drive's native versioning for file history

### Temporary Runtime Data:
Store processing data in memory during extraction
Clear it once the process completes