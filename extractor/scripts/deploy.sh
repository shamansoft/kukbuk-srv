#!/bin/bash

# Get image tag from command line argument or use "latest" as default
IMAGE_TAG=${1:-latest}

CLEANED_TAG=$(echo "$IMAGE_TAG" | sed 's/\./-/g') # Replace periods with hyphens
if [[ "$CLEANED_TAG" =~ ^[0-9] ]]; then
  REVISION_TAG="v$CLEANED_TAG"
else
  REVISION_TAG="$CLEANED_TAG"
fi

echo "revision tag: $REVISION_TAG"
# Deploy to Cloud Run
gcloud run deploy cookbook \
  --image gcr.io/cookbook-451120/cookbook:$IMAGE_TAG \
  --platform managed \
  --region us-west1 \
  --port 8080 \
  --set-secrets="COOKBOOK_GEMINI_API_KEY=gemini-api-key:latest" \
  --set-secrets="COOKBOOK_GOOGLE_OAUTH_ID=google-oauth-id:latest" \
  --set-env-vars SPRING_PROFILES_ACTIVE=gcp \
  --tag "$REVISION_TAG" || { echo "Deployment failed"; exit 1; }

# Optional: Print the deployed service URL
gcloud run services describe cookbook \
  --region us-west1 \
  --format='value(status.url)'
echo "Deployed $1"
