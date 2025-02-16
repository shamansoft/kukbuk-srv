package net.shamansoft.cookbook;

import lombok.RequiredArgsConstructor;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class CookbookController {

    private final RawContentService rawContentService;
    private final Transformer transformer;

    @GetMapping("/hello/{name}")
    public String index(@PathVariable("name") String name) {
        return "Hello, Cookbook user %s!".formatted(name);
    }

    @PostMapping(
            path = "/recipe",
            consumes = "application/json",
            produces = "application/json"
    )
    public RecipeResponse createRecipe(@RequestBody Request request) throws IOException {
        String fetched = rawContentService.fetch(request.url());
        String transformed = transformer.transform(fetched);
        return RecipeResponse.builder()
                .url(request.url())
                .raw(fetched)
                .content(transformed)
                .build();
    }

    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    @ExceptionHandler(IOException.class)
    public void handleIOException() {}

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public void handleGeneralException() {}
    }
}
