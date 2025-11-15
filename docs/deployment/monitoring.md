# Monitoring & Alerts

This document describes monitoring, logging, and alerting for the cookbook service deployed on Google Cloud Run.

## Health Checks

### Deployment Health Check

**Built into CI/CD workflow**:
```yaml
- name: Test deployment
  run: |
    echo "Testing health endpoint..."
    for i in {1..30}; do
      if curl -f ${{ steps.deploy.outputs.URL }}/actuator/health; then
        echo "Health check passed!"
        exit 0
      fi
      echo "Attempt $i/30 failed, retrying in 10s..."
      sleep 10
    done
    echo "Health check failed after 30 attempts"
    exit 1
```

**Why 30 retries**: Native GraalVM images can take 30-60 seconds to start up.

### Cloud Run Health Checks

**Configured in Terraform** (`terraform/main.tf`):

```hcl
resource "google_cloud_run_service" "cookbook" {
  # ...

  template {
    spec {
      containers {
        # Startup probe
        startup_probe {
          http_get {
            path = "/actuator/health"
            port = 8080
          }
          initial_delay_seconds = 10
          period_seconds        = 3
          timeout_seconds       = 2
          failure_threshold     = 10
        }

        # Liveness probe (automatic for HTTP serving)
        liveness_probe {
          http_get {
            path = "/actuator/health"
            port = 8080
          }
          period_seconds    = 10
          timeout_seconds   = 2
          failure_threshold = 3
        }
      }
    }
  }
}
```

### Manual Health Check

Test the deployed service:

```bash
# Get service URL
CLOUD_RUN_URL=$(gcloud run services describe cookbook \
  --region=us-west1 \
  --project=kukbuk-tf \
  --format='value(status.url)')

# Test health endpoint
curl -f "$CLOUD_RUN_URL/actuator/health"

# Expected response:
# {"status":"UP"}
```

### Health Check Endpoints

Spring Boot Actuator provides several health endpoints:

| Endpoint | Purpose | Authentication |
|----------|---------|----------------|
| `/actuator/health` | Overall service health | Public |
| `/actuator/health/liveness` | Liveness check | Public |
| `/actuator/health/readiness` | Readiness check | Public |
| `/actuator/info` | Application info | Public |

## Logging

### View Logs from GitHub Actions

During deployment, GitHub Actions shows real-time logs:

1. Go to **Actions** tab in GitHub
2. Click on the latest workflow run
3. Expand steps to see detailed logs

### View Cloud Run Service Logs

**Using gcloud CLI**:

```bash
# View recent logs (last 100 entries)
gcloud run services logs read cookbook \
  --region=us-west1 \
  --project=kukbuk-tf \
  --limit=100

# View logs from last hour
gcloud run services logs read cookbook \
  --region=us-west1 \
  --project=kukbuk-tf \
  --limit=200 \
  --format="table(timestamp,severity,textPayload)"

# Follow logs in real-time
gcloud run services logs tail cookbook \
  --region=us-west1 \
  --project=kukbuk-tf

# Filter for errors only
gcloud logging read "resource.type=cloud_run_revision AND severity>=ERROR" \
  --project=kukbuk-tf \
  --limit=50 \
  --format=json
```

**Using Cloud Console**:

1. Go to [Cloud Run Console](https://console.cloud.google.com/run)
2. Select the `cookbook` service
3. Click **LOGS** tab
4. Use filters to narrow down:
   - Severity: Error, Warning, Info
   - Time range
   - Text search

### Log Levels

The application uses standard log levels:

| Level | Purpose | Example |
|-------|---------|---------|
| ERROR | Application errors | Failed to extract recipe |
| WARN | Warnings, recoverable issues | Retry attempt 3 of 5 |
| INFO | Normal operations | Recipe extracted successfully |
| DEBUG | Detailed debugging info | Request payload: {...} |

### Structured Logging

Logs are structured as JSON for better parsing:

```json
{
  "timestamp": "2025-11-14T10:30:00.123Z",
  "severity": "INFO",
  "message": "Recipe extraction completed",
  "labels": {
    "version": "0.6.5",
    "service": "cookbook"
  },
  "httpRequest": {
    "requestMethod": "POST",
    "requestUrl": "/api/extract",
    "status": 200,
    "latency": "1.234s"
  }
}
```

### Useful Log Queries

**Find all errors in last 24 hours**:
```bash
gcloud logging read "resource.type=cloud_run_revision \
  AND severity>=ERROR \
  AND timestamp>=\"$(date -u -d '24 hours ago' '+%Y-%m-%dT%H:%M:%SZ')\"" \
  --project=kukbuk-tf \
  --format=json
```

**Find slow requests (>5 seconds)**:
```bash
gcloud logging read "resource.type=cloud_run_revision \
  AND httpRequest.latency>\"5.0s\"" \
  --project=kukbuk-tf \
  --limit=20
```

**Search logs by request ID**:
```bash
gcloud logging read "resource.type=cloud_run_revision \
  AND jsonPayload.requestId=\"abc123\"" \
  --project=kukbuk-tf
```

### Terraform Deployment Logs

```bash
# View Terraform output from CI/CD
# Go to GitHub Actions → Deployment run → Terraform Apply step

# Local Terraform logs
cd terraform
cat tofu.log
```

## Metrics

### Built-in Cloud Run Metrics

View in [Cloud Console → Cloud Run → cookbook → Metrics](https://console.cloud.google.com/run/detail/us-west1/cookbook/metrics):

**Request Metrics**:
- Request count
- Request latency (p50, p95, p99)
- Error rate (4xx, 5xx)

**Resource Metrics**:
- CPU utilization
- Memory utilization
- Container instance count
- Billable container time

**Startup Metrics**:
- Cold start count
- Cold start latency

### Custom Metrics (Recommended)

Consider adding custom metrics using Micrometer (Spring Boot):

```java
@Component
public class RecipeMetrics {
    private final MeterRegistry registry;

    public RecipeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordExtraction(String language, boolean success) {
        registry.counter("recipe.extractions",
            "language", language,
            "success", String.valueOf(success)
        ).increment();
    }

    public void recordExtractionTime(Duration duration) {
        registry.timer("recipe.extraction.time").record(duration);
    }
}
```

### View Metrics with gcloud

```bash
# CPU utilization
gcloud monitoring time-series list \
  --filter='metric.type="run.googleapis.com/container/cpu/utilizations"' \
  --project=kukbuk-tf

# Request count
gcloud monitoring time-series list \
  --filter='metric.type="run.googleapis.com/request_count"' \
  --project=kukbuk-tf
```

## Alerts (Not Yet Implemented)

**Recommended alerts to set up:**

### Critical Alerts

1. **Service Down**
   - Condition: Health check fails for 5 consecutive minutes
   - Notification: Immediate (PagerDuty, SMS)

2. **High Error Rate**
   - Condition: 5xx errors > 5% of requests in 10 minutes
   - Notification: Immediate (PagerDuty, Slack)

3. **Service Unavailable**
   - Condition: No requests processed for 15 minutes
   - Notification: Immediate

### Warning Alerts

4. **Elevated Latency**
   - Condition: p95 latency > 5 seconds for 10 minutes
   - Notification: Slack, Email

5. **High Memory Usage**
   - Condition: Memory > 90% for 15 minutes
   - Notification: Slack, Email

6. **High CPU Usage**
   - Condition: CPU > 80% for 15 minutes
   - Notification: Slack, Email

7. **Deployment Failed**
   - Condition: GitHub Actions workflow fails
   - Notification: Slack, Email

### Setting Up Alerts with gcloud

**Example: Create error rate alert**:

```bash
# Create notification channel (Slack)
gcloud alpha monitoring channels create \
  --display-name="Ops Slack Channel" \
  --type=slack \
  --channel-labels=url=WEBHOOK_URL \
  --project=kukbuk-tf

# Create alert policy
gcloud alpha monitoring policies create \
  --notification-channels=CHANNEL_ID \
  --display-name="High Error Rate" \
  --condition-display-name="5xx errors > 5%" \
  --condition-threshold-value=5.0 \
  --condition-threshold-duration=600s \
  --condition-filter='resource.type="cloud_run_revision" AND metric.type="run.googleapis.com/request_count" AND metric.label.response_code_class="5xx"' \
  --project=kukbuk-tf
```

### Alert Configuration in Terraform (Future)

```hcl
resource "google_monitoring_notification_channel" "slack" {
  display_name = "Ops Slack Channel"
  type         = "slack"

  labels = {
    url = var.slack_webhook_url
  }
}

resource "google_monitoring_alert_policy" "high_error_rate" {
  display_name = "High 5xx Error Rate"
  combiner     = "OR"

  conditions {
    display_name = "5xx errors > 5%"

    condition_threshold {
      filter          = "resource.type=\"cloud_run_revision\" AND metric.type=\"run.googleapis.com/request_count\" AND metric.label.response_code_class=\"5xx\""
      duration        = "600s"
      comparison      = "COMPARISON_GT"
      threshold_value = 5.0

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.slack.id]
}
```

## Dashboards

### Cloud Run Dashboard

View built-in dashboard:
1. Go to [Cloud Run Console](https://console.cloud.google.com/run)
2. Select `cookbook` service
3. View **METRICS** tab

Shows:
- Request count over time
- Latency distribution
- Instance count
- Error rates

### Custom Dashboard (Recommended)

Create a custom Cloud Monitoring dashboard:

1. Go to [Cloud Monitoring → Dashboards](https://console.cloud.google.com/monitoring/dashboards)
2. Create new dashboard
3. Add charts for:
   - Request rate
   - Error rate
   - Latency (p50, p95, p99)
   - Active instances
   - Memory usage
   - CPU usage
   - Deployment events

### GitHub Actions Dashboard

Monitor CI/CD pipeline:
1. Go to repository **Actions** tab
2. View workflow runs
3. Filter by status (success, failure)
4. Track deployment frequency

## Observability Best Practices

### 1. Correlation IDs

Add request IDs to correlate logs across services:

```java
@Component
public class RequestIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        chain.doFilter(request, response);
        MDC.clear();
    }
}
```

### 2. Trace Sampling

For detailed request tracing, consider Cloud Trace:

```yaml
# application.yaml
spring:
  sleuth:
    sampler:
      probability: 0.1  # Sample 10% of requests
```

### 3. Metrics Cardinality

Keep metric labels low-cardinality:
- ✅ Good: `language=en`, `status=success`
- ❌ Bad: `user_id=12345`, `url=/api/recipes/abc123`

### 4. Log Retention

Configure log retention policies:

```bash
# Set retention to 30 days
gcloud logging sinks update _Default \
  --log-filter='resource.type="cloud_run_revision"' \
  --retention-days=30 \
  --project=kukbuk-tf
```

## Troubleshooting Workflows

### Deployment Failed

1. Check GitHub Actions logs
2. Look for failed step (test, build, deploy)
3. Review error message
4. Check relevant service (GCP, Docker Registry)

### Service Returning 500 Errors

1. Check Cloud Run logs for errors
2. Look for stack traces
3. Check external dependencies (Gemini API, Firestore)
4. Verify secrets are accessible

### High Latency

1. Check Cloud Run metrics for CPU/memory bottlenecks
2. Review slow query logs
3. Check external API latency (Gemini)
4. Consider scaling up resources

### Out of Memory

1. Check memory usage metrics
2. Review logs for OOM errors
3. Increase Cloud Run memory limit:
   ```bash
   cd terraform
   # Edit main.tf to increase memory
   # resources { limits { memory = "1Gi" } }
   tofu apply
   ```

## Notifications (Optional - Not Yet Implemented)

### Recommended Notification Channels

1. **Slack** - For team notifications
2. **Email** - For less urgent alerts
3. **PagerDuty** - For critical production issues
4. **GitHub** - For deployment events

### Example: Slack Notification on Deployment

Add to GitHub Actions workflow:

```yaml
- name: Notify Slack on Success
  if: success()
  uses: slackapi/slack-github-action@v1
  with:
    webhook-url: ${{ secrets.SLACK_WEBHOOK }}
    payload: |
      {
        "text": "✅ Deployment v${{ steps.version.outputs.VERSION }} succeeded",
        "blocks": [
          {
            "type": "section",
            "text": {
              "type": "mrkdwn",
              "text": "*Deployment Successful*\n\nVersion: `${{ steps.version.outputs.VERSION }}`\nService: cookbook\nURL: ${{ steps.deploy.outputs.URL }}"
            }
          }
        ]
      }

- name: Notify Slack on Failure
  if: failure()
  uses: slackapi/slack-github-action@v1
  with:
    webhook-url: ${{ secrets.SLACK_WEBHOOK }}
    payload: |
      {
        "text": "❌ Deployment failed",
        "blocks": [
          {
            "type": "section",
            "text": {
              "type": "mrkdwn",
              "text": "*Deployment Failed*\n\nWorkflow: ${{ github.workflow }}\nRun: ${{ github.run_id }}"
            }
          }
        ]
      }
```

## Cost Monitoring

### Track Cloud Run Costs

```bash
# View billing for Cloud Run
gcloud billing accounts list

# View current month costs
gcloud alpha billing budgets list --billing-account=BILLING_ACCOUNT_ID

# Set up cost alert (recommended)
gcloud alpha billing budgets create \
  --billing-account=BILLING_ACCOUNT_ID \
  --display-name="Cloud Run Monthly Budget" \
  --budget-amount=100USD \
  --threshold-rule=percent=50 \
  --threshold-rule=percent=90 \
  --threshold-rule=percent=100
```

### Cost Optimization Tips

1. **Right-size resources** - Don't over-provision CPU/memory
2. **Enable concurrency** - Handle multiple requests per instance
3. **Use minimum instances wisely** - Avoid keeping instances always running
4. **Monitor cold starts** - Balance cost vs. cold start frequency

## Related Documents

- [Deployment Strategy](./strategy.md)
- [Rollback Procedures](./rollback.md)
- [Production Readiness](./production-readiness.md)
