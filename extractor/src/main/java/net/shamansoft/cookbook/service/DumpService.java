package net.shamansoft.cookbook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.shamansoft.recipe.model.Recipe;

public interface DumpService {
    String dump(String content, String prefix, String extension, String sessionId);
    String dumpRecipeJson(Recipe recipe, String sessionId) throws JsonProcessingException;
    String dumpRecipeYaml(String yaml, String sessionId);
}
