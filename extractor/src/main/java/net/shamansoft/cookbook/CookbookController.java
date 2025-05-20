package net.shamansoft.cookbook;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.ErrorResponse;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "chrome-extension://*",
//        allowedHeaders = {"Content-Type", "Authorization", "X-Extension-ID", "X-Request-ID", "Accept"},
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
                                Map<String,String> headers) throws IOException {
        return createRecipe(request, compression,
                new HttpHeaders(new HttpHeaders(MultiValueMap.fromSingleValue(headers))));
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
            throws IOException {

        log.debug("Headers: {}", httpHeaders.toString());

        String html = extractHtml(request, compression);
        String transformed = transformer.transform(html);
        RecipeResponse.RecipeResponseBuilder responseBuilder = RecipeResponse.builder()
                .title(request.title())
                .url(request.url());
        // Google Drive integration: if auth-token header is present, persist the recipe YAML
        storeToDrive(request, httpHeaders, transformed, responseBuilder);
        return responseBuilder.build();
    }

    private void storeToDrive(Request request, HttpHeaders httpHeaders, String transformed, RecipeResponse.RecipeResponseBuilder responseBuilder) {
        String authToken = httpHeaders.getFirst("X-S-AUTH-TOKEN");
        if (authToken != null && !authToken.isBlank()) {
            // Validate token
            if (!tokenService.verifyToken(authToken)) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid auth token");
            }
            // Ensure kukbuk folder exists
            String folderId = googleDriveService.getOrCreateFolder(authToken);
            // Generate filename and upload content
            String fileName = googleDriveService.generateFileName(request.title());
            DriveService.UploadResult uploadResult = googleDriveService.uploadRecipeYaml(authToken, folderId, fileName, transformed);
            responseBuilder.driveFileId(uploadResult.fileId())
                    .driveFileUrl(uploadResult.fileUrl());
        }
    }

    private String extractHtml(Request request, String compression) throws IOException {
        String html = "";
        // Try to use the HTML from the request first
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
                
                log.info("Falling back to fetching HTML from URL: {}", request.url());
                throw e;
            }
        } else {
            // Fallback to fetching from the URL
            log.debug("No HTML in request, fetching from URL: {}", request.url());
            html = rawContentService.fetch(request.url());
        }
        return html;
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Object> handleIOException(IOException e, HttpServletRequest request) {
        log.error("IO Exception occurred: {}", e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "IO Error",
                e.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.error("Validation error: {}", ex.getMessage());
        
        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.ValidationError(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());
                
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                "Request validation failed",
                request.getRequestURI()
        );
        errorResponse.setValidationErrors(validationErrors);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception e, HttpServletRequest request) {
        log.error("Unexpected error occurred: {}", e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
