# Metrics Guide

This guide explains how to view and monitor application metrics when running locally.

## HTML Preprocessing Metrics

The HTML preprocessing feature emits several metrics to track performance and effectiveness.

## Running Locally

### 1. Start the Application

Make sure you have the required environment variable set:

```bash
export COOKBOOK_GEMINI_API_KEY=your_api_key_here
./gradlew :cookbook:bootRun
```

### 2. Expose Metrics Endpoint

The metrics are available through Spring Boot Actuator. Ensure "metrics" is included in the exposed endpoints.

**Update `application.yaml`:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,httpexchanges,env,info,firestore,store-metrics,metrics
```

Or use this quick command:

```bash
sed -i '' 's/include: health,httpexchanges,env,info,firestore,store-metrics/include: health,httpexchanges,env,info,firestore,store-metrics,metrics/' extractor/src/main/resources/application.yaml
```

### 3. Trigger Preprocessing

Make a recipe creation request to generate metrics:

```bash
curl -X POST http://localhost:8080/v1/recipes?compression=none \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_FIREBASE_TOKEN" \
  -d '{
    "html": "<html><body><h1>Recipe</h1><p>Some content</p></body></html>",
    "title": "Test Recipe",
    "url": "https://example.com/recipe"
  }'
```

## Available Metrics

### List All Metrics

View all available metrics:

```bash
curl http://localhost:8080/actuator/metrics | jq
```

### HTML Preprocessing Metrics

#### 1. Strategy Usage Counter

Shows which preprocessing strategy was used (STRUCTURED_DATA, SECTION_BASED, CONTENT_FILTER, FALLBACK, DISABLED).

```bash
curl http://localhost:8080/actuator/metrics/html.preprocessing.strategy | jq
```

**Example output:**
```json
{
  "name": "html.preprocessing.strategy",
  "description": null,
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 5.0
    }
  ],
  "availableTags": [
    {
      "tag": "type",
      "values": ["STRUCTURED_DATA", "SECTION_BASED", "CONTENT_FILTER", "FALLBACK"]
    }
  ]
}
```

**View specific strategy count:**
```bash
curl "http://localhost:8080/actuator/metrics/html.preprocessing.strategy?tag=type:STRUCTURED_DATA" | jq
```

#### 2. Reduction Ratio Gauge

Current reduction ratio (0.0-1.0, where 0.92 = 92% reduction).

```bash
curl http://localhost:8080/actuator/metrics/html.preprocessing.reduction_ratio | jq
```

#### 3. Original HTML Size Distribution

Distribution of original HTML sizes before preprocessing.

```bash
curl http://localhost:8080/actuator/metrics/html.preprocessing.original_size | jq
```

**Example output:**
```json
{
  "name": "html.preprocessing.original_size",
  "description": null,
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 10.0
    },
    {
      "statistic": "TOTAL",
      "value": 1500000.0
    },
    {
      "statistic": "MAX",
      "value": 250000.0
    }
  ],
  "availableTags": []
}
```

#### 4. Cleaned HTML Size Distribution

Distribution of cleaned HTML sizes after preprocessing.

```bash
curl http://localhost:8080/actuator/metrics/html.preprocessing.cleaned_size | jq
```

#### 5. Error Counter

Count of preprocessing errors by error type.

```bash
curl http://localhost:8080/actuator/metrics/html.preprocessing.errors | jq
```

**View errors by type:**
```bash
curl "http://localhost:8080/actuator/metrics/html.preprocessing.errors?tag=error_type:IOException" | jq
```

## Viewing Logs

Preprocessing results are also logged for each request.

### Real-time Logs

Watch logs in real-time:

```bash
./gradlew :cookbook:bootRun | grep "HTML preprocessing"
```

### Search Logs

Search for preprocessing entries:

```bash
grep "HTML preprocessing" logs/spring.log
```

### Example Log Entries

```
INFO  RecipeService - HTML preprocessing - URL: https://example.com/recipe, Strategy: STRUCTURED_DATA, 150000 → 1800 chars (98.8% reduction)
INFO  RecipeService - HTML preprocessing - URL: https://example.com/blog, Strategy: SECTION_BASED, 80000 → 15000 chars (81.3% reduction)
WARN  RecipeService - Cleaned HTML too small (250 chars), falling back to raw HTML
ERROR RecipeService - HTML preprocessing failed for URL: https://example.com/broken, using raw HTML
```

## Interpreting Metrics

### Strategy Distribution

A healthy distribution should show:
- **STRUCTURED_DATA**: 30-40% (sites with proper JSON-LD markup)
- **SECTION_BASED**: 40-50% (sites with good HTML structure)
- **CONTENT_FILTER**: 10-20% (fallback for poorly structured sites)
- **FALLBACK**: <5% (only for very small or problematic pages)
- **DISABLED**: 0% (unless preprocessing is disabled in config)

### Reduction Ratios

Expected reduction ratios by strategy:
- **STRUCTURED_DATA**: 90-95% reduction (JSON-LD is very compact)
- **SECTION_BASED**: 60-80% reduction (extracts recipe sections only)
- **CONTENT_FILTER**: 30-50% reduction (removes scripts, styles, nav, etc.)
- **FALLBACK**: 0% reduction (uses raw HTML)

### Performance Impact

Monitor these metrics to assess preprocessing effectiveness:

1. **Average reduction ratio** - Should be >60% across all requests
2. **Error rate** - Should be <1% of total requests
3. **Fallback rate** - Should be <5% of total requests

## Production Monitoring

In production (Cloud Run), these metrics are automatically exported to Google Cloud Monitoring (Stackdriver) via Micrometer.

### Cloud Console

View metrics in Google Cloud Console:

1. Navigate to **Cloud Run** → Your service
2. Click **Metrics** tab
3. Search for "html.preprocessing"
4. Create custom dashboards and alerts

### Stackdriver Metrics

Metrics are exported with prefix: `custom.googleapis.com/`

Example metric paths:
- `custom.googleapis.com/html/preprocessing/strategy`
- `custom.googleapis.com/html/preprocessing/reduction_ratio`
- `custom.googleapis.com/html/preprocessing/original_size`
- `custom.googleapis.com/html/preprocessing/cleaned_size`

### Setting Up Alerts

Create alerts for:
- **High error rate**: Alert when `html.preprocessing.errors` > 5% of requests
- **Low reduction ratio**: Alert when average reduction ratio < 50%
- **High fallback rate**: Alert when FALLBACK strategy > 10% of requests

## Configuration

### Enable/Disable Preprocessing

Toggle preprocessing via environment variable or configuration:

```yaml
cookbook:
  html-preprocessing:
    enabled: true  # Set to false to disable
```

Or via environment variable:
```bash
export HTML_PREPROCESSING_ENABLED=false
```

### Adjust Thresholds

Tune preprocessing thresholds in `application.yaml`:

```yaml
cookbook:
  html-preprocessing:
    structured-data:
      min-completeness: 70  # 0-100, required fields score
    section-based:
      min-confidence: 70    # 0-100, keyword density score
    content-filter:
      min-output-size: 500  # chars, minimum after filtering
    fallback:
      min-safe-size: 300    # chars, absolute minimum threshold
```

## Troubleshooting

### No Metrics Showing

**Check if metrics endpoint is enabled:**
```bash
curl http://localhost:8080/actuator | jq '.["_links"]'
```

Should include:
```json
{
  "metrics": {
    "href": "http://localhost:8080/actuator/metrics",
    "templated": false
  }
}
```

**If missing**, add to `application.yaml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics
```

### High Error Rate

Check logs for error details:
```bash
grep "HTML preprocessing failed" logs/spring.log
```

Common causes:
- Malformed HTML that Jsoup can't parse
- JSON-LD with invalid JSON syntax
- Network timeouts (if fetching external resources)

### Low Reduction Ratios

Possible causes:
- Sites without structured data or poor HTML structure
- Threshold too high (sections not meeting min-confidence)
- Heavily customized markup that doesn't match keyword patterns

**Solution**: Adjust thresholds or add custom keywords:
```yaml
cookbook:
  html-preprocessing:
    section-based:
      keywords:
        - ingredients
        - instructions
        - your-custom-keyword
```

## See Also

- [HTML Preprocessing Design Document](plans/2026-01-23-html-preprocessing-design.md)
- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)
