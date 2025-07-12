#!/bin/bash

# Script to run the application locally with Firestore emulator
set -e

echo "🔥 Starting Cookbook Extractor in Local Development Mode"
echo "========================================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

echo "✅ Docker is running"

# Set local development profile
export SPRING_PROFILES_ACTIVE=local

# Optional: Set log level for more detailed output
export LOGGING_LEVEL_NET_SHAMANSOFT_COOKBOOK=DEBUG

echo "🚀 Starting application with local profile..."
echo "   - Profile: $SPRING_PROFILES_ACTIVE"
echo "   - Firestore emulator will auto-start"
echo "   - Application will be available at: http://localhost:8080"
echo ""

# Run the application
cd "$(dirname "$0")/.."
./gradlew bootRun --args='--spring.profiles.active=local'