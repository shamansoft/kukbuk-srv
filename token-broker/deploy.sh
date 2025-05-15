#!/bin/bash

# Deploy to Cloud Run
gcloud functions deploy token-broker \
  --runtime nodejs20 \
  --trigger-http \
  --allow-unauthenticated \
  --entry-point getIdToken \
  --region us-west1

# Optional: Print the deployed service URL
gcloud run services describe token-broker \
  --region us-west1 \
  --format='value(status.url)'
echo "Deployed $1"
