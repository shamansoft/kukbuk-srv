# Google Drive Integration
Add persisting a recipe after it was organized by the backend to google drive.
The POST /recipe endpoint should receive auth-token as a header. After the recipe yaml file is created - store the recipe in the user's google drive's kukbuk directory. 

## Authentication Framework

Create GoogleDriveService class for Drive operations
Implement authentication validation middleware
Extract auth token from request headers

## Google Drive Integration

Add Google Drive Java API dependencies
Create methods to:

Verify token validity
Check for/create "kukbuk" directory
Generate clean filenames from recipe titles
Create/update YAML files in Drive

## Controller Modifications

Update CookbookController.createRecipe() to:

Extract auth token from headers
Validate the token
Call Drive service after transformation
Return Drive file ID and URL in response

## Configuration & Security

Add Drive API configuration properties
Implement request/response logging
Add security headers and CORS updates