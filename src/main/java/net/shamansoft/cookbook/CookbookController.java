package net.shamansoft.cookbook;

import lombok.RequiredArgsConstructor;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
public class CookbookController {

    private final RawContentService rawContentService;
    private final Transformer transformer;

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
                                       @RequestParam(value = "debug", required = false) boolean debug) {
        String transformed = transformer.transform(request.html());
        RecipeResponse.RecipeResponseBuilder content = RecipeResponse.builder()
                .title(request.title())
                .url(request.url())
                .content(transformed);
        if (debug) {
            content.raw(request.html());
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
