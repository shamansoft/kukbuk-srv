#!/bin/bash

# Validate Firestore permissions for the kukbuk-tf project
# This script checks if the Cloud Run service account has the correct permissions

set -e

PROJECT_ID="kukbuk-tf"
SERVICE_ACCOUNT="cookbook-cloudrun-sa@${PROJECT_ID}.iam.gserviceaccount.com"
REGION="us-west1"
SERVICE_NAME="cookbook"

echo "🔍 Validating Firestore permissions for kukbuk-tf project"
echo "=================================================="

# Check if service account exists
echo "1. Checking service account existence..."
if gcloud iam service-accounts describe $SERVICE_ACCOUNT --project=$PROJECT_ID >/dev/null 2>&1; then
    echo "✅ Service account exists: $SERVICE_ACCOUNT"
else
    echo "❌ Service account does not exist: $SERVICE_ACCOUNT"
    echo "Run: terraform apply to create it"
    exit 1
fi

# Check IAM permissions
echo -e "\n2. Checking IAM permissions..."
iam_policy=$(gcloud projects get-iam-policy $PROJECT_ID --format=json)

# Check datastore.user role
if echo "$iam_policy" | jq -r '.bindings[] | select(.role == "roles/datastore.user") | .members[]' | grep -q "$SERVICE_ACCOUNT"; then
    echo "✅ Has datastore.user role"
else
    echo "❌ Missing datastore.user role"
    echo "This is required for Firestore read/write operations"
fi

# Check datastore.viewer role
if echo "$iam_policy" | jq -r '.bindings[] | select(.role == "roles/datastore.viewer") | .members[]' | grep -q "$SERVICE_ACCOUNT"; then
    echo "✅ Has datastore.viewer role"
else
    echo "❌ Missing datastore.viewer role"
    echo "This is required for Firestore metadata access"
fi

# Check secretmanager.secretAccessor role
if echo "$iam_policy" | jq -r '.bindings[] | select(.role == "roles/secretmanager.secretAccessor") | .members[]' | grep -q "$SERVICE_ACCOUNT"; then
    echo "✅ Has secretmanager.secretAccessor role"
else
    echo "❌ Missing secretmanager.secretAccessor role"
    echo "This is required for accessing API keys from Secret Manager"
fi

# Check Firestore database
echo -e "\n3. Checking Firestore database..."
if gcloud firestore databases describe --project=$PROJECT_ID >/dev/null 2>&1; then
    echo "✅ Firestore database exists"
    db_location=$(gcloud firestore databases describe --project=$PROJECT_ID --format="value(locationId)")
    echo "   Location: $db_location"
else
    echo "❌ Firestore database does not exist"
    echo "Run: terraform apply to create it"
fi

# Check Cloud Run service
echo -e "\n4. Checking Cloud Run service configuration..."
if gcloud run services describe $SERVICE_NAME --region=$REGION --project=$PROJECT_ID >/dev/null 2>&1; then
    echo "✅ Cloud Run service exists"
    
    # Check service account attachment
    current_sa=$(gcloud run services describe $SERVICE_NAME --region=$REGION --project=$PROJECT_ID --format="value(spec.template.spec.serviceAccountName)")
    if [ "$current_sa" = "$SERVICE_ACCOUNT" ]; then
        echo "✅ Correct service account attached to Cloud Run"
    else
        echo "❌ Wrong service account attached to Cloud Run"
        echo "   Current: $current_sa"
        echo "   Expected: $SERVICE_ACCOUNT"
        echo "Run: terraform apply to fix this"
    fi
    
    # Check environment variables
    echo -e "\n   Environment variables:"
    env_vars=$(gcloud run services describe $SERVICE_NAME --region=$REGION --project=$PROJECT_ID --format="value(spec.template.spec.template.spec.containers[0].env[].name,spec.template.spec.template.spec.containers[0].env[].value)")
    echo -e "All Environment: $env_vars" | sed 's/ / = /g' | sed 's/^/   /'

    if echo "$env_vars" | grep -q "FIRESTORE_PROJECT_ID"; then
        echo "   ✅ FIRESTORE_PROJECT_ID is set"
    else
        echo "   ❌ FIRESTORE_PROJECT_ID is missing"
    fi
    
    if echo "$env_vars" | grep -q "GOOGLE_CLOUD_PROJECT"; then
        echo "   ✅ GOOGLE_CLOUD_PROJECT is set"
    else
        echo "   ❌ GOOGLE_CLOUD_PROJECT is missing"
    fi
    
    if echo "$env_vars" | grep -q "SPRING_PROFILES_ACTIVE"; then
        echo "   ✅ SPRING_PROFILES_ACTIVE is set"
    else
        echo "   ❌ SPRING_PROFILES_ACTIVE is missing"
    fi
else
    echo "❌ Cloud Run service does not exist"
    echo "Run: terraform apply to create it"
fi

# Check required APIs
echo -e "\n5. Checking required APIs..."
enabled_apis=$(gcloud services list --enabled --project=$PROJECT_ID --format="value(name)")

required_apis=("firestore.googleapis.com" "run.googleapis.com" "secretmanager.googleapis.com")
for api in "${required_apis[@]}"; do
    if echo "$enabled_apis" | grep -q "$api"; then
        echo "✅ $api is enabled"
    else
        echo "❌ $api is not enabled"
    fi
done

# Check Cloud Run IAM configuration
echo -e "\n6. Checking Cloud Run IAM configuration..."
if gcloud run services describe $SERVICE_NAME --region=$REGION --project=$PROJECT_ID >/dev/null 2>&1; then
    iam_members=$(gcloud run services get-iam-policy $SERVICE_NAME --region=$REGION --project=$PROJECT_ID --format="value(bindings[0].members)")
    
    if echo "$iam_members" | grep -q "allUsers"; then
        echo "✅ Cloud Run allows unauthenticated access (allUsers)"
    elif echo "$iam_members" | grep -q "allAuthenticatedUsers"; then
        echo "⚠️  Cloud Run requires authentication (allAuthenticatedUsers)"
        echo "   This will cause health check failures"
    else
        echo "❌ Cloud Run IAM configuration unclear"
        echo "   Members: $iam_members"
    fi
fi

# Test application if service is running
echo -e "\n7. Testing application health..."
if gcloud run services describe $SERVICE_NAME --region=$REGION --project=$PROJECT_ID >/dev/null 2>&1; then
    service_url=$(gcloud run services describe $SERVICE_NAME --region=$REGION --project=$PROJECT_ID --format="value(status.url)")
    
    echo "   Testing: $service_url/actuator/health"
    
    # Test with curl and capture both response and HTTP status
    health_response=$(curl -s -w "\n%{http_code}" "$service_url/actuator/health" 2>/dev/null || echo -e "\nERROR")
    http_status=$(echo "$health_response" | tail -n1)
    response_body=$(echo "$health_response" | head -n -1)
    
    if [ "$http_status" = "200" ] && echo "$response_body" | grep -q "UP"; then
        echo "✅ Application health check passed"
        echo "   Response: $response_body"
    elif [ "$http_status" = "403" ]; then
        echo "❌ Health check failed: HTTP 403 Forbidden"
        echo "   This indicates authentication is required"
        echo "   Run: terraform apply to fix IAM configuration"
    elif [ "$http_status" = "ERROR" ]; then
        echo "❌ Health check failed: Connection error"
        echo "   Service may not be deployed or network issue"
    else
        echo "❌ Health check failed: HTTP $http_status"
        echo "   Response: $response_body"
        echo "   Check logs: gcloud run services logs read $SERVICE_NAME --region=$REGION --project=$PROJECT_ID"
    fi
    
    echo "   Service URL: $service_url"
fi

echo -e "\n=================================================="
echo "✅ Validation complete!"
echo -e "\nIf you see any ❌ items above, run 'terraform apply' to fix them."
echo -e "For detailed troubleshooting, see: FIRESTORE_TROUBLESHOOTING.md"