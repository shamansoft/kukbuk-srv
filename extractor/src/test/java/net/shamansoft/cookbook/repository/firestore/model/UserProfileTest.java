package net.shamansoft.cookbook.repository.firestore.model;

import net.shamansoft.cookbook.entitlement.UserTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileTest {

    // ---- fromMap ------------------------------------------------------------

    @Test
    @DisplayName("fromMap: returns empty for null input")
    void fromMap_nullInput_returnsEmpty() {
        Optional<UserProfile> result = UserProfile.fromMap(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fromMap: returns profile with nulls for empty map")
    void fromMap_emptyMap_returnsProfileWithNulls() {
        Optional<UserProfile> result = UserProfile.fromMap(new HashMap<>());
        assertThat(result).isPresent();
        assertThat(result.get().uid()).isNull();
        assertThat(result.get().email()).isNull();
        assertThat(result.get().storage()).isNull();
    }

    @Test
    @DisplayName("fromMap: maps core string fields correctly")
    void fromMap_coreFields_mapped() {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", "user-123");
        data.put("userId", "user-123");
        data.put("email", "test@example.com");
        data.put("displayName", "Test User");

        Optional<UserProfile> result = UserProfile.fromMap(data);

        assertThat(result).isPresent();
        UserProfile profile = result.get();
        assertThat(profile.uid()).isEqualTo("user-123");
        assertThat(profile.userId()).isEqualTo("user-123");
        assertThat(profile.email()).isEqualTo("test@example.com");
        assertThat(profile.displayName()).isEqualTo("Test User");
        assertThat(profile.storage()).isNull();
    }

    @Test
    @DisplayName("fromMap: maps nested storage entity when present as Map")
    void fromMap_withStorageMap_mapsStorageEntity() {
        Map<String, Object> storageMap = new HashMap<>();
        storageMap.put("type", "googleDrive");
        storageMap.put("connected", Boolean.TRUE);
        storageMap.put("accessToken", "enc-access-token");
        storageMap.put("refreshToken", "enc-refresh-token");
        storageMap.put("folderId", "folder-abc");
        storageMap.put("folderName", "My Recipes");

        Map<String, Object> data = new HashMap<>();
        data.put("uid", "user-456");
        data.put("storage", storageMap);

        Optional<UserProfile> result = UserProfile.fromMap(data);

        assertThat(result).isPresent();
        StorageEntity storage = result.get().storage();
        assertThat(storage).isNotNull();
        assertThat(storage.type()).isEqualTo("googleDrive");
        assertThat(storage.connected()).isTrue();
        assertThat(storage.accessToken()).isEqualTo("enc-access-token");
        assertThat(storage.refreshToken()).isEqualTo("enc-refresh-token");
        assertThat(storage.folderId()).isEqualTo("folder-abc");
        assertThat(storage.folderName()).isEqualTo("My Recipes");
    }

    @Test
    @DisplayName("fromMap: storage is null when storage value is not a Map")
    void fromMap_withNonMapStorage_storageIsNull() {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", "user-789");
        data.put("storage", "not-a-map-string");

        Optional<UserProfile> result = UserProfile.fromMap(data);

        assertThat(result).isPresent();
        assertThat(result.get().uid()).isEqualTo("user-789");
        assertThat(result.get().storage()).isNull();
    }

    @Test
    @DisplayName("fromMap: storage.connected is false when missing from map")
    void fromMap_storageConnectedMissing_defaultsFalse() {
        Map<String, Object> storageMap = new HashMap<>();
        storageMap.put("type", "googleDrive");
        // connected not set → Boolean.TRUE.equals(null) == false

        Map<String, Object> data = new HashMap<>();
        data.put("uid", "user-abc");
        data.put("storage", storageMap);

        Optional<UserProfile> result = UserProfile.fromMap(data);

        assertThat(result).isPresent();
        assertThat(result.get().storage().connected()).isFalse();
    }

    @Test
    @DisplayName("fromMap: tier present → parsed correctly")
    void fromMap_tierPresent_parsed() {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", "user-123");
        data.put("tier", "PRO");
        data.put("credits", 10L);

        Optional<UserProfile> result = UserProfile.fromMap(data);

        assertThat(result).isPresent();
        assertThat(result.get().tier()).isEqualTo(UserTier.PRO);
        assertThat(result.get().credits()).isEqualTo(10);
    }

    @Test
    @DisplayName("fromMap: tier absent → defaults to FREE")
    void fromMap_tierAbsent_defaultsFree() {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", "user-123");

        Optional<UserProfile> result = UserProfile.fromMap(data);

        assertThat(result).isPresent();
        assertThat(result.get().tier()).isEqualTo(UserTier.FREE);
        assertThat(result.get().credits()).isEqualTo(0);
    }

    @Test
    @DisplayName("fromMap: invalid tier string → defaults to FREE")
    void fromMap_invalidTierString_defaultsFree() {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", "user-123");
        data.put("tier", "INVALID_TIER");

        Optional<UserProfile> result = UserProfile.fromMap(data);

        assertThat(result).isPresent();
        assertThat(result.get().tier()).isEqualTo(UserTier.FREE);
    }

    // ---- toMap --------------------------------------------------------------

    @Test
    @DisplayName("toMap: only non-null fields are included")
    void toMap_partialFields_onlyNonNullIncluded() {
        UserProfile profile = UserProfile.builder()
                .uid("user-123")
                .email("test@example.com")
                .build();

        Map<String, Object> result = profile.toMap();

        assertThat(result).containsKey("uid");
        assertThat(result).containsKey("email");
        assertThat(result).doesNotContainKey("displayName");
        assertThat(result).doesNotContainKey("googleOAuthToken");
        assertThat(result).doesNotContainKey("storage");
        assertThat(result.get("uid")).isEqualTo("user-123");
        assertThat(result.get("email")).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("toMap: includes all non-null core fields")
    void toMap_allCoreFields_allIncluded() {
        UserProfile profile = UserProfile.builder()
                .uid("u")
                .userId("u")
                .email("e@e.com")
                .displayName("User Name")
                .googleOAuthToken("oauth-token")
                .googleRefreshToken("refresh-token")
                .build();

        Map<String, Object> result = profile.toMap();

        assertThat(result).containsKeys("uid", "userId", "email", "displayName",
                "googleOAuthToken", "googleRefreshToken");
    }

    @Test
    @DisplayName("toMap: includes nested storage map when storage is present")
    void toMap_withStorage_includesStorageAsMap() {
        StorageEntity storage = StorageEntity.builder()
                .type("googleDrive")
                .connected(true)
                .accessToken("enc-token")
                .folderId("folder-xyz")
                .build();

        UserProfile profile = UserProfile.builder()
                .uid("user-456")
                .storage(storage)
                .build();

        Map<String, Object> result = profile.toMap();

        assertThat(result).containsKey("storage");
        assertThat(result.get("storage")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> storageResult = (Map<String, Object>) result.get("storage");
        assertThat(storageResult.get("type")).isEqualTo("googleDrive");
    }

    @Test
    @DisplayName("toMap: returns empty map when all fields are null (except credits=0)")
    void toMap_allNullFields_returnsEmptyMap() {
        UserProfile profile = UserProfile.builder().build();

        Map<String, Object> result = profile.toMap();

        assertThat(result).containsOnlyKeys("credits");
        assertThat(result.get("credits")).isEqualTo(0);
    }

    @Test
    @DisplayName("toMap: includes tier and credits")
    void toMap_includesTierAndCredits() {
        UserProfile profile = UserProfile.builder()
                .uid("user-123")
                .tier(UserTier.ENTERPRISE)
                .credits(5)
                .build();

        Map<String, Object> result = profile.toMap();

        assertThat(result.get("tier")).isEqualTo("ENTERPRISE");
        assertThat(result.get("credits")).isEqualTo(5);
    }

    @Test
    @DisplayName("toMap: tier null is not serialized")
    void toMap_tierNull_notSerialized() {
        UserProfile profile = UserProfile.builder()
                .uid("user-123")
                .tier(null)
                .credits(0)
                .build();

        Map<String, Object> result = profile.toMap();

        assertThat(result).doesNotContainKey("tier");
        assertThat(result.get("credits")).isEqualTo(0);
    }

    // ---- toDto --------------------------------------------------------------

    @Test
    @DisplayName("toDto: maps userId and email, createdAt null when not set")
    void toDto_basicFields_mapped() {
        UserProfile profile = UserProfile.builder()
                .uid("user-123")
                .userId("user-123")
                .email("test@example.com")
                .build();

        var dto = profile.toDto();

        assertThat(dto.userId()).isEqualTo("user-123");
        assertThat(dto.email()).isEqualTo("test@example.com");
        assertThat(dto.createdAt()).isNull();
        assertThat(dto.storage()).isNull();
    }

    @Test
    @DisplayName("toDto: storage is null in dto when profile has no storage")
    void toDto_noStorage_storageDtoIsNull() {
        UserProfile profile = UserProfile.builder()
                .userId("user-xyz")
                .build();

        var dto = profile.toDto();

        assertThat(dto.storage()).isNull();
    }

    // ---- StoredRecipe.withUpdatedVersion ------------------------------------

    @Test
    @DisplayName("StoredRecipe.withUpdatedVersion: increments version and preserves other fields")
    void storedRecipe_withUpdatedVersion_incrementsVersion() throws InterruptedException {
        Instant originalTime = Instant.now();
        StoredRecipe original = StoredRecipe.builder()
                .contentHash("hash-abc")
                .sourceUrl("https://example.com/recipe")
                .recipesJson("[{\"title\":\"Pasta\"}]")
                .isValid(true)
                .createdAt(originalTime)
                .lastUpdatedAt(originalTime)
                .version(5L)
                .build();

        Thread.sleep(5); // ensure lastUpdatedAt can differ
        StoredRecipe updated = original.withUpdatedVersion();

        assertThat(updated.getVersion()).isEqualTo(6L);
        assertThat(updated.getContentHash()).isEqualTo("hash-abc");
        assertThat(updated.getSourceUrl()).isEqualTo("https://example.com/recipe");
        assertThat(updated.getRecipesJson()).isEqualTo("[{\"title\":\"Pasta\"}]");
        assertThat(updated.isValid()).isTrue();
        assertThat(updated.getCreatedAt()).isEqualTo(originalTime);
        assertThat(updated.getLastUpdatedAt()).isAfterOrEqualTo(originalTime);
    }

    @Test
    @DisplayName("StoredRecipe.withUpdatedVersion: works from version 0")
    void storedRecipe_withUpdatedVersion_fromZero() {
        StoredRecipe original = StoredRecipe.builder()
                .contentHash("hash-xyz")
                .sourceUrl("https://example.com")
                .isValid(false)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .version(0L)
                .build();

        StoredRecipe updated = original.withUpdatedVersion();

        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.isValid()).isFalse();
    }
}