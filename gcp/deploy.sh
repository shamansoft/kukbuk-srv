#!/bin/bash

# Deploy to Cloud Run
gcloud run deploy cookbook \
  --image gcr.io/cookbook-451120/cookbook \
  --platform managed \
  --region us-west1 \
  --port 8080 \
  --set-secrets="COOKBOOK_GEMINI_API_KEY=gemini-api-key:latest" \
  --set-env-vars SPRING_PROFILES_ACTIVE=gcp

# Optional: Print the deployed service URL
gcloud run services describe cookbook \
  --region us-west1 \
  --format='value(status.url)'