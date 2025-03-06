package net.shamansoft.cookbook;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "chrome-extension://*",
//        allowedHeaders = {"Content-Type", "Authorization", "X-Extension-ID", "X-Request-ID", "Accept"},
        allowedHeaders = "*",
        exposedHeaders = "*",
        allowCredentials = "false")
public class CookbookController {

    private final RawContentService rawContentService;
    private final Transformer transformer;
    private final Compressor compressor;

    @GetMapping("/")
    public String gcpHealth() {
        return "OK";
    }

    @GetMapping("/hello/{name}")
    public String index(@PathVariable("name") String name) {
        return "Hello, Cookbook user %s!".formatted(name);
    }

    @PostMapping(
            path = "/recipe",
            consumes = "application/json",
            produces = "application/json"
    )
    public RecipeResponse createRecipe(@RequestBody @Valid Request request,
                                       @RequestParam(value = "compression", required = false) String compression,
                                       @RequestParam(value = "debug", required = false) boolean debug)
            throws IOException {

        String html = "";
        // Try to use the HTML from the request first
        if (request.html() != null && !request.html().isEmpty()) {
            try {
                if("none".equals(compression)) {
                    html = request.html();
                    log.debug("Skipping decompression, using HTML from request");
                } else {
                    html = compressor.decompress(request.html());
                    log.debug("Successfully decompressed HTML from request");
                }
            } catch (IOException e) {
                log.warn("Failed to decompress HTML from request: {}", e.getMessage());
                log.debug("Falling back to fetching HTML from URL");
                throw e;
            }
        } else {
            // Fallback to fetching from the URL
            log.debug("No HTML in request, fetching from URL: {}", request.url());
            html = rawContentService.fetch(request.url());
        }
        String transformed = transformer.transform(html);
        RecipeResponse.RecipeResponseBuilder content = RecipeResponse.builder()
                .title(request.title())
                .url(request.url())
                .content(transformed);
        if (debug) {
            content.raw(html);
        }
        return content.build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body("Validation error: " + ex.getBindingResult().getFieldError().getDefaultMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public void handleGeneralException() {
    }
}
