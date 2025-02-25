#!/bin/bash

# Check args
if [ -z "$1" ]; then
    echo "Error: Tag is required"
    echo "Usage: ./build.sh <tag> [--native]"
    echo "Example: ./build.sh v1.0"
    exit 1
fi

echo "Building  tag $1"
./build.sh $1 $2
echo "Deploying  tag $1"
./deploy.sh $1