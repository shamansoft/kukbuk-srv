#!/bin/bash

GRADLE_FILE="../build.gradle"

# Function to extract version from build.gradle
extract_version() {
    # Extract version from a line like: version = '0.2.2'
    VERSION=$(grep "version = '" $GRADLE_FILE | grep -o "[0-9]\+\.[0-9]\+\.[0-9]\+" | head -1)

    # Validate the extracted version
    if [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo $VERSION
        return 0
    else
        echo "Error: Could not extract valid version from build.gradle." >&2
        return 1
    fi
}

# Function to increment patch version
increment_patch() {
    local version=$1

    # Validate input is in proper version format
    if ! [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "Error: Version '$version' is not in valid format (major.minor.patch)" >&2
        return 1
    fi

    # Split version into major.minor.patch
    local major=$(echo $version | cut -d. -f1)
    local minor=$(echo $version | cut -d. -f2)
    local patch=$(echo $version | cut -d. -f3)

    # Ensure components are valid numbers
    if ! [[ $major =~ ^[0-9]+$ ]] || ! [[ $minor =~ ^[0-9]+$ ]] || ! [[ $patch =~ ^[0-9]+$ ]]; then
        echo "Error: Version components must be numeric" >&2
        return 1
    fi

    # Increment patch
    local new_patch=$((patch + 1))
    local new_version="$major.$minor.$new_patch"

    echo $new_version
}

# Function to update version in build.gradle
update_version_in_gradle() {
    local old_version=$1
    local new_version=$2

    echo "Updating build.gradle: changing version from $old_version to $new_version"

    # Create a temporary file
    TEMP_FILE=$(mktemp)

    # Process the file line by line
    while IFS= read -r line; do
        if [[ "$line" == "version = '$old_version'" ]]; then
            echo "version = '$new_version'" >> "$TEMP_FILE"
            echo "Found and replaced version line" >&2
        else
            echo "$line" >> "$TEMP_FILE"
        fi
    done < $GRADLE_FILE

    # Check if any replacements were made
    if grep -q "version = '$new_version'" "$TEMP_FILE"; then
        # Move the temp file to the original
        mv "$TEMP_FILE" $GRADLE_FILE
        echo "Successfully updated build.gradle"
        return 0
    else
        echo "Error: Failed to update version in build.gradle" >&2
        rm "$TEMP_FILE"
        return 1
    fi
}

# Main function to update the version
update_version() {
    echo "Checking build.gradle for version..."

    # Extract current version from build.gradle
    CURRENT_VERSION=$(extract_version)

    if [ -z "$CURRENT_VERSION" ]; then
        echo "Error: Could not extract version from build.gradle"
        return 1
    fi

    echo "Current version in build.gradle: $CURRENT_VERSION"

    # Increment patch version
    NEW_VERSION=$(increment_patch $CURRENT_VERSION)

    if [ -z "$NEW_VERSION" ]; then
        echo "Error: Failed to increment version"
        return 1
    fi

    echo "New version: $NEW_VERSION"

    # Update build.gradle with new version
    update_version_in_gradle "$CURRENT_VERSION" "$NEW_VERSION"

    # Verify the update
    echo "Verifying update..."
    UPDATED_VERSION=$(extract_version)

    if [ "$UPDATED_VERSION" != "$NEW_VERSION" ]; then
        echo "Warning: Version update verification failed"
        echo "Expected: $NEW_VERSION, Found: $UPDATED_VERSION"
        return 1
    fi

    # Return the new version
    echo $NEW_VERSION
    return 0
}

# If script is executed directly, run the update
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    # Script is being executed directly
    update_version
fi