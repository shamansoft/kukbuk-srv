#!/bin/bash

# Get image tag from command line argument or use "latest" as default
IMAGE_TAG=${1:-latest}

# Deploy to Cloud Run
gcloud run deploy cookbook \
  --image gcr.io/cookbook-451120/cookbook:$IMAGE_TAG \
  --platform managed \
  --region us-west1 \
  --port 8080 \
  --set-secrets="COOKBOOK_GEMINI_API_KEY=gemini-api-key:latest" \
  --set-secrets="COOKBOOK_GOOGLE_OAUTH_ID=google-oauth-id:latest" \
  --set-env-vars SPRING_PROFILES_ACTIVE=gcp

# Optional: Print the deployed service URL
gcloud run services describe cookbook \
  --region us-west1 \
  --format='value(status.url)'
echo "Deployed $1"
