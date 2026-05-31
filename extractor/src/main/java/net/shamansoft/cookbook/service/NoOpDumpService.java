package net.shamansoft.cookbook.service;

import net.shamansoft.recipe.model.Recipe;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local")
public class NoOpDumpService implements DumpService {

    @Override
    public String dump(String content, String prefix, String extension, String sessionId) {
        return null;
    }

    @Override
    public String dumpRecipeJson(Recipe recipe, String sessionId) {
        return null;
    }

    @Override
    public String dumpRecipeYaml(String yaml, String sessionId) {
        return null;
    }
}
