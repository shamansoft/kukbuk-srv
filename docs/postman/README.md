# Postman Collection - Cookbook API Test Endpoints

This directory contains Postman collections for testing the Cookbook API in local/development environments.

## 📋 Collections

### `Cookbook-API-Test.postman_collection.json`

Test collection for the `/test-transform` endpoint that allows unauthenticated testing of Gemini AI recipe extraction.

**⚠️ Important**: These endpoints are **ONLY available in local/dev environments**, NOT in production.

## 🚀 Quick Start

### 1. Start the Local Server

Run the Spring Boot application with the `local` profile:

```bash
# From the project root (/Users/alexey/dev/sar/sar-srv/)
./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'
```

The server will start on `http://localhost:8080` by default.

### 2. Import Collection into Postman

1. Open Postman
2. Click **Import** button (top left)
3. Select the file: `Cookbook-API-Test.postman_collection.json`
4. The collection will appear in your workspace

### 3. Configure Variables

The collection includes a `baseUrl` variable set to `http://localhost:8080` by default.

To change it:
1. Click on the collection name
2. Go to the **Variables** tab
3. Update `baseUrl` if your server runs on a different port

### 4. Run Requests

The collection includes several test scenarios:

- ✅ **Transform from URL** - Extract recipe from a live URL
- ✅ **Transform from HTML Text** - Extract recipe from raw HTML
- ✅ **Transform Non-Recipe Content** - Test Gemini's detection of non-recipe pages
- ✅ **Transform Complex Recipe** - Test with detailed recipe structure
- ❌ **Error - Missing Both URL and Text** - Validate error handling

## 📖 Test Endpoint Details

### POST `/v1/recipes/test-transform`

Transforms HTML content into structured recipe YAML using Gemini AI.

#### Request Body

```json
{
  "url": "https://www.allrecipes.com/recipe/24074/alysias-basic-meat-lasagna/",
  "text": "<html>...</html>"
}
```

**Fields:**
- `url` (optional): URL to fetch recipe from
- `text` (optional): Raw HTML text to transform
- **Note**: Either `url` OR `text` must be provided (not both)

#### Response

**Success (200 OK):**
```yaml
is_recipe: true
schema_version: "1.0.0"
recipe_version: "1.0.0"
metadata:
  title: "Classic Lasagna"
  servings: 8
  # ... more fields
ingredients:
  - name: "ground beef"
    amount: "2 lbs"
  # ... more ingredients
instructions:
  - step: "Preheat oven to 375°F"
  # ... more steps
```

**Headers:**
- `X-Is-Recipe: true` - Indicates if content is a recipe
- `X-Recipe-Title: Classic Lasagna` - Extracted recipe title

**Non-Recipe Response (200 OK):**
```yaml
is_recipe: false
# Gemini determined this content is not a cooking recipe
```

**Error (400 Bad Request):**
```
Error: Either 'url' or 'text' field is required
```

## 🧪 Running Tests

Each request in the collection includes automated tests:

1. Status code validation
2. Response format validation
3. Header validation
4. Content validation

To run all tests:
1. Click on the collection name
2. Click **Run** button
3. Select all requests
4. Click **Run Cookbook API - Test Endpoints**

Postman will execute all requests and show test results.

## 📝 Example Usage Scenarios

### Scenario 1: Test with Real Recipe URL

```json
POST http://localhost:8080/v1/recipes/test-transform
Content-Type: application/json

{
  "url": "https://www.bbcgoodfood.com/recipes/classic-lasagne"
}
```

### Scenario 2: Test with Custom HTML

```json
POST http://localhost:8080/v1/recipes/test-transform
Content-Type: application/json

{
  "text": "<html><body><h1>Chocolate Cookies</h1><h2>Ingredients</h2><ul><li>2 cups flour</li><li>1 cup sugar</li></ul><h2>Instructions</h2><ol><li>Mix ingredients</li><li>Bake at 350°F</li></ol></body></html>"
}
```

### Scenario 3: Test Non-Recipe Detection

```json
POST http://localhost:8080/v1/recipes/test-transform
Content-Type: application/json

{
  "text": "<html><body><h1>About Us</h1><p>We are a company...</p></body></html>"
}
```

## 🔍 Troubleshooting

### Error: Connection Refused

**Problem**: Can't connect to `http://localhost:8080`

**Solution**:
- Verify the server is running: `./gradlew :cookbook:bootRun --args='--spring.profiles.active=local'`
- Check the console output for the actual port
- Update the `baseUrl` variable in Postman if using a different port

### Error: 404 Not Found

**Problem**: Endpoint returns 404

**Solution**:
- Ensure you're running with the correct profile: `--spring.profiles.active=local`
- The `TestController` is disabled in production profiles (`prod`, `gcp`)
- Check that the URL is exactly: `/v1/recipes/test-transform`

### Error: 500 Internal Server Error

**Problem**: Server error during transformation

**Solution**:
- Check server logs for detailed error messages
- Ensure `COOKBOOK_GEMINI_API_KEY` environment variable is set
- Verify the HTML content is valid
- Check if the URL is accessible

## 🔐 Environment Variables

The server requires these environment variables:

```bash
export COOKBOOK_GEMINI_API_KEY="your-gemini-api-key"
export COOKBOOK_GOOGLE_OAUTH_ID="your-google-oauth-client-id"  # Optional for test endpoint
```

## 📚 Related Documentation

- **Main README**: `../../extractor/README.md`
- **API Documentation**: `../../docs/API.md`
- **CLAUDE Instructions**: `../../CLAUDE.md`

## ⚙️ Profile Configuration

The `TestController` is controlled by Spring profiles:

| Profile | Test Endpoint Available? |
|---------|-------------------------|
| `local` | ✅ Yes |
| `dev`   | ✅ Yes |
| `prod`  | ❌ No |
| `gcp`   | ❌ No |

**Profile annotation**: `@Profile("!prod & !gcp")`

This ensures test endpoints are never exposed in production.

## 💡 Tips

1. **Save Responses**: Use Postman's "Save Response" feature to compare transformations
2. **Environment Variables**: Create separate Postman environments for local/dev/staging
3. **Collection Variables**: Add your own variables for frequently used URLs
4. **Test Scripts**: Modify test scripts to add custom validations
5. **Export Results**: Use "Export Results" after running tests to save a report

## 🐛 Known Issues

- Large HTML pages (>1MB) may timeout - consider increasing Gemini timeout in config
- Some recipe sites use JavaScript rendering - URLs must return server-side HTML
- Rate limiting: Gemini API has rate limits, excessive testing may hit limits

## 📞 Support

For issues or questions:
- Check the main project documentation
- Review server logs: `./gradlew :cookbook:bootRun`
- Contact: `@khisamutdinov` (code owner)