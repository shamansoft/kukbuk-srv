#!/bin/bash

# Initialize variables
TAG=""
NATIVE_FLAG=false
DOCKERFILE="Dockerfile.jvm"

# Parse arguments
for arg in "$@"; do
    if [ "$arg" == "--native" ]; then
        NATIVE_FLAG=true
    elif [[ "$arg" != --* ]]; then
        # If not a flag (doesn't start with --), treat as tag
        TAG="$arg"
    fi
done

# Check for tag and auto-generate if missing
if [ -z "$TAG" ]; then
    echo "No tag provided. Generating from build.gradle version..."

    # Source the version updater script
    source ./version-updater.sh

    # Capture only the last line (the new version) from update_version's output
    NEW_VERSION=$(update_version | tail -n 1)

    # Check if version update was successful
    if [ $? -ne 0 ]; then
        echo "Error: Version update failed"
        echo "Usage: ./build.sh [<tag>] [--native]"
        echo "Example: ./build.sh v1.0 --native"
        exit 1
    fi

    # Use new version as tag
    TAG=$NEW_VERSION
    echo "Using auto-generated tag: $TAG"
fi

# Handle build type based on native flag
cd ../
if [ "$NATIVE_FLAG" = true ]; then
    DOCKERFILE="Dockerfile.native"
    echo "Building Native image with tag $TAG..."
else
    echo "Building JVM image with tag $TAG..."
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
cd gcp

# Print confirmation and image details
echo "Build complete! Image details:"
docker images | grep cookbook
echo $TAG