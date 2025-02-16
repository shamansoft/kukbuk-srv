package net.shamansoft.cookbook;

import lombok.RequiredArgsConstructor;
import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.web.bind.annotation.*;

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
    public RecipeResponse createRecipe(@RequestBody Request request) {
        try {
            String fetched = rawContentService.fetch(request.url());
            String transformed = transformer.transform(fetched);
            return RecipeResponse.builder()
                    .url(request.url())
                    .raw(fetched)
                    .content(transformed)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
