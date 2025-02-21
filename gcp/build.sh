#!/bin/bash

# Check args
if [ -z "$1" ]; then
    echo "Error: Tag is required"
    echo "Usage: ./build.sh <tag> [--native]"
    echo "Example: ./build.sh v1.0"
    exit 1
fi

TAG=$1
DOCKERFILE="Dockerfile.jvm"

# Check for --native flag
if [[ "$*" == *"--native"* ]]; then
    DOCKERFILE="Dockerfile.native"
else
    echo "Building JVM image..."
    ./gradlew build
fi

# Build command
if [ "$TAG" = "local" ]; then
    docker buildx build \
        --platform linux/amd64 \
        -f $DOCKERFILE \
        -t gcr.io/cookbook-451120/cookbook:$TAG \
        --load .
else
    docker buildx build \
        --platform linux/amd64 \
        -f $DOCKERFILE \
        -t gcr.io/cookbook-451120/cookbook:$TAG \
        --push .
fi

# Print confirmation and image details
echo "Build complete! Image details:"
docker images | grep cookbook