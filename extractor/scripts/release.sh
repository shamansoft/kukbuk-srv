#!/bin/bash

echo "Building..."
BUILD_OUTPUT=$(./build.sh "$@" | tee /dev/tty)
if [ ${PIPESTATUS[0]} -ne 0 ]; then
  echo "Build failed. Exiting release script."
  exit 1
fi
TAG=$(echo "$BUILD_OUTPUT" | tail -n 1)
echo "Deploying tag $TAG..."
./deploy.sh "$TAG"