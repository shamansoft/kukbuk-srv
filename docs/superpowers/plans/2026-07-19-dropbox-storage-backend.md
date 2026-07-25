# Dropbox Storage Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Dropbox as a second recipe-storage backend to `sar-srv` behind a provider abstraction, so a user can connect Dropbox and have all existing recipe/media endpoints read and write it — with zero behavior change for Google Drive users.

**Architecture:** Extract a provider-agnostic `StorageProvider` interface + `StorageProviderResolver` from today's Google-coupled code. Add `DropboxAuthClient` (OAuth code exchange / refresh / revoke), `DropboxClient` (Dropbox HTTP file API), and `DropboxStorageProvider`. `StorageService` gains a `connectDropbox` path and becomes provider-aware for token refresh and disconnect; `RecipeService`/`RecipeMediaProxyService` route through the resolver by the user's stored `storage.type`. New HTTP surface: `POST /v1/storage/dropbox/connect` plus provider-neutral `GET /v1/storage/status` and `DELETE /v1/storage/disconnect`. The existing `/v1/storage/google-drive/*` endpoints keep working.

**Tech Stack:** Java 25, Spring Boot 4.0.1 (Spring Framework 7 `RestClient`), Gradle 9.2 (`./gradlew`), Jackson 3 (`tools.jackson.*`), Lombok, JUnit 5 + Mockito + AssertJ, Testcontainers/WireMock for `intTest`, Firestore, Cloud KMS token encryption, GraalVM native for production.

## Global Constraints

- **Module:** the app lives in `extractor/` but the Gradle project is `:cookbook`. Files use `extractor/...`; Gradle tasks use `:cookbook`.
- **Base package:** `net.shamansoft.cookbook`.
- **Build/test commands (use these verbatim):**
  - Single test class: `./gradlew :cookbook:test --tests <ClassName>`
  - Single method: `./gradlew :cookbook:test --tests <ClassName>.<method>`
  - All unit tests: `./gradlew :cookbook:test`
  - Integration tests (Docker required): `./gradlew :cookbook:intTest`
  - Full module build: `./gradlew :cookbook:build`
  - Coverage gate (40% min): `./gradlew :cookbook:checkCoverage`
- **Testing conventions:** unit tests in `extractor/src/test/java`; integration tests in `extractor/src/intTest/java`. Mock Spring beans in `@SpringBootTest` with `@MockitoBean` (not `@MockBean`). `RestClient` HTTP clients are unit-tested by mocking the `RestClient` fluent chain (see existing `GoogleAuthClientTest`, `GoogleDriveTest`).
- **Do NOT change recipe/media endpoint behavior:** `GET /v1/recipes`, `GET /v1/recipes/{id}`, `POST /v1/recipes`, `POST /v1/recipes/custom`, `GET /v1/media/{id}` must behave identically for Google-Drive users. They become provider-aware only via the resolver.
- **Single storage per user:** connecting Dropbox overwrites `users/{uid}.storage` (`type: "dropbox"`). No cross-provider migration in v1.
- **Tokens are encrypted at rest** via `TokenEncryptionService` (KMS) and auto-refreshed on expiry — this must remain true and become provider-aware.
- **Dropbox "App folder" access level** (set in the Dropbox App Console — NOT "Full Dropbox"). Consequence: every Dropbox path parameter is *relative to the app folder*; the app-folder root is the empty string `""` (equivalently `/`). Store `folderId = ""` for the app-folder root (default) or an optional `/<subfolder>` path; `folderName` is cosmetic. All path handling MUST treat `""` as the app-folder root and never fail on it.
- **Dropbox file id:** persist Dropbox `id:...` values as the recipe/media `fileId` (stable across rename/move) so `GET /v1/recipes/{id}` and `GET /v1/media/{id}` keep working.
- **Config keys:** `cookbook.dropbox.app-key`, `cookbook.dropbox.app-secret`, `cookbook.dropbox.folder-name`. The **app-secret** must be provisioned in Secret Manager in `sar-infra` (handoff note only — do not implement infra here).
- **Reuse existing DTO shapes** (`StorageConnectionRequest`, `StorageConnectionResponse`, `StorageStatusResponse`, `StorageInfo`) — no new request/response DTOs.

---

## File Structure

**New production files**
- `extractor/src/main/java/net/shamansoft/cookbook/client/DropboxAuthClient.java` — Dropbox OAuth: exchange / refresh / expiry / revoke.
- `extractor/src/main/java/net/shamansoft/cookbook/client/DropboxClient.java` — Dropbox file HTTP API (create_folder_v2, upload, download, list_folder[/continue], get_metadata).
- `extractor/src/main/java/net/shamansoft/cookbook/service/StorageProvider.java` — provider-agnostic operation surface + `FolderRef` record.
- `extractor/src/main/java/net/shamansoft/cookbook/service/StorageProviderResolver.java` — selects a `StorageProvider` by `StorageType`.
- `extractor/src/main/java/net/shamansoft/cookbook/service/GoogleDriveStorageProvider.java` — `StorageProvider` over the existing `GoogleDriveService`.
- `extractor/src/main/java/net/shamansoft/cookbook/service/DropboxStorageProvider.java` — `StorageProvider` over `DropboxClient`.

**Modified production files**
- `extractor/src/main/resources/application.yaml` — add `cookbook.dropbox.*` config block.
- `extractor/src/main/java/net/shamansoft/cookbook/repository/firestore/model/StorageEntity.java` — `toMap()` persists the actual `type` (stop hardcoding `GOOGLE_DRIVE`).
- `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java` — resolve provider by `storage.type()`.
- `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeMediaProxyService.java` — resolve provider by `storage.type()`.
- `extractor/src/main/java/net/shamansoft/cookbook/service/StorageService.java` — `connectDropbox`, provider-aware refresh, Dropbox token revoke on disconnect.
- `extractor/src/main/java/net/shamansoft/cookbook/controller/StorageController.java` — `POST /dropbox/connect`, neutral `GET /status`, neutral `DELETE /disconnect`.

**New test files**
- `.../repository/firestore/model/StorageEntityTest.java`
- `.../client/DropboxAuthClientTest.java`
- `.../client/DropboxClientTest.java`
- `.../service/StorageProviderResolverTest.java`
- `.../service/GoogleDriveStorageProviderTest.java`
- `.../service/DropboxStorageProviderTest.java`

**Modified test files**
- `.../service/RecipeServiceListGetTest.java`, `RecipeServiceCreateRecipeTest.java`, `RecipeServiceFromDescriptionTest.java`, `RecipeServiceErrorHandlingTest.java`, `RecipeServiceTest.java` — swap the `DriveService` collaborator for a `StorageProvider` + `StorageProviderResolver`.
- `.../service/StorageServiceTest.java` and `.../intTest/.../service/StorageServiceIntegrationTest.java` — extend the `StorageService` constructor; add Dropbox connect/refresh/revoke coverage.
- `.../controller/StorageControllerTest.java` — Dropbox connect + neutral status/disconnect.

---

## Task 1: `StorageEntity.toMap()` persists the actual storage type

**Files:**
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/repository/firestore/model/StorageEntity.java:28-45`
- Test: `extractor/src/test/java/net/shamansoft/cookbook/repository/firestore/model/StorageEntityTest.java` (create)

**Interfaces:**
- Consumes: nothing new.
- Produces: `StorageEntity.toMap()` now serializes whatever `type` the entity was built with (`"googleDrive"` or `"dropbox"`), enabling per-provider persistence used by Tasks 8-9.

- [ ] **Step 1: Write the failing test**

Create `extractor/src/test/java/net/shamansoft/cookbook/repository/firestore/model/StorageEntityTest.java`:

```java
package net.shamansoft.cookbook.repository.firestore.model;

import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageEntityTest {

    @Test
    void toMap_persistsDropboxType() {
        StorageEntity entity = StorageEntity.builder()
                .type(StorageType.DROPBOX.getFirestoreValue())
                .connected(true)
                .accessToken("enc-access")
                .folderId("")
                .folderName("MyKukBuk")
                .build();

        Map<String, Object> map = entity.toMap();

        assertThat(map.get("type")).isEqualTo("dropbox");
        assertThat(map.get("connected")).isEqualTo(true);
        assertThat(map.get("folderId")).isEqualTo("");
    }

    @Test
    void toMap_persistsGoogleDriveType() {
        StorageEntity entity = StorageEntity.builder()
                .type(StorageType.GOOGLE_DRIVE.getFirestoreValue())
                .connected(true)
                .accessToken("enc-access")
                .build();

        assertThat(entity.toMap().get("type")).isEqualTo("googleDrive");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :cookbook:test --tests StorageEntityTest`
Expected: FAIL — `toMap_persistsDropboxType` fails with `expected: "dropbox" but was: "googleDrive"` (the method hardcodes `GOOGLE_DRIVE`).

- [ ] **Step 3: Write minimal implementation**

In `StorageEntity.java`, change the first line of `toMap()`:

```java
    public Map<String, Object> toMap() {
        Map<String, Object> storage = new HashMap<>();
        storage.put("type", type != null ? type : StorageType.GOOGLE_DRIVE.getFirestoreValue());
        storage.put("connected", connected);
        storage.put("accessToken", accessToken);
        if (refreshToken != null) {
            storage.put("refreshToken", refreshToken);
        }
        storage.put("expiresAt", expiresAt);
        storage.put("connectedAt", connectedAt);
        if (folderId != null) {
            storage.put("folderId", folderId);
        }
        if (folderName != null) {
            storage.put("folderName", folderName);
        }
        return storage;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :cookbook:test --tests StorageEntityTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Confirm existing storage tests still pass**

Run: `./gradlew :cookbook:test --tests StorageServiceTest`
Expected: PASS. (`StorageService.connectGoogleDriveWithTokens` already sets `.type(GOOGLE_DRIVE...)`, so the persisted map is unchanged for Google.)

- [ ] **Step 6: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/repository/firestore/model/StorageEntity.java \
        extractor/src/test/java/net/shamansoft/cookbook/repository/firestore/model/StorageEntityTest.java
git commit -m "feat(storage): persist actual storage type in StorageEntity.toMap()"
```

---

## Task 2: `DropboxAuthClient` + Dropbox config keys

**Files:**
- Create: `extractor/src/main/java/net/shamansoft/cookbook/client/DropboxAuthClient.java`
- Modify: `extractor/src/main/resources/application.yaml` (add `cookbook.dropbox` block)
- Test: `extractor/src/test/java/net/shamansoft/cookbook/client/DropboxAuthClientTest.java` (create)

**Interfaces:**
- Consumes: `genericRestClient` bean (`RestClient`, no base URL — already defined in `RestClientConfig`).
- Produces:
  - `DropboxAuthClient.TokenResponse(String accessToken, String refreshToken, long expiresIn)`
  - `DropboxAuthClient.RefreshTokenResponse(String accessToken, com.google.cloud.Timestamp expiresAt)`
  - `TokenResponse exchangeAuthorizationCode(String authorizationCode, String redirectUri)`
  - `RefreshTokenResponse refreshAccessToken(String refreshToken)`
  - `boolean isTokenExpired(com.google.cloud.Timestamp expiresAt)`
  - `void revokeToken(String accessToken)` (best-effort, never throws)

- [ ] **Step 1: Write the failing test**

Create `extractor/src/test/java/net/shamansoft/cookbook/client/DropboxAuthClientTest.java`:

```java
package net.shamansoft.cookbook.client;

import com.google.cloud.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class DropboxAuthClientTest {

    private DropboxAuthClient newClient(RestClient restClient) {
        DropboxAuthClient client = new DropboxAuthClient(restClient);
        ReflectionTestUtils.setField(client, "appKey", "app-key");
        ReflectionTestUtils.setField(client, "appSecret", "app-secret");
        return client;
    }

    private RestClient.ResponseSpec stubPost(RestClient restClient) {
        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        when(reqSpec.body(any(Object.class))).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(responseSpec);
        return responseSpec;
    }

    @Test
    void exchangeAuthorizationCode_returnsTokens() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "dbx-access");
        response.put("refresh_token", "dbx-refresh");
        response.put("expires_in", 14400);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        DropboxAuthClient.TokenResponse result =
                newClient(restClient).exchangeAuthorizationCode("code", "sar://dropbox-callback");

        assertThat(result.accessToken()).isEqualTo("dbx-access");
        assertThat(result.refreshToken()).isEqualTo("dbx-refresh");
        assertThat(result.expiresIn()).isEqualTo(14400);
    }

    @Test
    void exchangeAuthorizationCode_missingRefreshToken_throwsIllegalState() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "dbx-access");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        assertThatThrownBy(() -> newClient(restClient).exchangeAuthorizationCode("code", "sar://cb"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refreshAccessToken_returnsNewAccessTokenAndExpiry() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "dbx-new");
        response.put("expires_in", 14400);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);

        DropboxAuthClient.RefreshTokenResponse result =
                newClient(restClient).refreshAccessToken("dbx-refresh");

        assertThat(result.accessToken()).isEqualTo("dbx-new");
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void isTokenExpired_nullOrPast_true_future_false() {
        DropboxAuthClient client = newClient(mock(RestClient.class));
        long now = System.currentTimeMillis() / 1000;
        assertThat(client.isTokenExpired(null)).isTrue();
        assertThat(client.isTokenExpired(Timestamp.ofTimeSecondsAndNanos(now - 60, 0))).isTrue();
        assertThat(client.isTokenExpired(Timestamp.ofTimeSecondsAndNanos(now + 3600, 0))).isFalse();
    }

    @Test
    void revokeToken_swallowsErrors() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenThrow(new RuntimeException("boom"));
        assertThatCode(() -> newClient(restClient).revokeToken("dbx-access")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :cookbook:test --tests DropboxAuthClientTest`
Expected: FAIL — compilation error: `DropboxAuthClient` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `extractor/src/main/java/net/shamansoft/cookbook/client/DropboxAuthClient.java`:

```java
package net.shamansoft.cookbook.client;

import com.google.cloud.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Client for Dropbox OAuth2 (authorization-code + offline refresh token).
 * Mirrors {@link GoogleAuthClient}. Non-PKCE: the app secret is held server-side.
 */
@Service
@Slf4j
public class DropboxAuthClient {

    private static final long TOKEN_BUFFER_SECONDS = 300;
    private static final String TOKEN_URL = "https://api.dropboxapi.com/oauth2/token";
    private static final String REVOKE_URL = "https://api.dropboxapi.com/2/auth/token/revoke";
    private static final int DEFAULT_EXPIRES_IN = 14400;

    private final RestClient restClient;

    @Value("${cookbook.dropbox.app-key:REPLACE_WITH_DROPBOX_APP_KEY}")
    private String appKey;
    @Value("${cookbook.dropbox.app-secret:REPLACE_WITH_DROPBOX_APP_SECRET}")
    private String appSecret;

    public DropboxAuthClient(@Qualifier("genericRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public TokenResponse exchangeAuthorizationCode(String authorizationCode, String redirectUri) {
        log.info("Exchanging Dropbox authorization code for tokens, redirect_uri: {}", redirectUri);
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", authorizationCode);
            params.add("grant_type", "authorization_code");
            params.add("client_id", appKey);
            params.add("client_secret", appSecret);
            params.add("redirect_uri", redirectUri);

            Map<String, Object> response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null || !response.containsKey("access_token")) {
                throw new IllegalArgumentException("Invalid response from Dropbox OAuth: " + response);
            }
            String accessToken = (String) response.get("access_token");
            String refreshToken = (String) response.get("refresh_token");
            Integer expiresIn = (Integer) response.get("expires_in");
            if (refreshToken == null) {
                throw new IllegalStateException(
                        "No refresh token received. Ensure token_access_type=offline in the authorize URL.");
            }
            if (expiresIn == null) {
                expiresIn = DEFAULT_EXPIRES_IN;
            }
            log.info("Exchanged Dropbox authorization code, expires in {}s", expiresIn);
            return new TokenResponse(accessToken, refreshToken, expiresIn.longValue());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.error("Dropbox token exchange failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalArgumentException(
                    "Dropbox OAuth token exchange failed: " + e.getResponseBodyAsString()
                            + ". Check that app-key, app-secret, and redirect_uri match your Dropbox app configuration.");
        } catch (Exception e) {
            log.error("Failed to exchange Dropbox authorization code: {}", e.getMessage(), e);
            throw new DatabaseUnavailableException("Dropbox OAuth token exchange failed: " + e.getMessage(), e);
        }
    }

    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        log.info("Refreshing Dropbox access token");
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("refresh_token", refreshToken);
            params.add("client_id", appKey);
            params.add("client_secret", appSecret);

            Map<String, Object> response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("Invalid response from Dropbox OAuth: " + response);
            }
            String newAccessToken = (String) response.get("access_token");
            Integer expiresIn = (Integer) response.get("expires_in");
            if (expiresIn == null) {
                expiresIn = DEFAULT_EXPIRES_IN;
            }
            Timestamp newExpiresAt = Timestamp.ofTimeSecondsAndNanos(
                    System.currentTimeMillis() / 1000 + expiresIn, 0);
            log.info("Refreshed Dropbox token, expires in {}s", expiresIn);
            return new RefreshTokenResponse(newAccessToken, newExpiresAt);
        } catch (Exception e) {
            log.error("Failed to refresh Dropbox token: {}", e.getMessage(), e);
            throw new DatabaseUnavailableException("Failed to refresh Dropbox token: " + e.getMessage(), e);
        }
    }

    public boolean isTokenExpired(Timestamp expiresAt) {
        if (expiresAt == null) {
            return true;
        }
        long now = System.currentTimeMillis() / 1000;
        return (expiresAt.getSeconds() - now) <= TOKEN_BUFFER_SECONDS;
    }

    /**
     * Best-effort server-side token revocation. Never throws.
     */
    public void revokeToken(String accessToken) {
        try {
            restClient.post()
                    .uri(REVOKE_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Revoked Dropbox token");
        } catch (Exception e) {
            log.warn("Dropbox token revoke failed (ignored): {}", e.getMessage());
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
    }

    public record RefreshTokenResponse(String accessToken, Timestamp expiresAt) {
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :cookbook:test --tests DropboxAuthClientTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Add Dropbox config block to `application.yaml`**

In `extractor/src/main/resources/application.yaml`, immediately after the `cookbook.drive:` block (the line `oauth-secret: "${SAR_SRV_GOOGLE_OAUTH_SECRET:REPLACE_WITH_YOUR_OAUTH_SECRET}"`), insert:

```yaml
  # ---------------------------------------------------------------------------
  # DROPBOX CONFIGURATION (App-folder access level)
  # ---------------------------------------------------------------------------
  dropbox:
    # Dropbox app key (public value, safe in clients). Used by DropboxAuthClient.
    app-key: "${COOKBOOK_DROPBOX_APP_KEY:REPLACE_WITH_DROPBOX_APP_KEY}"
    # Dropbox app secret — provisioned via Secret Manager in sar-infra. NEVER commit a real value.
    app-secret: "${COOKBOOK_DROPBOX_APP_SECRET:REPLACE_WITH_DROPBOX_APP_SECRET}"
    # Default recipe subfolder inside the app folder. Empty = app-folder root ("").
    folder-name: "${COOKBOOK_DROPBOX_FOLDER_NAME:}"
```

- [ ] **Step 6: Verify the context still loads with the new config**

Run: `./gradlew :cookbook:test --tests StorageControllerTest`
Expected: PASS — the full `@SpringBootTest` context boots with `DropboxAuthClient` present (the `@Value` defaults cover test resources).

- [ ] **Step 7: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/client/DropboxAuthClient.java \
        extractor/src/test/java/net/shamansoft/cookbook/client/DropboxAuthClientTest.java \
        extractor/src/main/resources/application.yaml
git commit -m "feat(dropbox): add DropboxAuthClient (exchange/refresh/revoke) + config keys

Secret handoff: cookbook.dropbox.app-secret must be provisioned in sar-infra Secret Manager (KMS-backed), mapped to env COOKBOOK_DROPBOX_APP_SECRET on Cloud Run."
```

---

## Task 3: `DropboxClient` (Dropbox file HTTP API)

**Files:**
- Create: `extractor/src/main/java/net/shamansoft/cookbook/client/DropboxClient.java`
- Test: `extractor/src/test/java/net/shamansoft/cookbook/client/DropboxClientTest.java` (create)

**Interfaces:**
- Consumes: `genericRestClient` (`RestClient`), `tools.jackson.databind.ObjectMapper` (Spring bean), and the existing `net.shamansoft.cookbook.client.ClientException`.
- Produces:
  - `DropboxClient.FileEntry(String id, String name, String pathDisplay, String serverModified)`
  - `DropboxClient.ListResult(java.util.List<FileEntry> files, String cursor, boolean hasMore)`
  - `void createFolder(String path, String token)` (treats HTTP 409 as already-exists)
  - `FileEntry upload(String path, byte[] content, String token)`
  - `byte[] downloadAsBytes(String path, String token)` / `String downloadAsString(String path, String token)`
  - `FileEntry getMetadata(String path, String token)`
  - `ListResult listFolder(String path, int limit, String token)` / `ListResult listFolderContinue(String cursor, String token)`

- [ ] **Step 1: Write the failing test**

Create `extractor/src/test/java/net/shamansoft/cookbook/client/DropboxClientTest.java`:

```java
package net.shamansoft.cookbook.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class DropboxClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Stub restClient.post().uri(str).header(...).header(...).contentType(...).body(...).retrieve() */
    private RestClient.ResponseSpec stubPost(RestClient restClient) {
        RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(bodySpec);
        when(bodySpec.uri(anyString())).thenReturn(reqSpec);
        when(reqSpec.header(anyString(), anyString())).thenReturn(reqSpec);
        when(reqSpec.contentType(any(MediaType.class))).thenReturn(reqSpec);
        doReturn(reqSpec).when(reqSpec).body(any(Object.class));
        when(reqSpec.retrieve()).thenReturn(responseSpec);
        return responseSpec;
    }

    @Test
    void createFolder_conflict409_treatedAsExists() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientResponseException("conflict", 409, "Conflict", null, null, null));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatCode(() -> client.createFolder("/MyKukBuk", "token")).doesNotThrowAnyException();
    }

    @Test
    void createFolder_otherError_throwsClientException() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientResponseException("server error", 500, "Error", null, null, null));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThatThrownBy(() -> client.createFolder("/MyKukBuk", "token"))
                .isInstanceOf(ClientException.class);
    }

    @Test
    void upload_returnsFileEntryWithId() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("id", "id:abc123", "name", "pasta.yaml", "path_display", "/pasta.yaml"));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        DropboxClient.FileEntry entry = client.upload("/pasta.yaml", "content".getBytes(), "token");

        assertThat(entry.id()).isEqualTo("id:abc123");
        assertThat(entry.name()).isEqualTo("pasta.yaml");
        assertThat(entry.pathDisplay()).isEqualTo("/pasta.yaml");
    }

    @Test
    void downloadAsString_returnsUtf8() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(byte[].class))
                .thenReturn("title: Хачапури".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        assertThat(client.downloadAsString("id:abc", "token")).isEqualTo("title: Хачапури");
    }

    @Test
    void listFolder_filtersToFilesAndMapsCursor() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        Map<String, Object> file = Map.of(".tag", "file", "id", "id:f1", "name", "a.yaml",
                "path_display", "/a.yaml", "server_modified", "2024-01-15T10:00:00Z");
        Map<String, Object> folder = Map.of(".tag", "folder", "id", "id:d1", "name", "sub");
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("entries", List.of(file, folder), "cursor", "CURSOR1", "has_more", true));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        DropboxClient.ListResult result = client.listFolder("", 100, "token");

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).id()).isEqualTo("id:f1");
        assertThat(result.cursor()).isEqualTo("CURSOR1");
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void getMetadata_returnsFileEntry() {
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = stubPost(restClient);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("id", "id:abc", "name", "photo.jpg",
                        "path_display", "/photo.jpg", "server_modified", "2024-02-01T00:00:00Z"));

        DropboxClient client = new DropboxClient(restClient, objectMapper);
        DropboxClient.FileEntry entry = client.getMetadata("id:abc", "token");

        assertThat(entry.name()).isEqualTo("photo.jpg");
        assertThat(entry.serverModified()).isEqualTo("2024-02-01T00:00:00Z");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :cookbook:test --tests DropboxClientTest`
Expected: FAIL — compilation error: `DropboxClient` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `extractor/src/main/java/net/shamansoft/cookbook/client/DropboxClient.java`:

```java
package net.shamansoft.cookbook.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Dropbox file API (App-folder access).
 * All {@code path} arguments are relative to the app folder; {@code ""} is the app-folder root.
 */
@Slf4j
@Service
public class DropboxClient {

    private static final String API = "https://api.dropboxapi.com";
    private static final String CONTENT = "https://content.dropboxapi.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DropboxClient(@Qualifier("genericRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /** Create a folder; a 409 path/conflict is treated as "already exists". */
    public void createFolder(String path, String token) {
        try {
            rpc(API + "/2/files/create_folder_v2", Map.of("path", path, "autorename", false), token);
            log.info("Created Dropbox folder: {}", path);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                log.info("Dropbox folder already exists: {}", path);
                return;
            }
            throw new ClientException("Failed to create Dropbox folder: " + path, e);
        }
    }

    public FileEntry upload(String path, byte[] content, String token) {
        String arg = writeArg(Map.of("path", path, "mode", "overwrite", "mute", true));
        Map<String, Object> resp = restClient.post()
                .uri(CONTENT + "/2/files/upload")
                .header("Authorization", "Bearer " + token)
                .header("Dropbox-API-Arg", arg)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        if (resp == null || resp.get("id") == null) {
            throw new ClientException("Dropbox upload returned no id for path: " + path);
        }
        return toFileEntry(resp);
    }

    public byte[] downloadAsBytes(String path, String token) {
        String arg = writeArg(Map.of("path", path));
        byte[] bytes = restClient.post()
                .uri(CONTENT + "/2/files/download")
                .header("Authorization", "Bearer " + token)
                .header("Dropbox-API-Arg", arg)
                .retrieve()
                .body(byte[].class);
        if (bytes == null) {
            throw new ClientException("Dropbox download returned no content for path: " + path);
        }
        return bytes;
    }

    public String downloadAsString(String path, String token) {
        return new String(downloadAsBytes(path, token), StandardCharsets.UTF_8);
    }

    public FileEntry getMetadata(String path, String token) {
        Map<String, Object> resp = rpc(API + "/2/files/get_metadata", Map.of("path", path), token);
        if (resp == null || resp.get("id") == null) {
            throw new ClientException("Dropbox get_metadata returned no id for path: " + path);
        }
        return toFileEntry(resp);
    }

    public ListResult listFolder(String path, int limit, String token) {
        return parseList(rpc(API + "/2/files/list_folder", Map.of("path", path, "limit", limit), token));
    }

    public ListResult listFolderContinue(String cursor, String token) {
        return parseList(rpc(API + "/2/files/list_folder/continue", Map.of("cursor", cursor), token));
    }

    private Map<String, Object> rpc(String uri, Map<String, Object> body, String token) {
        return restClient.post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    @SuppressWarnings("unchecked")
    private ListResult parseList(Map<String, Object> resp) {
        if (resp == null) {
            throw new ClientException("Dropbox list_folder returned null");
        }
        List<Map<String, Object>> entries = (List<Map<String, Object>>) resp.get("entries");
        List<FileEntry> files = new ArrayList<>();
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                if ("file".equals(e.get(".tag"))) {
                    files.add(toFileEntry(e));
                }
            }
        }
        String cursor = (String) resp.get("cursor");
        boolean hasMore = Boolean.TRUE.equals(resp.get("has_more"));
        return new ListResult(files, cursor, hasMore);
    }

    private static FileEntry toFileEntry(Map<String, Object> e) {
        return new FileEntry(
                (String) e.get("id"),
                (String) e.get("name"),
                (String) e.get("path_display"),
                (String) e.get("server_modified"));
    }

    private String writeArg(Map<String, Object> arg) {
        try {
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            throw new ClientException("Failed to build Dropbox-API-Arg header", e);
        }
    }

    public record FileEntry(String id, String name, String pathDisplay, String serverModified) {
    }

    public record ListResult(List<FileEntry> files, String cursor, boolean hasMore) {
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :cookbook:test --tests DropboxClientTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/client/DropboxClient.java \
        extractor/src/test/java/net/shamansoft/cookbook/client/DropboxClientTest.java
git commit -m "feat(dropbox): add DropboxClient (create_folder_v2/upload/download/list_folder/get_metadata)"
```

---

## Task 4: `StorageProvider` interface + `StorageProviderResolver`

**Files:**
- Create: `extractor/src/main/java/net/shamansoft/cookbook/service/StorageProvider.java`
- Create: `extractor/src/main/java/net/shamansoft/cookbook/service/StorageProviderResolver.java`
- Test: `extractor/src/test/java/net/shamansoft/cookbook/service/StorageProviderResolverTest.java` (create)

**Interfaces:**
- Consumes: `net.shamansoft.cookbook.dto.StorageType`, `DriveService.UploadResult`, `GoogleDrive.DriveFileListResult`, `GoogleDrive.DriveFileMetadata` (reused verbatim to avoid rippling `RecipeMapper`).
- Produces:
  - `StorageProvider` with methods whose names match `DriveService` (`generateFileName`, `uploadRecipeYaml`, `listRecipeFiles`, `getFileContent`, `downloadFile`, `getFileMimeType`, `getFileMetadata`) plus `StorageType type()`, `FolderRef getOrCreateFolder(String accessToken, String folderName)`, and `record FolderRef(String id, String name)`.
  - `StorageProviderResolver.resolve(StorageType) : StorageProvider`.

> **Note (deliberate reuse):** `StorageProvider` returns the existing `DriveService.UploadResult` / `GoogleDrive.DriveFileListResult` / `GoogleDrive.DriveFileMetadata` record types. They are plain `(id, name, modifiedTime, mimeType)` holders; reusing them keeps `RecipeMapper` and the recipe/media read path untouched, satisfying the "minimize surface change" principle.

- [ ] **Step 1: Write the failing test**

Create `extractor/src/test/java/net/shamansoft/cookbook/service/StorageProviderResolverTest.java`:

```java
package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageProviderResolverTest {

    @Test
    void resolve_returnsProviderForType() {
        StorageProvider google = mock(StorageProvider.class);
        StorageProvider dropbox = mock(StorageProvider.class);
        when(google.type()).thenReturn(StorageType.GOOGLE_DRIVE);
        when(dropbox.type()).thenReturn(StorageType.DROPBOX);

        StorageProviderResolver resolver = new StorageProviderResolver(List.of(google, dropbox));

        assertThat(resolver.resolve(StorageType.GOOGLE_DRIVE)).isSameAs(google);
        assertThat(resolver.resolve(StorageType.DROPBOX)).isSameAs(dropbox);
    }

    @Test
    void resolve_unknownType_throws() {
        StorageProvider google = mock(StorageProvider.class);
        when(google.type()).thenReturn(StorageType.GOOGLE_DRIVE);
        StorageProviderResolver resolver = new StorageProviderResolver(List.of(google));

        assertThatThrownBy(() -> resolver.resolve(StorageType.ONE_DRIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ONE_DRIVE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :cookbook:test --tests StorageProviderResolverTest`
Expected: FAIL — compilation error: `StorageProvider` / `StorageProviderResolver` do not exist.

- [ ] **Step 3: Write the interface**

Create `extractor/src/main/java/net/shamansoft/cookbook/service/StorageProvider.java`:

```java
package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;

/**
 * Provider-agnostic storage operation surface used by RecipeService / RecipeMediaProxyService.
 * Method names mirror {@link DriveService} so existing consumers change only which bean they call.
 */
public interface StorageProvider {

    StorageType type();

    String generateFileName(String title);

    /**
     * Ensure the recipe folder exists.
     *
     * @param accessToken provider access token
     * @param folderName  desired folder name (may be blank → provider default / app-folder root)
     * @return the folder reference (id may be a Drive id, or a Dropbox path such as "" or "/Sub")
     */
    FolderRef getOrCreateFolder(String accessToken, String folderName);

    DriveService.UploadResult uploadRecipeYaml(String accessToken, String folderId, String fileName, String content);

    GoogleDrive.DriveFileListResult listRecipeFiles(String accessToken, String folderId, int pageSize, String pageToken);

    String getFileContent(String accessToken, String fileId);

    byte[] downloadFile(String accessToken, String fileId);

    String getFileMimeType(String accessToken, String fileId);

    GoogleDrive.DriveFileMetadata getFileMetadata(String accessToken, String fileId);

    record FolderRef(String id, String name) {
    }
}
```

- [ ] **Step 4: Write the resolver**

Create `extractor/src/main/java/net/shamansoft/cookbook/service/StorageProviderResolver.java`:

```java
package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.dto.StorageType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Selects the {@link StorageProvider} implementation for a given {@link StorageType}.
 */
@Service
public class StorageProviderResolver {

    private final Map<StorageType, StorageProvider> byType = new EnumMap<>(StorageType.class);

    public StorageProviderResolver(List<StorageProvider> providers) {
        for (StorageProvider provider : providers) {
            byType.put(provider.type(), provider);
        }
    }

    public StorageProvider resolve(StorageType type) {
        StorageProvider provider = byType.get(type);
        if (provider == null) {
            throw new IllegalStateException("No storage provider registered for type: " + type);
        }
        return provider;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :cookbook:test --tests StorageProviderResolverTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/service/StorageProvider.java \
        extractor/src/main/java/net/shamansoft/cookbook/service/StorageProviderResolver.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/StorageProviderResolverTest.java
git commit -m "feat(storage): add StorageProvider interface + StorageProviderResolver"
```

---

## Task 5: `GoogleDriveStorageProvider`

**Files:**
- Create: `extractor/src/main/java/net/shamansoft/cookbook/service/GoogleDriveStorageProvider.java`
- Test: `extractor/src/test/java/net/shamansoft/cookbook/service/GoogleDriveStorageProviderTest.java` (create)

**Interfaces:**
- Consumes: the existing `GoogleDriveService` bean (reused for all file ops), `@Value("${cookbook.drive.folder-name}")`.
- Produces: a `StorageProvider` bean whose `type()` is `GOOGLE_DRIVE`.

- [ ] **Step 1: Write the failing test**

Create `extractor/src/test/java/net/shamansoft/cookbook/service/GoogleDriveStorageProviderTest.java`:

```java
package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GoogleDriveStorageProviderTest {

    @Mock
    private GoogleDriveService googleDriveService;

    @InjectMocks
    private GoogleDriveStorageProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(provider, "folderName", "_save_a_recipe");
    }

    @Test
    void type_isGoogleDrive() {
        assertThat(provider.type()).isEqualTo(StorageType.GOOGLE_DRIVE);
    }

    @Test
    void getOrCreateFolder_delegatesAndUsesConfiguredName() {
        when(googleDriveService.getOrCreateFolder("token")).thenReturn("folder-123");

        StorageProvider.FolderRef ref = provider.getOrCreateFolder("token", "ignored");

        assertThat(ref.id()).isEqualTo("folder-123");
        assertThat(ref.name()).isEqualTo("_save_a_recipe");
    }

    @Test
    void listRecipeFiles_delegates() {
        GoogleDrive.DriveFileListResult expected =
                new GoogleDrive.DriveFileListResult(java.util.List.of(), "next");
        when(googleDriveService.listRecipeFiles("token", "folder", 20, null)).thenReturn(expected);

        assertThat(provider.listRecipeFiles("token", "folder", 20, null)).isSameAs(expected);
    }

    @Test
    void uploadRecipeYaml_delegates() {
        DriveService.UploadResult expected = new DriveService.UploadResult("id1", "url1");
        when(googleDriveService.uploadRecipeYaml("token", "folder", "a.yaml", "yaml")).thenReturn(expected);

        assertThat(provider.uploadRecipeYaml("token", "folder", "a.yaml", "yaml")).isSameAs(expected);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :cookbook:test --tests GoogleDriveStorageProviderTest`
Expected: FAIL — compilation error: `GoogleDriveStorageProvider` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `extractor/src/main/java/net/shamansoft/cookbook/service/GoogleDriveStorageProvider.java`:

```java
package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * {@link StorageProvider} backed by Google Drive. Delegates all file operations to the
 * existing {@link GoogleDriveService} bean (single source of truth for Drive logic).
 */
@Service
public class GoogleDriveStorageProvider implements StorageProvider {

    private final GoogleDriveService googleDriveService;

    @Value("${cookbook.drive.folder-name}")
    private String folderName;

    public GoogleDriveStorageProvider(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    @Override
    public StorageType type() {
        return StorageType.GOOGLE_DRIVE;
    }

    @Override
    public String generateFileName(String title) {
        return googleDriveService.generateFileName(title);
    }

    @Override
    public FolderRef getOrCreateFolder(String accessToken, String folderName) {
        String id = googleDriveService.getOrCreateFolder(accessToken);
        return new FolderRef(id, this.folderName);
    }

    @Override
    public DriveService.UploadResult uploadRecipeYaml(String accessToken, String folderId, String fileName, String content) {
        return googleDriveService.uploadRecipeYaml(accessToken, folderId, fileName, content);
    }

    @Override
    public GoogleDrive.DriveFileListResult listRecipeFiles(String accessToken, String folderId, int pageSize, String pageToken) {
        return googleDriveService.listRecipeFiles(accessToken, folderId, pageSize, pageToken);
    }

    @Override
    public String getFileContent(String accessToken, String fileId) {
        return googleDriveService.getFileContent(accessToken, fileId);
    }

    @Override
    public byte[] downloadFile(String accessToken, String fileId) {
        return googleDriveService.downloadFile(accessToken, fileId);
    }

    @Override
    public String getFileMimeType(String accessToken, String fileId) {
        return googleDriveService.getFileMimeType(accessToken, fileId);
    }

    @Override
    public GoogleDrive.DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        return googleDriveService.getFileMetadata(accessToken, fileId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :cookbook:test --tests GoogleDriveStorageProviderTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/service/GoogleDriveStorageProvider.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/GoogleDriveStorageProviderTest.java
git commit -m "feat(storage): add GoogleDriveStorageProvider over GoogleDriveService"
```

---

## Task 6: `DropboxStorageProvider`

**Files:**
- Create: `extractor/src/main/java/net/shamansoft/cookbook/service/DropboxStorageProvider.java`
- Test: `extractor/src/test/java/net/shamansoft/cookbook/service/DropboxStorageProviderTest.java` (create)

**Interfaces:**
- Consumes: `DropboxClient`, `Transliterator` (existing bean, `toAsciiKebab`), `@Value("${cookbook.dropbox.folder-name:}")`.
- Produces: a `StorageProvider` bean whose `type()` is `DROPBOX`. Path rules: app-folder root is `""`; upload path = `folderId + "/" + fileName` (root → `"/" + fileName`); files stored/read by Dropbox `id:...`; MIME inferred from filename.

- [ ] **Step 1: Write the failing test**

Create `extractor/src/test/java/net/shamansoft/cookbook/service/DropboxStorageProviderTest.java`:

```java
package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.client.DropboxClient;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DropboxStorageProviderTest {

    @Mock
    private DropboxClient dropboxClient;
    @Mock
    private Transliterator transliterator;

    @InjectMocks
    private DropboxStorageProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(provider, "folderName", "");
    }

    @Test
    void type_isDropbox() {
        assertThat(provider.type()).isEqualTo(StorageType.DROPBOX);
    }

    @Test
    void generateFileName_kebabsAndAddsYaml() {
        when(transliterator.toAsciiKebab("Хачапури")).thenReturn("khachapuri");
        assertThat(provider.generateFileName("Хачапури")).isEqualTo("khachapuri.yaml");
    }

    @Test
    void getOrCreateFolder_blank_returnsAppRoot_noApiCall() {
        StorageProvider.FolderRef ref = provider.getOrCreateFolder("token", "");
        assertThat(ref.id()).isEqualTo("");
        verify(dropboxClient, never()).createFolder(any(), any());
    }

    @Test
    void getOrCreateFolder_named_createsSubfolder() {
        StorageProvider.FolderRef ref = provider.getOrCreateFolder("token", "MyKukBuk");
        assertThat(ref.id()).isEqualTo("/MyKukBuk");
        assertThat(ref.name()).isEqualTo("MyKukBuk");
        verify(dropboxClient).createFolder("/MyKukBuk", "token");
    }

    @Test
    void uploadRecipeYaml_appRoot_joinsPathAndReturnsId() {
        when(dropboxClient.upload(any(), any(), eq("token")))
                .thenReturn(new DropboxClient.FileEntry("id:abc", "a.yaml", "/a.yaml", null));

        DriveService.UploadResult result = provider.uploadRecipeYaml("token", "", "a.yaml", "yaml-content");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(dropboxClient).upload(path.capture(), any(), eq("token"));
        assertThat(path.getValue()).isEqualTo("/a.yaml");
        assertThat(result.fileId()).isEqualTo("id:abc");
        assertThat(result.fileUrl()).isEqualTo("/a.yaml");
    }

    @Test
    void uploadRecipeYaml_subfolder_joinsPath() {
        when(dropboxClient.upload(any(), any(), eq("token")))
                .thenReturn(new DropboxClient.FileEntry("id:abc", "a.yaml", "/MyKukBuk/a.yaml", null));

        provider.uploadRecipeYaml("token", "/MyKukBuk", "a.yaml", "yaml");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(dropboxClient).upload(path.capture(), any(), eq("token"));
        assertThat(path.getValue()).isEqualTo("/MyKukBuk/a.yaml");
    }

    @Test
    void listRecipeFiles_firstPage_filtersYamlAndMapsCursor() {
        DropboxClient.FileEntry yaml = new DropboxClient.FileEntry("id:1", "a.yaml", "/a.yaml", "2024-01-15T10:00:00Z");
        DropboxClient.FileEntry other = new DropboxClient.FileEntry("id:2", "note.txt", "/note.txt", "2024-01-16T10:00:00Z");
        when(dropboxClient.listFolder("", 20, "token"))
                .thenReturn(new DropboxClient.ListResult(List.of(yaml, other), "CUR", true));

        GoogleDrive.DriveFileListResult result = provider.listRecipeFiles("token", "", 20, null);

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).id()).isEqualTo("id:1");
        assertThat(result.files().get(0).modifiedTime()).isEqualTo("2024-01-15T10:00:00Z");
        assertThat(result.nextPageToken()).isEqualTo("CUR");
    }

    @Test
    void listRecipeFiles_withPageToken_usesContinue_noMore() {
        when(dropboxClient.listFolderContinue("CUR", "token"))
                .thenReturn(new DropboxClient.ListResult(List.of(), null, false));

        GoogleDrive.DriveFileListResult result = provider.listRecipeFiles("token", "", 20, "CUR");

        assertThat(result.files()).isEmpty();
        assertThat(result.nextPageToken()).isNull();
    }

    @Test
    void getFileContent_delegates() {
        when(dropboxClient.downloadAsString("id:abc", "token")).thenReturn("yaml");
        assertThat(provider.getFileContent("token", "id:abc")).isEqualTo("yaml");
    }

    @Test
    void downloadFile_delegates() {
        byte[] bytes = {1, 2, 3};
        when(dropboxClient.downloadAsBytes("id:abc", "token")).thenReturn(bytes);
        assertThat(provider.downloadFile("token", "id:abc")).isEqualTo(bytes);
    }

    @Test
    void getFileMimeType_inferredFromName() {
        when(dropboxClient.getMetadata("id:img", "token"))
                .thenReturn(new DropboxClient.FileEntry("id:img", "photo.jpg", "/photo.jpg", null));
        assertThat(provider.getFileMimeType("token", "id:img")).isEqualTo("image/jpeg");
    }

    @Test
    void getFileMetadata_mapsWithInferredMime() {
        when(dropboxClient.getMetadata("id:1", "token"))
                .thenReturn(new DropboxClient.FileEntry("id:1", "a.yaml", "/a.yaml", "2024-01-15T10:00:00Z"));

        GoogleDrive.DriveFileMetadata meta = provider.getFileMetadata("token", "id:1");

        assertThat(meta.id()).isEqualTo("id:1");
        assertThat(meta.name()).isEqualTo("a.yaml");
        assertThat(meta.mimeType()).isEqualTo("application/x-yaml");
        assertThat(meta.modifiedTime()).isEqualTo("2024-01-15T10:00:00Z");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :cookbook:test --tests DropboxStorageProviderTest`
Expected: FAIL — compilation error: `DropboxStorageProvider` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `extractor/src/main/java/net/shamansoft/cookbook/service/DropboxStorageProvider.java`:

```java
package net.shamansoft.cookbook.service;

import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.DropboxClient;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.StorageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * {@link StorageProvider} backed by Dropbox (App-folder access). Paths are relative to the
 * app folder; {@code ""} is the app-folder root. Recipe/media files are addressed by Dropbox id.
 */
@Slf4j
@Service
public class DropboxStorageProvider implements StorageProvider {

    private final DropboxClient dropboxClient;
    private final Transliterator transliterator;

    @Value("${cookbook.dropbox.folder-name:}")
    private String folderName;

    public DropboxStorageProvider(DropboxClient dropboxClient, Transliterator transliterator) {
        this.dropboxClient = dropboxClient;
        this.transliterator = transliterator;
    }

    @Override
    public StorageType type() {
        return StorageType.DROPBOX;
    }

    @Override
    public String generateFileName(String title) {
        String lowerAscii = transliterator.toAsciiKebab(title);
        String base = lowerAscii == null || lowerAscii.isEmpty()
                ? "recipe-" + System.currentTimeMillis()
                : lowerAscii;
        return base + ".yaml";
    }

    @Override
    public FolderRef getOrCreateFolder(String accessToken, String folderName) {
        String sub = normalizeSubfolder(folderName);
        if (sub.isEmpty()) {
            // App-folder root: nothing to create (Dropbox provisions the app folder on first write).
            return new FolderRef("", folderName == null ? "" : folderName.trim());
        }
        dropboxClient.createFolder(sub, accessToken);
        return new FolderRef(sub, folderName.trim());
    }

    @Override
    public DriveService.UploadResult uploadRecipeYaml(String accessToken, String folderId, String fileName, String content) {
        String path = joinPath(folderId, fileName);
        DropboxClient.FileEntry entry =
                dropboxClient.upload(path, content.getBytes(StandardCharsets.UTF_8), accessToken);
        String url = entry.pathDisplay() != null ? entry.pathDisplay() : path;
        return new DriveService.UploadResult(entry.id(), url);
    }

    @Override
    public GoogleDrive.DriveFileListResult listRecipeFiles(String accessToken, String folderId, int pageSize, String pageToken) {
        DropboxClient.ListResult result = (pageToken == null)
                ? dropboxClient.listFolder(rootPath(folderId), pageSize, accessToken)
                : dropboxClient.listFolderContinue(pageToken, accessToken);

        List<GoogleDrive.DriveFileInfo> files = result.files().stream()
                .filter(f -> f.name() != null && f.name().endsWith(".yaml"))
                .map(f -> new GoogleDrive.DriveFileInfo(f.id(), f.name(), f.serverModified()))
                .toList();

        String nextPageToken = result.hasMore() ? result.cursor() : null;
        return new GoogleDrive.DriveFileListResult(files, nextPageToken);
    }

    @Override
    public String getFileContent(String accessToken, String fileId) {
        return dropboxClient.downloadAsString(fileId, accessToken);
    }

    @Override
    public byte[] downloadFile(String accessToken, String fileId) {
        return dropboxClient.downloadAsBytes(fileId, accessToken);
    }

    @Override
    public String getFileMimeType(String accessToken, String fileId) {
        return mimeTypeForName(dropboxClient.getMetadata(fileId, accessToken).name());
    }

    @Override
    public GoogleDrive.DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        DropboxClient.FileEntry e = dropboxClient.getMetadata(fileId, accessToken);
        return new GoogleDrive.DriveFileMetadata(e.id(), e.name(), mimeTypeForName(e.name()), e.serverModified());
    }

    /** App-folder root is "" (also accept "/"); a stored subfolder path is returned as-is. */
    static String rootPath(String folderId) {
        return (folderId == null || folderId.isBlank() || "/".equals(folderId)) ? "" : folderId;
    }

    static String joinPath(String folderId, String fileName) {
        String root = rootPath(folderId);
        return root.isEmpty() ? "/" + fileName : root + "/" + fileName;
    }

    static String normalizeSubfolder(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return "";
        }
        String n = folderName.trim();
        return n.startsWith("/") ? n : "/" + n;
    }

    static String mimeTypeForName(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "application/x-yaml";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :cookbook:test --tests DropboxStorageProviderTest`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/service/DropboxStorageProvider.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/DropboxStorageProviderTest.java
git commit -m "feat(dropbox): add DropboxStorageProvider (App-folder path semantics, id-addressed files)"
```

---

## Task 7: Route `RecipeService` / `RecipeMediaProxyService` through the resolver

This is one deliverable: both consumers select the provider by `storage.type()` instead of injecting the concrete Google bean. Recipe/media behavior is unchanged for Google users; the hardcoded "Expected Google Drive" guards are removed (multi-provider is now supported). Their tests are updated mechanically by keeping the mock variable name `driveService` but retyping it as `StorageProvider` and stubbing the resolver.

**Files:**
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java`
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeMediaProxyService.java`
- Modify tests: `RecipeServiceListGetTest.java`, `RecipeServiceCreateRecipeTest.java`, `RecipeServiceFromDescriptionTest.java`, `RecipeServiceErrorHandlingTest.java`, `RecipeServiceTest.java`

**Interfaces:**
- Consumes: `StorageProviderResolver.resolve(StorageType)` (Task 4), `StorageProvider` (Task 4).
- Produces: no signature change to public `RecipeService`/`RecipeMediaProxyService` methods; only the injected collaborator changes (constructor 2nd param of `RecipeService` becomes `StorageProviderResolver`).

- [ ] **Step 1: Update the two guard tests in `RecipeServiceListGetTest` to expect failure first**

In `extractor/src/test/java/net/shamansoft/cookbook/service/RecipeServiceListGetTest.java`, **delete** these two now-obsolete tests (multi-provider removes the guard):

Delete the method spanning the `@Test` at "listRecipes: throws IllegalStateException for non-Drive storage" (`listRecipes_throwsForWrongStorageType`, lines ~155-166):

```java
    @Test
    @DisplayName("listRecipes: throws IllegalStateException for non-Drive storage")
    void listRecipes_throwsForWrongStorageType() {
        StorageInfo dropbox = StorageInfo.builder()
                .type(StorageType.DROPBOX).connected(true)
                .accessToken(ACCESS_TOKEN).folderId(FOLDER_ID).build();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(dropbox);

        assertThatThrownBy(() -> recipeService.listRecipes(USER_ID, 20, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DROPBOX");
    }
```

Delete the method "getRecipe: throws IllegalStateException for non-Drive storage" (`getRecipe_throwsForWrongStorageType`, lines ~227-237):

```java
    @Test
    @DisplayName("getRecipe: throws IllegalStateException for non-Drive storage")
    void getRecipe_throwsForWrongStorageType() {
        StorageInfo dropbox = StorageInfo.builder()
                .type(StorageType.DROPBOX).connected(true)
                .accessToken(ACCESS_TOKEN).folderId(FOLDER_ID).build();
        when(storageService.getStorageInfo(USER_ID)).thenReturn(dropbox);

        assertThatThrownBy(() -> recipeService.getRecipe(USER_ID, FILE_ID))
                .isInstanceOf(IllegalStateException.class);
    }
```

- [ ] **Step 2: Rewire `RecipeServiceListGetTest` collaborators (compile-fail expected)**

In `RecipeServiceListGetTest.java`, change the drive mock's type and add a resolver, then stub the resolver in `setUp()`.

Replace:
```java
    @Mock private ContentHashService contentHashService;
    @Mock private DriveService driveService;
    @Mock private StorageService storageService;
```
with:
```java
    @Mock private ContentHashService contentHashService;
    @Mock private StorageProvider driveService;
    @Mock private StorageProviderResolver storageProviderResolver;
    @Mock private StorageService storageService;
```

Then at the very start of the `setUp()` body (before building `connectedStorage`), add:
```java
        org.mockito.Mockito.lenient()
                .when(storageProviderResolver.resolve(any(StorageType.class)))
                .thenReturn(driveService);
```
(`any` and `StorageType` are already imported in this file.)

- [ ] **Step 3: Rewire the four `new RecipeService(...)` test setups (compile-fail expected)**

For each of `RecipeServiceCreateRecipeTest.java`, `RecipeServiceFromDescriptionTest.java`, `RecipeServiceErrorHandlingTest.java`, `RecipeServiceTest.java`:

(a) Change the drive mock field type from `DriveService` to `StorageProvider` and add a resolver mock beside it. For the `@Mock`-annotated files (`RecipeServiceCreateRecipeTest`, `RecipeServiceFromDescriptionTest`, `RecipeServiceErrorHandlingTest`), replace:
```java
    @Mock
    private DriveService driveService;
```
with:
```java
    @Mock
    private StorageProvider driveService;
    @Mock
    private StorageProviderResolver storageProviderResolver;
```

(b) In those three files, immediately before the `recipeService = new RecipeService(...)` line inside `setUp()`, add:
```java
        org.mockito.Mockito.lenient()
                .when(storageProviderResolver.resolve(any(net.shamansoft.cookbook.dto.StorageType.class)))
                .thenReturn(driveService);
```
and change the constructor call's 2nd argument from `driveService` to `storageProviderResolver`, e.g.:
```java
        recipeService = new RecipeService(contentHashService, storageProviderResolver, storageService,
                recipeStoreService, recipeParser, recipeMapper, htmlExtractor,
                compressor, transformer, validationService, geminiRestTransformer);
```

(c) In `RecipeServiceTest.java` (two `@Test` methods that build the service locally with `mock(...)`), in **each** test method replace:
```java
        DriveService driveService = mock(DriveService.class);
```
with:
```java
        StorageProvider driveService = mock(StorageProvider.class);
        StorageProviderResolver storageProviderResolver = mock(StorageProviderResolver.class);
        when(storageProviderResolver.resolve(any(net.shamansoft.cookbook.dto.StorageType.class))).thenReturn(driveService);
```
and change each `new RecipeService(contentHashService, driveService, storageService, ...)` to `new RecipeService(contentHashService, storageProviderResolver, storageService, ...)`. `any` is already statically imported in `RecipeServiceTest`.

> All existing `when(driveService.uploadRecipeYaml(...))`, `when(driveService.listRecipeFiles(...))`, `when(driveService.getFileContent(...))`, `when(driveService.getFileMetadata(...))`, `when(driveService.getFileMimeType(...))`, `when(driveService.downloadFile(...))`, and `when(driveService.generateFileName(...))` stubs stay valid unchanged, because `StorageProvider` declares those exact method names.

- [ ] **Step 4: Run the consumer tests to confirm they now fail against unchanged production code**

Run: `./gradlew :cookbook:test --tests "RecipeService*"`
Expected: FAIL — compilation error in `RecipeService` construction (2nd arg type mismatch: `StorageProviderResolver` vs the current `DriveService` field). This proves the tests now demand the refactor.

- [ ] **Step 5: Refactor `RecipeService` to use the resolver**

Overwrite `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java` with:

```java
package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.client.GoogleDrive;
import net.shamansoft.cookbook.dto.Compression;
import net.shamansoft.cookbook.dto.RecipeDto;
import net.shamansoft.cookbook.dto.RecipeItemResult;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.html.HtmlExtractor;
import net.shamansoft.cookbook.service.gemini.GeminiRestTransformer;
import net.shamansoft.recipe.model.Recipe;
import net.shamansoft.recipe.parser.RecipeSerializeException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core business logic for recipe operations.
 * Routes storage I/O to the provider selected by the user's connected storage type.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

    private final ContentHashService contentHashService;
    private final StorageProviderResolver storageProviderResolver;
    private final StorageService storageService;
    private final RecipeStoreService recipeStoreService;
    private final RecipeParser recipeParser;
    private final RecipeMapper recipeMapper;
    private final HtmlExtractor htmlExtractor;
    private final Compressor compressor;
    private final Transformer transformer;  // AdaptiveCleaningTransformerService (@Primary)
    private final RecipeValidationService validationService;
    private final GeminiRestTransformer geminiRestTransformer;

    public RecipeResponse createRecipe(String userId, String url, String sourceHtml, Compression compression, String title) throws IOException {
        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.folderId() == null) {
            throw new StorageNotConnectedException(
                    "No folder configured for recipe storage. Please reconnect storage or configure a folder.");
        }

        StorageProvider provider = storageProviderResolver.resolve(storage.type());

        var transformerResponse = createOrGetCached(url, sourceHtml, compression);
        RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder()
                .title(title)
                .url(url)
                .isRecipe(transformerResponse.isRecipe());

        if (transformerResponse.isRecipe()) {
            List<RecipeItemResult> uploadedRecipes = new ArrayList<>();

            for (Recipe recipe : transformerResponse.recipes()) {
                String recipeTitle = recipe.metadata() != null && recipe.metadata().title() != null
                        ? recipe.metadata().title() : title;
                String fileName = provider.generateFileName(recipeTitle);
                String yamlContent = convertRecipeToYaml(recipe);
                DriveService.UploadResult uploadResult = provider.uploadRecipeYaml(
                        storage.accessToken(), storage.folderId(), fileName, yamlContent);
                uploadedRecipes.add(new RecipeItemResult(recipeTitle, uploadResult.fileId(), uploadResult.fileUrl()));
            }

            responseBuilder.recipes(uploadedRecipes);

            // Backward compat: populate top-level fields from the first recipe
            if (!uploadedRecipes.isEmpty()) {
                RecipeItemResult first = uploadedRecipes.get(0);
                responseBuilder
                        .title(first.title())
                        .driveFileId(first.driveFileId())
                        .driveFileUrl(first.driveFileUrl());
            }
        } else {
            log.info("Content is not a recipe. Skipping storage - URL: {}", url);
        }

        return responseBuilder.build();
    }

    public RecipeResponse createRecipeFromDescription(String userId, String description, String title, String url, Compression compression) throws IOException {
        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.folderId() == null) {
            throw new StorageNotConnectedException(
                    "No folder configured for recipe storage. Please reconnect storage or configure a folder.");
        }

        StorageProvider provider = storageProviderResolver.resolve(storage.type());

        String plainDescription = decompressContent(description, compression);
        var transformerResponse = geminiRestTransformer.transformDescription(plainDescription);

        List<RecipeItemResult> uploadedRecipes = new ArrayList<>();
        for (Recipe recipe : transformerResponse.recipes()) {
            String recipeTitle = recipe.metadata() != null && recipe.metadata().title() != null
                    ? recipe.metadata().title() : title;
            String fileName = provider.generateFileName(recipeTitle);
            String yamlContent = convertRecipeToYaml(recipe);
            DriveService.UploadResult uploadResult = provider.uploadRecipeYaml(
                    storage.accessToken(), storage.folderId(), fileName, yamlContent);
            uploadedRecipes.add(new RecipeItemResult(recipeTitle, uploadResult.fileId(), uploadResult.fileUrl()));
        }

        RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder()
                .title(title)
                .url(url)
                .isRecipe(true)
                .recipes(uploadedRecipes);

        if (!uploadedRecipes.isEmpty()) {
            RecipeItemResult first = uploadedRecipes.get(0);
            responseBuilder
                    .title(first.title())
                    .driveFileId(first.driveFileId())
                    .driveFileUrl(first.driveFileUrl());
        }

        return responseBuilder.build();
    }

    private Transformer.Response createOrGetCached(String url, String sourceHtml, Compression compression) throws IOException {
        String contentHash = contentHashService.generateContentHash(url);
        Optional<RecipeStoreService.CachedRecipes> cached = recipeStoreService.findCachedRecipes(contentHash);
        if (cached.isPresent()) {
            RecipeStoreService.CachedRecipes hit = cached.get();
            if (hit.valid()) {
                List<Recipe> recipes = hit.recipes();
                log.debug("Cache HIT: {} recipe(s) for hash: {}", recipes.size(), contentHash);
                return recipes.size() == 1
                        ? Transformer.Response.recipe(recipes.get(0))
                        : Transformer.Response.recipes(recipes);
            } else {
                return Transformer.Response.notRecipe();
            }
        }

        // Cache miss — decompress then extract
        String decompressed = null;
        try {
            decompressed = decompressContent(sourceHtml, compression);
        } catch (IOException e) {
            log.warn("Failed to decompress HTML, falling back to URL fetch: {}", e.getMessage());
        }
        String html = htmlExtractor.extractHtml(url, decompressed);
        log.info("Extracted HTML - URL: {}, HTML length: {} chars, Content hash: {}", url, html.length(), contentHash);

        var response = transformer.transform(html, url);

        if (response.isRecipe()) {
            recipeStoreService.storeValidRecipes(contentHash, url, response.recipes());
            log.debug("Cached {} recipe(s) for hash: {}", response.recipes().size(), contentHash);
        } else {
            log.warn("Gemini determined content is NOT a recipe - URL: {}, Hash: {}", url, contentHash);
            recipeStoreService.storeInvalidRecipe(contentHash, url);
        }
        return response;
    }

    /**
     * List all recipes from the user's storage folder with full parsing.
     */
    public RecipeListResult listRecipes(String userId, int pageSize, String pageToken) {
        log.info("Listing recipes for user: {}, pageSize: {}, pageToken: {}",
                userId, pageSize, pageToken);

        StorageInfo storage = storageService.getStorageInfo(userId);

        if (storage.folderId() == null) {
            throw new StorageNotConnectedException(
                    "No folder configured for recipe storage. Please reconnect storage or configure a folder.");
        }

        StorageProvider provider = storageProviderResolver.resolve(storage.type());
        String folderId = storage.folderId();
        log.debug("Using folder ID from user profile: {}", folderId);

        GoogleDrive.DriveFileListResult driveFiles = provider.listRecipeFiles(
                storage.accessToken(), folderId, pageSize, pageToken);

        log.info("Found {} YAML files in folder: {}", driveFiles.files().size(), folderId);

        List<RecipeDto> recipes = driveFiles.files().stream()
                .map(file -> parseRecipeFile(provider, storage.accessToken(), file))
                .filter(Objects::nonNull)
                .toList();

        log.info("Successfully parsed {} out of {} recipes", recipes.size(), driveFiles.files().size());

        return new RecipeListResult(recipes, driveFiles.nextPageToken());
    }

    /**
     * Get single recipe by storage file ID.
     */
    public RecipeDto getRecipe(String userId, String fileId) {
        log.info("Getting recipe: {} for user: {}", fileId, userId);

        StorageInfo storage = storageService.getStorageInfo(userId);
        StorageProvider provider = storageProviderResolver.resolve(storage.type());

        try {
            GoogleDrive.DriveFileMetadata metadata = provider.getFileMetadata(
                    storage.accessToken(), fileId);

            log.debug("Found file: {} ({})", metadata.name(), metadata.mimeType());

            String yamlContent = provider.getFileContent(storage.accessToken(), fileId);
            Recipe recipe = recipeParser.parse(yamlContent);
            RecipeDto dto = recipeMapper.toDto(recipe, metadata);

            log.info("Successfully retrieved recipe: {}", dto.getTitle());
            return dto;

        } catch (Exception e) {
            log.error("Failed to get recipe: {}", fileId, e);

            if (e.getMessage() != null &&
                    (e.getMessage().contains("404") || e.getMessage().contains("not found"))) {
                throw new RecipeNotFoundException("Recipe not found: " + fileId, e);
            }

            throw e;
        }
    }

    private RecipeDto parseRecipeFile(StorageProvider provider, String authToken, GoogleDrive.DriveFileInfo fileInfo) {
        try {
            log.debug("Parsing recipe file: {} ({})", fileInfo.name(), fileInfo.id());
            String yamlContent = provider.getFileContent(authToken, fileInfo.id());
            Recipe recipe = recipeParser.parse(yamlContent);
            RecipeDto dto = recipeMapper.toDto(recipe, fileInfo);
            log.debug("Successfully parsed: {}", dto.getTitle());
            return dto;
        } catch (Exception e) {
            log.error("Failed to parse recipe file: {} - Skipping. Error: {}",
                    fileInfo.name(), e.getMessage());
            return null;
        }
    }

    /**
     * Decompresses content only when compression is BASE64_GZIP.
     */
    private String decompressContent(String content, Compression compression) throws IOException {
        if (content == null || content.isBlank()) return content;
        if (compression != Compression.BASE64_GZIP) return content;
        return compressor.decompress(content);
    }

    private String convertRecipeToYaml(Recipe recipe) {
        try {
            return validationService.toYaml(recipe);
        } catch (RecipeSerializeException e) {
            log.error("Failed to serialize Recipe to YAML - Title: {}",
                    recipe.metadata() != null ? recipe.metadata().title() : "N/A", e);
            throw new RuntimeException("Failed to convert recipe to YAML for storage", e);
        }
    }

    public record RecipeListResult(List<RecipeDto> recipes, String nextPageToken) {
    }
}
```

- [ ] **Step 6: Refactor `RecipeMediaProxyService` to use the resolver**

Overwrite `extractor/src/main/java/net/shamansoft/cookbook/service/RecipeMediaProxyService.java` with:

```java
package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.exception.RecipeNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Proxies media files from the user's connected storage provider.
 * Validates the user session and uses their access token to fetch the file.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeMediaProxyService {

    private final StorageProviderResolver storageProviderResolver;
    private final StorageService storageService;

    public MediaContent getMediaFile(String userId, String driveFileId) {
        log.info("Proxying media file: {} for user: {}", driveFileId, userId);

        StorageInfo storage = storageService.getStorageInfo(userId);
        StorageProvider provider = storageProviderResolver.resolve(storage.type());

        try {
            byte[] content = provider.downloadFile(storage.accessToken(), driveFileId);
            String mimeType = provider.getFileMimeType(storage.accessToken(), driveFileId);

            log.info("Successfully proxied media: {} ({} bytes, type: {})",
                    driveFileId, content.length, mimeType);

            return new MediaContent(content, mimeType);

        } catch (Exception e) {
            log.error("Failed to proxy media file: {}", driveFileId, e);

            if (e.getMessage() != null &&
                    (e.getMessage().contains("404") || e.getMessage().contains("not found"))) {
                throw new RecipeNotFoundException("Media file not found: " + driveFileId, e);
            }

            throw e;
        }
    }

    public record MediaContent(byte[] data, String mimeType) {
    }
}
```

- [ ] **Step 7: Run the consumer tests to verify they pass**

Run: `./gradlew :cookbook:test --tests "RecipeService*"`
Expected: PASS (all RecipeService test classes green).

- [ ] **Step 8: Run the broader suite touched by the media/recipe context**

Run: `./gradlew :cookbook:test --tests RecipeControllerTest --tests MediaControllerTest`
Expected: PASS. (Context now wires `StorageProviderResolver` + both providers; media/recipe endpoints behave identically for Google users. If `MediaControllerTest` does not exist, run only `RecipeControllerTest`.)

- [ ] **Step 9: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/service/RecipeService.java \
        extractor/src/main/java/net/shamansoft/cookbook/service/RecipeMediaProxyService.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/RecipeServiceListGetTest.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/RecipeServiceCreateRecipeTest.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/RecipeServiceFromDescriptionTest.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/RecipeServiceErrorHandlingTest.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/RecipeServiceTest.java
git commit -m "refactor(storage): route RecipeService/RecipeMediaProxyService through StorageProviderResolver"
```

---

## Task 8: `StorageService` — `connectDropbox`, provider-aware refresh, revoke-on-disconnect

**Files:**
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/service/StorageService.java`
- Modify test: `extractor/src/test/java/net/shamansoft/cookbook/service/StorageServiceTest.java`
- Modify int test: `extractor/src/intTest/java/net/shamansoft/cookbook/service/StorageServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `DropboxAuthClient` (Task 2), `DropboxStorageProvider` (Task 6), `StorageEntity.toMap()` (Task 1).
- Produces: `StorageService.FolderInfo connectDropbox(String userId, String authorizationCode, String redirectUri, String folderName)`; refresh & disconnect become provider-aware. Public `getStorageInfo`, `disconnectStorage`, `connectGoogleDrive`, `FolderInfo` signatures unchanged.

- [ ] **Step 1: Write the failing tests (extend `StorageServiceTest`)**

In `StorageServiceTest.java`, add two Dropbox mocks next to the existing `@Mock` fields:

```java
    @Mock
    private net.shamansoft.cookbook.client.DropboxAuthClient dropboxAuthClient;
    @Mock
    private DropboxStorageProvider dropboxStorageProvider;
```

Change the constructor call in `setUp()` from:
```java
        storageService = new StorageService(firestore, tokenEncryptionService, googleAuthClient, googleDrive);
```
to:
```java
        storageService = new StorageService(firestore, tokenEncryptionService, googleAuthClient, googleDrive,
                dropboxAuthClient, dropboxStorageProvider);
```

Add these test methods to `StorageServiceTest`:

```java
    @Test
    @DisplayName("Should connect Dropbox and persist type=dropbox with app-root folder")
    void shouldConnectDropbox() throws Exception {
        net.shamansoft.cookbook.client.DropboxAuthClient.TokenResponse tokens =
                new net.shamansoft.cookbook.client.DropboxAuthClient.TokenResponse(ACCESS_TOKEN, REFRESH_TOKEN, 14400L);
        when(dropboxAuthClient.exchangeAuthorizationCode("dbx-code", "sar://cb")).thenReturn(tokens);
        when(dropboxStorageProvider.getOrCreateFolder(ACCESS_TOKEN, "kukbuk"))
                .thenReturn(new StorageProvider.FolderRef("", "kukbuk"));
        when(tokenEncryptionService.encrypt(ACCESS_TOKEN)).thenReturn(ENCRYPTED_ACCESS);
        when(tokenEncryptionService.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_REFRESH);
        when(userDocument.update(eq("storage"), any(Map.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        StorageService.FolderInfo result = storageService.connectDropbox(USER_ID, "dbx-code", "sar://cb", null);

        assertThat(result.folderId()).isEqualTo("");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(userDocument).update(eq("storage"), captor.capture());
        assertThat(captor.getValue().get("type")).isEqualTo("dropbox");
        assertThat(captor.getValue().get("accessToken")).isEqualTo(ENCRYPTED_ACCESS);
    }

    @Test
    @DisplayName("Should refresh Dropbox token via DropboxAuthClient")
    void shouldRefreshDropboxToken() throws Exception {
        long past = System.currentTimeMillis() / 1000 - 3600;
        Timestamp expiredAt = Timestamp.ofTimeSecondsAndNanos(past, 0);
        StorageEntity entity = StorageEntity.builder()
                .type("dropbox").connected(true)
                .accessToken(ENCRYPTED_ACCESS).refreshToken(ENCRYPTED_REFRESH)
                .expiresAt(expiredAt).connectedAt(Timestamp.now()).folderId("").build();

        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.get("storage")).thenReturn(entity.toMap());
        when(dropboxAuthClient.isTokenExpired(expiredAt)).thenReturn(true);
        when(tokenEncryptionService.decrypt(ENCRYPTED_REFRESH)).thenReturn(REFRESH_TOKEN);

        Timestamp newExpiry = Timestamp.ofTimeSecondsAndNanos(System.currentTimeMillis() / 1000 + 14400, 0);
        when(dropboxAuthClient.refreshAccessToken(REFRESH_TOKEN))
                .thenReturn(new net.shamansoft.cookbook.client.DropboxAuthClient.RefreshTokenResponse("dbx-new", newExpiry));
        when(tokenEncryptionService.encrypt("dbx-new")).thenReturn("enc-new");
        when(userDocument.update(anyString(), any(), anyString(), any())).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        StorageInfo result = storageService.getStorageInfo(USER_ID);

        assertThat(result.accessToken()).isEqualTo("dbx-new");
        assertThat(result.type()).isEqualTo(StorageType.DROPBOX);
        verify(dropboxAuthClient).refreshAccessToken(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Should revoke Dropbox token before clearing storage on disconnect")
    void shouldRevokeDropboxTokenOnDisconnect() throws Exception {
        StorageEntity entity = StorageEntity.builder()
                .type("dropbox").connected(true)
                .accessToken(ENCRYPTED_ACCESS).folderId("").build();
        when(userDocument.get()).thenReturn(documentFuture);
        when(documentFuture.get()).thenReturn(documentSnapshot);
        when(documentSnapshot.get("storage")).thenReturn(entity.toMap());
        when(tokenEncryptionService.decrypt(ENCRYPTED_ACCESS)).thenReturn(ACCESS_TOKEN);
        when(userDocument.update("storage", null)).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        storageService.disconnectStorage(USER_ID);

        verify(dropboxAuthClient).revokeToken(ACCESS_TOKEN);
        verify(userDocument).update("storage", null);
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :cookbook:test --tests StorageServiceTest`
Expected: FAIL — compilation error: `StorageService` has no 6-arg constructor and no `connectDropbox`.

- [ ] **Step 3: Extend the `StorageService` constructor and fields**

In `StorageService.java`, add imports at the top (with the other imports):
```java
import net.shamansoft.cookbook.client.DropboxAuthClient;
```

Replace the field block and constructor:
```java
    private final Firestore firestore;
    private final TokenEncryptionService tokenEncryptionService;
    private final GoogleAuthClient googleAuthClient;
    private final GoogleDrive googleDrive;
    @Value("${cookbook.drive.folder-name}")
    private String defaultFolderName;

    public StorageService(Firestore firestore,
                          TokenEncryptionService tokenEncryptionService,
                          GoogleAuthClient googleAuthClient,
                          GoogleDrive googleDrive) {
        this.firestore = firestore;
        this.tokenEncryptionService = tokenEncryptionService;
        this.googleAuthClient = googleAuthClient;
        this.googleDrive = googleDrive;
    }
```
with:
```java
    private final Firestore firestore;
    private final TokenEncryptionService tokenEncryptionService;
    private final GoogleAuthClient googleAuthClient;
    private final GoogleDrive googleDrive;
    private final DropboxAuthClient dropboxAuthClient;
    private final net.shamansoft.cookbook.service.DropboxStorageProvider dropboxStorageProvider;
    @Value("${cookbook.drive.folder-name}")
    private String defaultFolderName;
    @Value("${cookbook.dropbox.folder-name:}")
    private String defaultDropboxFolderName;

    public StorageService(Firestore firestore,
                          TokenEncryptionService tokenEncryptionService,
                          GoogleAuthClient googleAuthClient,
                          GoogleDrive googleDrive,
                          DropboxAuthClient dropboxAuthClient,
                          net.shamansoft.cookbook.service.DropboxStorageProvider dropboxStorageProvider) {
        this.firestore = firestore;
        this.tokenEncryptionService = tokenEncryptionService;
        this.googleAuthClient = googleAuthClient;
        this.googleDrive = googleDrive;
        this.dropboxAuthClient = dropboxAuthClient;
        this.dropboxStorageProvider = dropboxStorageProvider;
    }
```

- [ ] **Step 4: Add `connectDropbox` + a generic `storeConnection` helper**

In `StorageService.java`, add these methods (e.g. directly after `connectGoogleDrive`):

```java
    /**
     * Connect Dropbox storage: exchange the authorization code, provision the app-folder
     * (or an optional subfolder), and store the encrypted connection.
     */
    public FolderInfo connectDropbox(String userId, String authorizationCode, String redirectUri,
                                     String folderName) {
        log.info("Connecting Dropbox storage for user: {}", userId);
        try {
            DropboxAuthClient.TokenResponse tokens =
                    dropboxAuthClient.exchangeAuthorizationCode(authorizationCode, redirectUri);

            String resolvedFolderName = (folderName == null || folderName.isBlank())
                    ? defaultDropboxFolderName
                    : folderName.trim();

            StorageProvider.FolderRef folder =
                    dropboxStorageProvider.getOrCreateFolder(tokens.accessToken(), resolvedFolderName);
            log.info("Using Dropbox folder '{}' (id='{}')", folder.name(), folder.id());

            storeConnection(userId, StorageType.DROPBOX, tokens.accessToken(), tokens.refreshToken(),
                    tokens.expiresIn(), folder.id(), folder.name());

            return new FolderInfo(folder.id(), folder.name());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to connect Dropbox for user {}: {}", userId, e.getMessage(), e);
            throw new DatabaseUnavailableException("Failed to connect Dropbox: " + e.getMessage(), e);
        }
    }

    /**
     * Persist an encrypted storage connection of any provider type.
     */
    private void storeConnection(String userId, StorageType type, String accessToken, String refreshToken,
                                 long expiresIn, String folderId, String folderName) {
        try {
            StorageEntity storageEntity = StorageEntity.builder()
                    .type(type.getFirestoreValue())
                    .connected(true)
                    .accessToken(tokenEncryptionService.encrypt(accessToken))
                    .refreshToken(refreshToken != null ? tokenEncryptionService.encrypt(refreshToken) : null)
                    .expiresAt(Timestamp.ofTimeSecondsAndNanos(
                            System.currentTimeMillis() / 1000 + expiresIn, 0))
                    .connectedAt(Timestamp.now())
                    .folderId(folderId)
                    .folderName(folderName)
                    .build();

            firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .update(STORAGE_FIELD, storageEntity.toMap())
                    .get();

            log.info("{} connected successfully for user: {}", type, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseUnavailableException("Failed to store connection: operation interrupted", e);
        } catch (ExecutionException e) {
            throw new DatabaseUnavailableException("Failed to store connection", e);
        } catch (Exception e) {
            throw new DatabaseUnavailableException("Failed to encrypt tokens or update Firestore", e);
        }
    }
```

- [ ] **Step 5: Make token refresh provider-aware**

In `StorageService.getFreshStorageInfo(...)`, replace the single Google refresh call:
```java
            // Call GoogleAuthClient to refresh token
            GoogleAuthClient.RefreshTokenResponse response = googleAuthClient.refreshAccessToken(refreshToken);

            log.info("Successfully refreshed OAuth token for user: {}", userId);

            // Update Firestore with new token
            String encryptedAccessToken = tokenEncryptionService.encrypt(response.accessToken());

            firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .update(
                            STORAGE_FIELD + ".accessToken", encryptedAccessToken,
                            STORAGE_FIELD + ".expiresAt", response.expiresAt()
                    )
                    .get();
```
with:
```java
            // Refresh via the provider's auth client, selected by stored type.
            StorageType storageType = storage.type() != null
                    ? StorageType.fromFirestoreValue(storage.type())
                    : StorageType.GOOGLE_DRIVE;

            final String newAccessToken;
            final Timestamp newExpiresAt;
            if (storageType == StorageType.DROPBOX) {
                DropboxAuthClient.RefreshTokenResponse response = dropboxAuthClient.refreshAccessToken(refreshToken);
                newAccessToken = response.accessToken();
                newExpiresAt = response.expiresAt();
            } else {
                GoogleAuthClient.RefreshTokenResponse response = googleAuthClient.refreshAccessToken(refreshToken);
                newAccessToken = response.accessToken();
                newExpiresAt = response.expiresAt();
            }

            log.info("Successfully refreshed OAuth token for user: {}", userId);

            // Update Firestore with new token
            String encryptedAccessToken = tokenEncryptionService.encrypt(newAccessToken);

            firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .update(
                            STORAGE_FIELD + ".accessToken", encryptedAccessToken,
                            STORAGE_FIELD + ".expiresAt", newExpiresAt
                    )
                    .get();
```
Then, in the `return StorageInfo.builder()...` block at the end of `getFreshStorageInfo`, replace the two references to `response.accessToken()` and `response.expiresAt()`:
```java
                    .accessToken(response.accessToken())  // Decrypted new token
```
becomes
```java
                    .accessToken(newAccessToken)  // Decrypted new token
```
and
```java
                    .expiresAt(response.expiresAt().toDate().toInstant())
```
becomes
```java
                    .expiresAt(newExpiresAt.toDate().toInstant())
```

- [ ] **Step 6: Make the expiry check provider-aware in `getStorageInfo`**

In `getStorageInfo(...)`, replace step 4:
```java
            // 4. Check if token needs refresh
            if (googleAuthClient.isTokenExpired(storageEntity.expiresAt())) {
```
with:
```java
            // 4. Check if token needs refresh (provider-aware)
            StorageType storageType = storageEntity.type() != null
                    ? StorageType.fromFirestoreValue(storageEntity.type())
                    : StorageType.GOOGLE_DRIVE;
            boolean expired = (storageType == StorageType.DROPBOX)
                    ? dropboxAuthClient.isTokenExpired(storageEntity.expiresAt())
                    : googleAuthClient.isTokenExpired(storageEntity.expiresAt());
            if (expired) {
```

- [ ] **Step 7: Revoke Dropbox token before clearing storage on disconnect**

In `StorageService.disconnectStorage(...)`, add a revoke call as the first line of the method body (before the existing `log.info("Disconnecting storage...")` / try block):
```java
    public void disconnectStorage(String userId) {
        revokeProviderTokenBestEffort(userId);
        log.info("Disconnecting storage for user: {}", userId);
        // ... existing body unchanged ...
```
Then add this private helper (e.g. right below `disconnectStorage`):
```java
    /**
     * If the connected provider is Dropbox, revoke its token server-side before disconnect.
     * Best-effort: never blocks or fails the disconnect.
     */
    @SuppressWarnings("unchecked")
    private void revokeProviderTokenBestEffort(String userId) {
        try {
            DocumentSnapshot doc = firestore.collection(USERS_COLLECTION)
                    .document(userId).get().get();
            Map<String, Object> storageMap = (Map<String, Object>) doc.get("storage");
            if (storageMap == null) {
                return;
            }
            String type = (String) storageMap.get("type");
            if (!StorageType.DROPBOX.getFirestoreValue().equals(type)) {
                return;
            }
            String encryptedAccess = (String) storageMap.get("accessToken");
            if (encryptedAccess == null) {
                return;
            }
            String accessToken = tokenEncryptionService.decrypt(encryptedAccess);
            dropboxAuthClient.revokeToken(accessToken);
        } catch (Exception e) {
            log.warn("Best-effort Dropbox token revoke failed for user {}: {}", userId, e.getMessage());
        }
    }
```

- [ ] **Step 8: Run the unit tests**

Run: `./gradlew :cookbook:test --tests StorageServiceTest`
Expected: PASS — new Dropbox tests green; existing Google connect/refresh/disconnect tests still green (Google disconnect tests pass because `userDocument.get()` is unstubbed → the best-effort revoke swallows the resulting error and proceeds to clear storage).

- [ ] **Step 9: Update the integration test constructor**

In `extractor/src/intTest/java/net/shamansoft/cookbook/service/StorageServiceIntegrationTest.java`, replace:
```java
        storageService = new StorageService(firestore, tokenEncryptionService, googleAuthClient, googleDrive);
```
with:
```java
        net.shamansoft.cookbook.client.DropboxAuthClient dropboxAuthClient =
                mock(net.shamansoft.cookbook.client.DropboxAuthClient.class);
        net.shamansoft.cookbook.service.DropboxStorageProvider dropboxStorageProvider =
                mock(net.shamansoft.cookbook.service.DropboxStorageProvider.class);
        storageService = new StorageService(firestore, tokenEncryptionService, googleAuthClient, googleDrive,
                dropboxAuthClient, dropboxStorageProvider);
```

- [ ] **Step 10: Run the integration test (Docker required)**

Run: `./gradlew :cookbook:intTest --tests StorageServiceIntegrationTest`
Expected: PASS — the existing Google-Drive emulator scenarios are unchanged (disconnect reads `type=googleDrive` → no revoke).

- [ ] **Step 11: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/service/StorageService.java \
        extractor/src/test/java/net/shamansoft/cookbook/service/StorageServiceTest.java \
        extractor/src/intTest/java/net/shamansoft/cookbook/service/StorageServiceIntegrationTest.java
git commit -m "feat(dropbox): StorageService connectDropbox + provider-aware refresh + revoke-on-disconnect"
```

---

## Task 9: `StorageController` — Dropbox connect + provider-neutral status/disconnect

**Files:**
- Modify: `extractor/src/main/java/net/shamansoft/cookbook/controller/StorageController.java`
- Modify test: `extractor/src/test/java/net/shamansoft/cookbook/controller/StorageControllerTest.java`

**Interfaces:**
- Consumes: `StorageService.connectDropbox(...)`, `getStorageInfo(...)`, `disconnectStorage(...)`.
- Produces HTTP endpoints: `POST /v1/storage/dropbox/connect` (201/400), `GET /v1/storage/status` (200), `DELETE /v1/storage/disconnect` (200). Existing `/v1/storage/google-drive/*` endpoints unchanged.

- [ ] **Step 1: Write the failing tests (extend `StorageControllerTest`)**

Add to `StorageControllerTest.java`:

```java
    @Test
    @DisplayName("POST /dropbox/connect - Success")
    void connectDropbox_Success() {
        StorageConnectionRequest request = StorageConnectionRequest.builder()
                .authorizationCode(TEST_AUTH_CODE)
                .redirectUri(TEST_REDIRECT_URI)
                .folderName(null)
                .build();
        when(storageService.connectDropbox(eq(TEST_USER_ID), eq(TEST_AUTH_CODE), eq(TEST_REDIRECT_URI), isNull()))
                .thenReturn(new StorageService.FolderInfo("", "MyKukBuk"));

        ResponseEntity<StorageConnectionResponse> response =
                controller.connectDropbox(TEST_USER_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getStatus()).isEqualTo("success");
        assertThat(response.getBody().getMessage()).isEqualTo("Dropbox connected successfully");
        assertThat(response.getBody().isConnected()).isTrue();
        assertThat(response.getBody().getDefaultFolderId()).isEqualTo("");
        assertThat(response.getBody().getDefaultFolderName()).isEqualTo("MyKukBuk");
    }

    @Test
    @DisplayName("POST /dropbox/connect - Invalid code returns 400")
    void connectDropbox_InvalidCode_Returns400() {
        StorageConnectionRequest request = StorageConnectionRequest.builder()
                .authorizationCode("bad")
                .redirectUri(TEST_REDIRECT_URI)
                .build();
        doThrow(new IllegalArgumentException("Invalid authorization code"))
                .when(storageService).connectDropbox(anyString(), anyString(), anyString(), any());

        ResponseEntity<StorageConnectionResponse> response =
                controller.connectDropbox(TEST_USER_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo("error");
    }

    @Test
    @DisplayName("GET /status - neutral status returns connected provider")
    void getStatus_Connected() {
        StorageInfo storageInfo = StorageInfo.builder()
                .type(StorageType.DROPBOX)
                .connected(true)
                .accessToken("t")
                .folderId("")
                .folderName("MyKukBuk")
                .build();
        when(storageService.getStorageInfo(TEST_USER_ID)).thenReturn(storageInfo);

        ResponseEntity<StorageStatusResponse> response = controller.getStatus(TEST_USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isConnected()).isTrue();
        assertThat(response.getBody().getStorageType()).isEqualTo("dropbox");
    }

    @Test
    @DisplayName("GET /status - not connected returns connected=false")
    void getStatus_NotConnected() {
        when(storageService.getStorageInfo(TEST_USER_ID))
                .thenThrow(new StorageNotConnectedException("none"));

        ResponseEntity<StorageStatusResponse> response = controller.getStatus(TEST_USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isConnected()).isFalse();
        assertThat(response.getBody().getStorageType()).isNull();
    }

    @Test
    @DisplayName("DELETE /disconnect - neutral disconnect")
    void disconnect_Neutral() {
        doNothing().when(storageService).disconnectStorage(TEST_USER_ID);

        ResponseEntity<StorageConnectionResponse> response = controller.disconnect(TEST_USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isConnected()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Storage disconnected successfully");
        verify(storageService).disconnectStorage(TEST_USER_ID);
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :cookbook:test --tests StorageControllerTest`
Expected: FAIL — compilation error: `controller.connectDropbox`, `controller.getStatus`, `controller.disconnect` do not exist.

- [ ] **Step 3: Add the three endpoints to `StorageController`**

In `StorageController.java`, add these methods inside the class (e.g. after `connectGoogleDrive`):

```java
    /**
     * Connect Dropbox storage for the authenticated user.
     * Mirrors the Google Drive connect flow; provider selection is server-side.
     */
    @PostMapping("/dropbox/connect")
    public ResponseEntity<StorageConnectionResponse> connectDropbox(
            @RequestAttribute("userId") String userId,
            @RequestBody @Valid StorageConnectionRequest request) {

        log.info("Connecting Dropbox storage for user: {}", userId);
        try {
            StorageService.FolderInfo folderInfo = storageService.connectDropbox(
                    userId,
                    request.getAuthorizationCode(),
                    request.getRedirectUri(),
                    request.getFolderName());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(StorageConnectionResponse.success(
                            "Dropbox connected successfully",
                            true,
                            folderInfo.folderId(),
                            folderInfo.folderName()));

        } catch (IllegalArgumentException e) {
            log.error("Invalid Dropbox authorization code for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(StorageConnectionResponse.error("Invalid authorization code: " + e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Dropbox OAuth configuration error for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(StorageConnectionResponse.error("OAuth error: " + e.getMessage()));
        }
    }

    /**
     * Provider-neutral status: returns whichever provider is currently connected.
     */
    @GetMapping("/status")
    public ResponseEntity<StorageStatusResponse> getStatus(
            @RequestAttribute("userId") String userId) {
        log.debug("Getting storage status for user: {}", userId);
        try {
            StorageInfo info = storageService.getStorageInfo(userId);
            return ResponseEntity.ok(StorageStatusResponse.fromStorageInfo(info));
        } catch (StorageNotConnectedException e) {
            log.debug("Storage not connected for user: {}", userId);
            return ResponseEntity.ok(StorageStatusResponse.notConnected());
        }
    }

    /**
     * Provider-neutral disconnect: clears whichever provider is connected.
     */
    @DeleteMapping("/disconnect")
    public ResponseEntity<StorageConnectionResponse> disconnect(
            @RequestAttribute("userId") String userId) {
        log.info("Disconnecting storage for user: {}", userId);
        storageService.disconnectStorage(userId);
        return ResponseEntity.ok(
                StorageConnectionResponse.success("Storage disconnected successfully", false));
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :cookbook:test --tests StorageControllerTest`
Expected: PASS — new Dropbox/neutral tests green; existing `/google-drive/*` tests unchanged and green.

- [ ] **Step 5: Commit**

```bash
git add extractor/src/main/java/net/shamansoft/cookbook/controller/StorageController.java \
        extractor/src/test/java/net/shamansoft/cookbook/controller/StorageControllerTest.java
git commit -m "feat(dropbox): add POST /v1/storage/dropbox/connect + neutral GET /status and DELETE /disconnect"
```

---

## Task 10: Full verification (build + coverage + native reflection sanity)

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :cookbook:test`
Expected: PASS — BUILD SUCCESSFUL, no failing tests.

- [ ] **Step 2: Run the integration-test suite (Docker required)**

Run: `./gradlew :cookbook:intTest`
Expected: PASS — BUILD SUCCESSFUL.

- [ ] **Step 3: Enforce the coverage gate**

Run: `./gradlew :cookbook:checkCoverage`
Expected: PASS — coverage ≥ 40% (the new provider/client/auth classes are unit-tested).

- [ ] **Step 4: Full module build**

Run: `./gradlew :cookbook:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Native reflection sanity note (no code change expected)**

The Dropbox clients parse JSON into `Map<String,Object>` via `ParameterizedTypeReference` (same pattern as `GoogleDrive`) and build request bodies from `Map`/`MultiValueMap`, so no new `reflect-config.json` entries are anticipated. This can only be confirmed by a full `nativeCompile` in CI/CD (not runnable on Apple Silicon locally). If a Cloud Run deploy later throws `MissingReflectionRegistrationError` for a Dropbox class, paste the exact JSON snippet from the error into `extractor/src/main/resources/META-INF/native-image/reflect-config.json` (per the "Adding Reflection Metadata" section of `CLAUDE.md`).

- [ ] **Step 6: Final commit (if any incidental changes)**

```bash
git add -A
git commit -m "chore(dropbox): finalize Dropbox storage backend (build + coverage green)" --allow-empty
```

---

## Self-Review

**1. Spec coverage** (against `research/dropbox-backend-contract.md`)

- §1.1 `POST /v1/storage/dropbox/connect` → Task 9 (+ Task 8 `connectDropbox`). Reuses `StorageConnectionRequest`/`StorageConnectionResponse`, returns 201/400. ✅
- §1.2 neutral `GET /v1/storage/status` → Task 9 `getStatus` (reuses `StorageStatusResponse.fromStorageInfo` / `.notConnected`). ✅
- §1.3 neutral `DELETE /v1/storage/disconnect` → Task 9 `disconnect`. ✅
- §1.4 back-compat Google endpoints unchanged → untouched in `StorageController`; verified by existing `StorageControllerTest` (Task 9 Step 4). ✅
- §3 provider abstraction (`StorageProvider` + resolver, `GoogleDriveStorageProvider`, `DropboxStorageProvider`) → Tasks 4-6; consumers routed via resolver → Task 7. ✅
- §3 `DropboxAuthClient` (exchange/refresh/isTokenExpired) + Dropbox token endpoint + `expires_in ~14400` → Task 2. ✅
- §3 `StorageEntity.toMap()` stops hardcoding `GOOGLE_DRIVE` → Task 1. ✅
- §3 App-folder access; `folderId=""` app root; path handling never fails on `""` → Task 6 (`rootPath`/`joinPath`/`normalizeSubfolder`), default `cookbook.dropbox.folder-name=""` (Task 2). ✅
- §3 store Dropbox `id:...` as fileId → Task 6 (upload returns `entry.id()`; get/download addressed by id). ✅
- §3 revoke token on disconnect (`/2/auth/token/revoke`) before clearing Firestore → Task 2 (`revokeToken`) + Task 8 (`revokeProviderTokenBestEffort`). ✅
- §3 Dropbox file API (upload/download/list_folder[/continue]/create_folder_v2 with 409 tolerance/get_metadata; `cursor`↔`pageToken`, `has_more`↔`nextPageToken`) → Task 3 (client) + Task 6 (provider mapping). ✅
- §3 config keys `cookbook.dropbox.{app-key,app-secret,folder-name}` + Secret Manager handoff note → Task 2. ✅
- Preserve encryption-at-rest + provider-aware auto-refresh → Task 8 (Steps 5-6 reuse `TokenEncryptionService`, branch by type). ✅
- Recipe/media endpoints unchanged behavior for Google → Task 7 (resolver keeps Google path identical; verified by existing RecipeService/controller tests). ✅

**2. Placeholder scan:** No `TODO`/`TBD`/"handle errors"/"similar to". Every code step contains complete, compilable code; every test step contains real assertions; the only literal `REPLACE_WITH_*` strings are intentional config placeholders (matching the existing `oauth-secret` convention) and are documented as such.

**3. Type consistency:** Method names on `StorageProvider` exactly match `DriveService` (`generateFileName`, `uploadRecipeYaml`, `listRecipeFiles`, `getFileContent`, `downloadFile`, `getFileMimeType`, `getFileMetadata`) — this is what lets the consumer tests keep `when(driveService.X)` stubs after retyping the mock to `StorageProvider`. `DropboxAuthClient.TokenResponse(accessToken, refreshToken, expiresIn)` / `RefreshTokenResponse(accessToken, expiresAt)` are used identically in Task 8. `StorageProvider.FolderRef(id, name)` returned by both providers and consumed by `connectDropbox`. `DropboxClient.FileEntry(id, name, pathDisplay, serverModified)` / `ListResult(files, cursor, hasMore)` are used consistently across Tasks 3 and 6. `StorageService` 6-arg constructor is updated in every construction site (unit test, int test).

No gaps found.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-19-dropbox-storage-backend.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**

- If Subagent-Driven: REQUIRED SUB-SKILL `superpowers:subagent-driven-development` (fresh subagent per task + two-stage review).
- If Inline: REQUIRED SUB-SKILL `superpowers:executing-plans` (batch execution with checkpoints).
