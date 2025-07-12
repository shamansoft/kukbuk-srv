#!/bin/bash

# Script to clean up Firestore emulator containers
set -e

echo "🧹 Cleaning up Firestore emulator containers"
echo "============================================"

CONTAINER_NAME="firestore-emulator-standalone"

# Stop and remove the container if it exists
if docker ps -a --format "table {{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
    echo "🛑 Stopping container: $CONTAINER_NAME"
    docker stop $CONTAINER_NAME 2>/dev/null || true
    
    echo "🗑️  Removing container: $CONTAINER_NAME"
    docker rm $CONTAINER_NAME 2>/dev/null || true
    
    echo "✅ Cleanup complete"
else
    echo "ℹ️  No emulator container found to clean up"
fi

echo ""
echo "You can now run ./scripts/firestore-ui.sh to start fresh"