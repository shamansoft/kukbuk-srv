#!/bin/bash

# Check if tag is provided
if [ -z "$1" ]; then
  TAG="local"
else
  TAG=$1
fi

echo "Running an image gcr.io/cookbook-451120/cookbook:$TAG"

# Run the container with env file
docker run --platform linux/amd64 -p 8080:8080 -p 5005:5005 \
  --env-file ../.env \
  -e SPRING_PROFILES_ACTIVE=gcp \
  -e PORT=8080 \
  gcr.io/cookbook-451120/cookbook:$TAG