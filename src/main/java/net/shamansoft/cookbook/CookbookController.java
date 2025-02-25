package net.shamansoft.cookbook;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
                                       @RequestParam(value = "debug", required = false) boolean debug) throws IOException {

        var html = "";
        if(request.html() != null) {
            try {
                html = compressor.decompress(request.html());
            } catch (IOException e) {
                rawContentService.fetch(request.url());
            }
        } else {
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
