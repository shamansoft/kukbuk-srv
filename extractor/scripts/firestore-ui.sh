#!/bin/bash

# Script to start Firestore UI for debugging local emulator
set -e

# Check for help flag
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "🔥 Firestore UI Helper Script"
    echo "============================"
    echo ""
    echo "Usage: $0 [--cleanup]"
    echo ""
    echo "Options:"
    echo "  --cleanup    Remove existing emulator container and start fresh"
    echo "  --help, -h   Show this help message"
    echo ""
    echo "This script will:"
    echo "1. Start or reuse existing Firestore emulator container"
    echo "2. Set up Firebase UI configuration"
    echo "3. Launch Firebase UI at http://localhost:4000"
    echo ""
    exit 0
fi

# Check for cleanup flag
if [[ "$1" == "--cleanup" ]]; then
    echo "🧹 Cleaning up existing containers first..."
    ./scripts/cleanup-emulator.sh
    echo ""
fi

echo "🔥 Starting Firestore UI for Local Development"
echo "=============================================="
echo ""

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo "❌ Firebase CLI not found. Installing..."
    npm install -g firebase-tools
fi

echo "✅ Firebase CLI is available"

# Check if Docker is running and start emulator if needed
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

echo "✅ Docker is running"

# Check if emulator container exists and handle accordingly
CONTAINER_NAME="firestore-emulator-standalone"

if docker ps -a --format "table {{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
    echo "📦 Found existing emulator container..."
    
    if docker ps --format "table {{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        echo "✅ Emulator container is already running"
    else
        echo "🔄 Starting existing emulator container..."
        docker start $CONTAINER_NAME
        echo "⏳ Waiting for emulator to start..."
        sleep 3
    fi
else
    echo "🚀 Creating new Firestore emulator container..."
    docker run -d --name $CONTAINER_NAME \
        -p 8081:8080 \
        gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators \
        gcloud emulators firestore start --host-port=0.0.0.0:8080 --project=local-dev-project
    
    echo "⏳ Waiting for emulator to start..."
    sleep 5
fi

# Verify emulator is accessible
echo "🔍 Checking emulator connectivity..."
if curl -f http://localhost:8081 &>/dev/null; then
    echo "✅ Emulator is accessible at localhost:8081"
else
    echo "⚠️  Emulator might still be starting up..."
fi

# Set emulator host for Firebase CLI
export FIRESTORE_EMULATOR_HOST=localhost:8081

echo "🎯 Starting Firebase UI..."
echo "   - Emulator: $FIRESTORE_EMULATOR_HOST"
echo "   - UI will be available at: http://localhost:4000"
echo ""

# Change to script directory to find firebase.json
cd "$(dirname "$0")/.."

# Check if firebase.json exists, create if not
if [ ! -f "firebase.json" ]; then
    echo "📝 Creating firebase.json configuration..."
    cat > firebase.json << EOF
{
  "emulators": {
    "firestore": {
      "host": "localhost",
      "port": 8081
    },
    "ui": {
      "enabled": true,
      "host": "localhost", 
      "port": 4000
    }
  }
}
EOF
fi

echo "🔥 Starting Firebase UI with configuration..."

# Try to start Firebase UI
firebase emulators:start