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
    
    private Instant createdAt;
    
    private Instant lastAccessedAt;
    
    private long accessCount;
    
    public Recipe incrementAccessCount() {
        return Recipe.builder()
                .contentHash(this.contentHash)
                .sourceUrl(this.sourceUrl)
                .recipeYaml(this.recipeYaml)
                .createdAt(this.createdAt)
                .lastAccessedAt(Instant.now())
                .accessCount(this.accessCount + 1)
                .build();
    }
}