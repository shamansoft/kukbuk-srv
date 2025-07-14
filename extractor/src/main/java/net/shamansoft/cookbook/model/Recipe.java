package net.shamansoft.cookbook.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Data
@Builder
@Jacksonized
public class Recipe {
    
    private String contentHash;
    
    private String sourceUrl;
    
    private String recipeYaml;

    private boolean isValid;
    
    private Instant createdAt;
    
    private Instant lastUpdatedAt;
    
    private long version;
    
    public Recipe withUpdatedVersion() {
        return Recipe.builder()
                .contentHash(this.contentHash)
                .sourceUrl(this.sourceUrl)
                .recipeYaml(this.recipeYaml)
                .createdAt(this.createdAt)
                .lastUpdatedAt(Instant.now())
                .version(this.version + 1)
                .isValid(this.isValid)
                .build();
    }
}