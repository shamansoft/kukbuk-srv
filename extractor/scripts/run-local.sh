#!/bin/bash

# Local development runner with OAuth secrets
# Usage: ./scripts/run-local.sh

set -e

# Check if secrets file exists
SECRETS_FILE="$(dirname "$0")/local-secrets.env"

if [ -f "$SECRETS_FILE" ]; then
    echo "Loading secrets from $SECRETS_FILE"
    # Export all variables from the file
    set -a
    source "$SECRETS_FILE"
    set +a
else
    echo "Warning: $SECRETS_FILE not found"
    echo "Create it with:"
    echo "  SAR_SRV_GOOGLE_OAUTH_CLIENT=your-client-id"
    echo "  SAR_SRV_GOOGLE_OAUTH_SECRET=your-client-secret"
    echo ""
    echo "Continuing with fallback values from application.yaml..."
fi

# Run the application
cd "$(dirname "$0")/.."
../gradlew :cookbook:bootRun
