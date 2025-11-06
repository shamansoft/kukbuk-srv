#!/bin/bash

# Script to push Docker images to Google Container Registry
# Usage: ./push.sh <tag> [--dry-run]

# Initialize variables
TAG=""
DRY_RUN=false
PROJECT_ID="kukbuk-tf"

# Parse arguments
for arg in "$@"; do
    if [ "$arg" == "--dry-run" ]; then
        DRY_RUN=true
    elif [[ "$arg" != --* ]]; then
        # If not a flag, treat as tag
        TAG="$arg"
    fi
done

# Validate tag is provided
if [ -z "$TAG" ]; then
    echo "Error: No tag provided"
    echo "Usage: ./push.sh <tag> [--dry-run]"
    echo "Example: ./push.sh v1.0.0"
    exit 1
fi

IMAGE_NAME="gcr.io/$PROJECT_ID/cookbook:$TAG"

# Check if image exists locally
if ! docker image inspect "$IMAGE_NAME" > /dev/null 2>&1; then
    echo "Error: Image $IMAGE_NAME does not exist locally"
    echo "Build the image first using: ./build.sh $TAG"
    exit 1
fi

if [ "$DRY_RUN" = true ]; then
    echo "DRY RUN: Would push image: $IMAGE_NAME"
    exit 0
fi

echo "Pushing image to Google Container Registry..."
echo "Image: $IMAGE_NAME"

# Push to GCR
docker push "$IMAGE_NAME" || {
    echo "Error: Docker push failed"
    echo "Make sure you're authenticated with GCR:"
    echo "  gcloud auth configure-docker"
    exit 1
}

echo "Successfully pushed image: $IMAGE_NAME"
echo "Image URL: https://gcr.io/$PROJECT_ID/cookbook:$TAG"
