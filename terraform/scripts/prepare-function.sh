#!/bin/bash

# Script to prepare Cloud Function source code for deployment
# This script creates a zip file of the token-broker source code

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TOKEN_BROKER_DIR="$PROJECT_ROOT/token-broker"
TERRAFORM_DIR="$PROJECT_ROOT/terraform"

echo "Preparing Cloud Function source code..."

# Create temporary directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Copy token-broker files to temp directory
cp "$TOKEN_BROKER_DIR/index.js" "$TEMP_DIR/"
cp "$TOKEN_BROKER_DIR/package.json" "$TEMP_DIR/"

# Create zip file
cd "$TEMP_DIR"
zip -r "$TERRAFORM_DIR/token-broker-source.zip" .

echo "Cloud Function source prepared: terraform/token-broker-source.zip"