#!/bin/bash

# Initialize variables
TAG=""
NATIVE_FLAG=false
DOCKERFILE="Dockerfile.jvm"
DEBUG_FLAG=false
MEMORY_LIMIT="12g"  # Increased from original
BUILD_SUCCESS=false
PROJECT_ID="kukbuk-tf" # cookbook-451120

# Parse arguments
for arg in "$@"; do
    if [ "$arg" == "--native" ]; then
        NATIVE_FLAG=true
    elif [ "$arg" == "--debug" ]; then
        DEBUG_FLAG=true
    elif [ "$arg" == "--dry-run" ]; then
        DRY_RUN=true
    elif [ "$arg" == "--tag" ]; then
        TAG="${arg#*=}"
    elif [[ "$arg" == --memory=* ]]; then
        MEMORY_LIMIT="${arg#*=}"  # Extract value after =
    elif [[ "$arg" != --* ]]; then
        # If not a flag (doesn't start with --), treat as tag
        TAG="$arg"
    fi
done

if [ "$DRY_RUN" = true ]; then
  TAG="dry-run"
    echo "This is a dry run"
    echo "Build complete! Image details:"
    echo "$TAG"
    exit 1
fi

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
        echo "Usage: ./build.sh [<tag>] [--native] [--debug] [--memory=8g]"
        echo "Example: ./build.sh v1.0 --native --memory=8g"
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
    echo "Building Native image with tag $TAG and memory limit $MEMORY_LIMIT..."
else
    echo "Building JVM image with tag $TAG..."
    ./gradlew build || { echo "Gradle build failed"; exit 1; }
fi

# Build command with memory limit
BUILD_ARGS=""
if [ "$DEBUG_FLAG" = true ]; then
    BUILD_ARGS="$BUILD_ARGS --progress=plain"
fi

if [ "$TAG" = "local" ]; then
docker buildx build \
        --platform linux/amd64 \
        -f $DOCKERFILE \
        -t gcr.io/$PROJECT_ID/cookbook:$TAG \
        --memory=$MEMORY_LIMIT \
        --memory-swap=$MEMORY_LIMIT \
        $BUILD_ARGS \
        --load . || { echo "Docker build failed"; exit 1; }
else
    docker buildx build \
        --platform linux/amd64 \
        -f $DOCKERFILE \
        -t gcr.io/$PROJECT_ID/cookbook:$TAG \
        --memory=$MEMORY_LIMIT \
        --memory-swap=$MEMORY_LIMIT \
        $BUILD_ARGS \
        --push . || { echo "Docker build failed"; exit 1; }
fi

cd ./scripts
# Print confirmation and image details
echo "Build complete! Image details:"
#docker images | grep cookbook
echo "$TAG"
