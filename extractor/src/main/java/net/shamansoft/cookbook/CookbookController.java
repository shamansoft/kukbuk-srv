package net.shamansoft.cookbook;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.model.Recipe;
import java.util.Optional;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.ContentHashService;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.RecipeStoreService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "chrome-extension://*",
        allowedHeaders = "*",
        exposedHeaders = "*",
        allowCredentials = "false")
public class CookbookController {

    public static final String NONE = "none";
    private final RawContentService rawContentService;
    private final Transformer transformer;
    private final Compressor compressor;
    private final DriveService googleDriveService;
    private final TokenService tokenService;
    private final ContentHashService contentHashService;
    private final RecipeStoreService recipeStoreService;

    @GetMapping("/")
    public String gcpHealth() {
        return "OK";
    }

    @GetMapping("/hello/{name}")
    public String index(@PathVariable("name") String name) {
        return "Hello, Cookbook user %s!".formatted(name);
    }

    RecipeResponse createRecipe(Request request,
                                String compression,
                                boolean test,
                                Map<String, String> headers) throws IOException, AuthenticationException {
        return createRecipe(request, compression, new HttpHeaders(HttpHeaders.readOnlyHttpHeaders(new HttpHeaders(MultiValueMap.fromSingleValue(headers)))));
    }

    @PostMapping(
            path = "/recipe",
            consumes = "application/json",
            produces = "application/json"
    )
    public RecipeResponse createRecipe(@RequestBody @Valid Request request,
                                       @RequestParam(value = "compression", required = false) String compression,
                                       @RequestHeader HttpHeaders httpHeaders
    )
            throws IOException, AuthenticationException {

        log.debug("Headers: {}", httpHeaders);
        // authenticate user - will throw exception if token is invalid
        String authToken = tokenService.getAuthToken(httpHeaders);
        // get hash
        String contentHash = contentHashService.generateContentHash(request.url());
        // retrieve from store
        Optional<Recipe> stored = Optional.empty();
        if (contentHash != null) {
            stored = recipeStoreService.findStoredRecipeByHash(contentHash);
        }
        
        Transformer.Response response;
        if (stored.isEmpty()) {
            String html = extractHtml(request, compression);
            response = transformer.transform(html);
            if (contentHash != null) {
                if(response.isRecipe()) {
                    recipeStoreService.storeValidRecipe(contentHash, request.url(), response.value());
                } else {
                    log.info("The content is not a recipe. Skipping storage.");
                    recipeStoreService.storeInvalidRecipe(contentHash, request.url());
                }
            }
        } else {
            Recipe storedRecipe = stored.get();
            response = new Transformer.Response(storedRecipe.isValid(), storedRecipe.getRecipeYaml());
        }

        RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder()
                .title(request.title())
                .url(request.url())
                .isRecipe(response.isRecipe());
        if (response.isRecipe()) {
            storeToDrive(request, authToken, response.value(), responseBuilder);
        } else {
            log.info("The content is not a recipe. Skipping Drive storage.");
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

    private String extractHtml(Request request, String compression) throws IOException {
        String html = "";
        if (request.html() != null && !request.html().isEmpty()) {
            try {
                if (NONE.equals(compression)) {
                    html = request.html();
                    log.debug("Skipping decompression, using HTML from request");
                } else {
                    html = compressor.decompress(request.html());
                    log.debug("Successfully decompressed HTML from request");
                }
            } catch (IOException e) {
                log.warn("Failed to decompress HTML from request: {}", e.getMessage(), e);
                if (request.url() == null || request.url().isEmpty()) {
                    log.error("Cannot fall back to URL as it's not provided or empty");
                    throw new IOException("Failed to decompress HTML and no valid URL provided as fallback", e);
                }
                throw e;
            }
        } else {
            log.debug("No HTML in request, fetching from URL: {}", request.url());
            html = rawContentService.fetch(request.url());
        }
        return html;
    }
}