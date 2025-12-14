package net.shamansoft.cookbook.dto;

import lombok.Getter;

/**
 * Supported storage provider types.
 * Maps to camelCase values in Firestore for readability.
 */
@Getter
public enum StorageType {
    GOOGLE_DRIVE("googleDrive"),
    DROPBOX("dropbox"),
    ONE_DRIVE("oneDrive");

    private final String firestoreValue;

    StorageType(String firestoreValue) {
        this.firestoreValue = firestoreValue;
    }

    /**
     * Get StorageType from Firestore string value
     *
     * @param value the Firestore string value
     * @return the corresponding StorageType
     * @throws IllegalArgumentException if the value is unknown
     */
    public static StorageType fromFirestoreValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Storage type cannot be null");
        }
        for (StorageType type : StorageType.values()) {
            if (type.firestoreValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown storage type: " + value);
    }
}

