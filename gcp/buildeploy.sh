#!/bin/bash

echo "Building.."
TAG=$(./build.sh "$1" "$2" | tail -n 1)
echo "Deploying tag $TAG"
./deploy.sh "$TAG"