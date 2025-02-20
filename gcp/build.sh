#!/bin/bash

# Check if tag is provided
if [ -z "$1" ]; then
    echo "Error: Tag is required"
    echo "Usage: ./build.sh <tag>"
    echo "Example: ./build.sh v1.0"
    exit 1
fi

TAG=$1

# Build and push the Docker image for multiple platforms
echo "Building image with tag: $TAG"
docker buildx build \
  --platform linux/amd64 \
  --build-arg BUILDPLATFORM=linux/amd64 \
  -t gcr.io/cookbook-451120/cookbook:$TAG \
  --push .

# Print confirmation and image details
echo "Build complete! Image details:"
docker images | grep cookbook