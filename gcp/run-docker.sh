#!/bin/bash

# Check if tag is provided
if [ -z "$1" ]; then
    echo "Error: Tag is required"
    echo "Usage: ./run.sh <tag>"
    echo "Example: ./run.sh v1.0"
    exit 1
fi

TAG=$1

# Run the container with env file
docker run --platform linux/amd64 -p 8080:8080 \
  --env-file ../.env \
  -e SPRING_PROFILES_ACTIVE=gcp \
  -e PORT=8080 \
  gcr.io/cookbook-451120/cookbook:$TAG