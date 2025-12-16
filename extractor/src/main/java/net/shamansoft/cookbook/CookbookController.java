package net.shamansoft.cookbook;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.dto.StorageInfo;
import net.shamansoft.cookbook.exception.StorageNotConnectedException;
import net.shamansoft.cookbook.model.StoredRecipe;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.HtmlExtractor;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.StorageService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import net.shamansoft.cookbook.service.UserProfileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "chrome-extension://*",
        allowedHeaders = "*",
        exposedHeaders = "*",
        allowCredentials = "false")
public class CookbookController {

    private final HtmlExtractor htmlExtractor;
    private final Transformer transformer;
    private final DriveService googleDriveService;
    private final TokenService tokenService;
    private final ContentHashService contentHashService;
    private final RecipeStoreService recipeStoreService;
    private final UserProfileService userProfileService;
    private final StorageService storageService;

    @GetMapping("/")
    public String gcpHealth() {
        return "OK";
    }

    @GetMapping("/hello/{name}")
    public String index(@PathVariable("name") String name) {
        return "Hello, Cookbook user %s!".formatted(name);
    }

    /**
     * Get current user's profile (basic info from Firebase token plus storage status)
     */
    @GetMapping("/api/user/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("userEmail") String userEmail) {

        log.info("Getting profile for user: {}", userId);

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", userId);
        profile.put("email", userEmail);

        // Add storage status (without exposing sensitive tokens)
        try {
            boolean hasStorage = storageService.isStorageConnected(userId);
            profile.put("hasStorageConfigured", hasStorage);

            if (hasStorage) {
                StorageInfo storage = storageService.getStorageInfo(userId);
                profile.put("storageType", storage.getType().getFirestoreValue());
                profile.put("storageConnectedAt", storage.getConnectedAt());
                // Intentionally NOT including access/refresh tokens for security
            }
        } catch (Exception e) {
            log.debug("No storage configured for user {}: {}", userId, e.getMessage());
            profile.put("hasStorageConfigured", false);
        }

        return ResponseEntity.ok(profile);
    }

    RecipeResponse createRecipe(Request request,
                                String compression,
                                boolean test,
                                Map<String, String> headers) throws IOException, AuthenticationException {
        return createRecipe(request, compression, "test-user", "test@example.com", new HttpHeaders(HttpHeaders.readOnlyHttpHeaders(new HttpHeaders(MultiValueMap.fromSingleValue(headers)))));
    }

    /**
     * Save recipe endpoint
     * <p>
     * Authentication flow:
     * 1. Firebase ID token required in Authorization header (validates user)
     * 2. Backend retrieves storage OAuth tokens from Firestore (encrypted)
     * 3. Backend uses storage provider to save recipe
     * <p>
     * No OAuth tokens in headers - all managed server-side!
     */
    @PostMapping(
            path = "/recipe",
            consumes = "application/json",
            produces = "application/json"
    )
    public RecipeResponse createRecipe(@RequestBody @Valid Request request,
                                       @RequestParam(value = "compression", required = false) String compression,
                                       @RequestAttribute("userId") String userId,
                                       @RequestAttribute("userEmail") String userEmail,
                                       @RequestHeader HttpHeaders httpHeaders
    )
            throws IOException, AuthenticationException {

        log.info("Creating recipe for user: {} ({})", userEmail, userId);
        log.info("Processing recipe request - URL: {}, Title: {}, Has HTML: {}, Compression: {}",
                request.url(),
                request.title(),
                request.html() != null && !request.html().isEmpty(),
                compression != null ? compression : "default");

        // Get OAuth token from StorageService (NEW flow)
        String googleOAuthToken;
        try {
            StorageInfo storage = storageService.getStorageInfo(userId);
            googleOAuthToken = storage.getAccessToken();
            log.debug("Using OAuth token from StorageService");
        } catch (StorageNotConnectedException e) {
            log.warn("No storage configured for user: {}", userEmail);
            // Exception handler will return HTTP 428 with proper error message
            throw e;
        } catch (Exception e) {
            log.error("Failed to get storage info for user {}: {}", userEmail, e.getMessage());
            throw new IOException("Failed to retrieve storage credentials: " + e.getMessage(), e);
        }
        // get hash
        String contentHash = contentHashService.generateContentHash(request.url());
        // retrieve from store
        Optional<StoredRecipe> stored = Optional.empty();
        if (contentHash != null) {
            stored = recipeStoreService.findStoredRecipeByHash(contentHash);
        }

        Transformer.Response response;
        if (stored.isEmpty()) {
            String html = htmlExtractor.extractHtml(request, compression);
            log.info("Extracted HTML - URL: {}, HTML length: {} chars, Content hash: {}",
                    request.url(),
                    html.length(),
                    contentHash);
            response = transformer.transform(html);
            if (contentHash != null) {
                if (response.isRecipe()) {
                    recipeStoreService.storeValidRecipe(contentHash, request.url(), response.value());
                } else {
                    log.warn("Gemini determined content is NOT a recipe - URL: {}, Hash: {}", request.url(), contentHash);
                    recipeStoreService.storeInvalidRecipe(contentHash, request.url());
                }
            }
        } else {
            StoredRecipe storedRecipe = stored.get();
            response = new Transformer.Response(storedRecipe.isValid(), storedRecipe.getRecipeYaml());
        }

        RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder()
                .title(request.title())
                .url(request.url())
                .isRecipe(response.isRecipe());
        if (response.isRecipe()) {
            storeToDrive(request, googleOAuthToken, response.value(), responseBuilder);
        } else {
            log.info("Content is not a recipe. Skipping Drive storage - URL: {}", request.url());
        }

        return responseBuilder.build();
    }

    private void storeToDrive(Request request, String authToken, String transformed,
                              RecipeResponse.RecipeResponseBuilder responseBuilder) {
        String folderId = googleDriveService.getOrCreateFolder(authToken);
        String fileName = googleDriveService.generateFileName(request.title());
        DriveService.UploadResult uploadResult = googleDriveService.uploadRecipeYaml(
                authToken, folderId, fileName, transformed);
        responseBuilder.driveFileId(uploadResult.fileId())
                .driveFileUrl(uploadResult.fileUrl());
    }


}