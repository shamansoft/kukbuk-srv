# Firestore Database Schema for Recipe Storage

## Collection Structure

### `/users/{userId}`
User profile and settings
```json
{
  "email": "user@example.com",
  "displayName": "John Doe",
  "createdAt": "2025-01-01T00:00:00Z",
  "preferences": {
    "defaultServings": 4,
    "units": "metric",
    "language": "en"
  }
}
```

### `/users/{userId}/recipes/{recipeId}`
User's private recipes
```json
{
  "schema_version": "1.0.0",
  "recipe_version": "1.0.0",
  "metadata": {
    "title": "Chocolate Chip Cookies",
    "description": "Classic homemade cookies",
    "date_created": "2025-01-01T00:00:00Z",
    "date_modified": "2025-01-01T00:00:00Z",
    "servings": 24,
    "prep_time": "15m",
    "cook_time": "12m",
    "total_time": "27m",
    "difficulty": "easy",
    "cuisine": "American",
    "tags": ["dessert", "cookies", "baking"],
    "author": "John Doe",
    "source_url": "https://example.com/recipe",
    "language": "en"
  },
  "ingredients": [
    {
      "item": "flour",
      "amount": 2.25,
      "unit": "cups",
      "notes": "all-purpose",
      "component": "dough",
      "optional": false,
      "substitutions": [
        {
          "item": "gluten-free flour",
          "ratio": "1:1"
        }
      ]
    }
  ],
  "instructions": [
    {
      "step": 1,
      "description": "Preheat oven to **375°F** (190°C)",
      "time": "5m",
      "temperature": "375°F",
      "media": []
    }
  ],
  "nutrition": {
    "per_serving": {
      "calories": 180,
      "protein": "2g",
      "carbs": "25g",
      "fat": "8g",
      "fiber": "1g",
      "sugar": "12g"
    }
  },
  "storage": {
    "instructions": "Store in airtight container",
    "duration": "1 week"
  },
  "notes": "Recipe notes and tips",
  "rating": 4.5,
  "reviews_count": 0,
  "is_public": false,
  "created_by": "userId",
  "extraction_metadata": {
    "source_html_url": "https://example.com/recipe",
    "extracted_at": "2025-01-01T00:00:00Z",
    "extractor_version": "1.0.0"
  }
}
```

### `/public_recipes/{recipeId}`
Publicly shared recipes (same schema as user recipes but with visibility)
```json
{
  // Same schema as user recipes, plus:
  "author_id": "userId",
  "author_name": "John Doe",
  "shared_at": "2025-01-01T00:00:00Z",
  "visibility": "public",
  "likes_count": 0,
  "saves_count": 0
}
```

### `/templates/{templateId}`
Recipe templates and schemas
```json
{
  "name": "Basic Recipe Template",
  "version": "1.0.0",
  "schema": {
    // JSON schema definition
  },
  "example": {
    // Example recipe following the schema
  }
}
```

## Security Rules

The Firestore security rules ensure:
- ✅ **Users can only access their own recipes**
- ✅ **Public recipes are readable by authenticated users**
- ✅ **Only recipe authors can modify public recipes**
- ✅ **Templates are read-only for all authenticated users**
- ✅ **Users can manage their own profiles**

## Indexes

Recommended indexes for efficient queries:
- `public_recipes`: `author_id`, `created_at` (desc)
- `public_recipes`: `tags` (array), `created_at` (desc)
- `public_recipes`: `cuisine`, `difficulty`, `created_at` (desc)
- `users/{userId}/recipes`: `created_at` (desc)
- `users/{userId}/recipes`: `tags` (array), `created_at` (desc)

## Usage in Java Application

Environment variables available in your Cloud Run service:
- `FIREBASE_PROJECT_ID`: Your Firebase project ID
- `FIREBASE_ADMIN_KEY`: Service account key for Firebase Admin SDK

Add to your Spring Boot application:
```java
@Value("${FIREBASE_PROJECT_ID}")
private String firebaseProjectId;

@Value("${FIREBASE_ADMIN_KEY}")
private String firebaseAdminKey;
```