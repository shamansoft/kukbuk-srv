# User Stories and Technical Tasks

## Google Authentication

### As a user, I want to sign in to the extension with my Google account
- Add Google OAuth integration to Chrome extension
- Implement sign-in UI element in the extension popup
- Store authentication token securely in extension storage
- Add sign-out functionality

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