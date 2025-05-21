package net.shamansoft.cookbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ResourcesLoader {

    private final org.springframework.core.io.ResourceLoader resourceLoader;

    public String loadYaml(String resourceName) throws IOException {
        String content = resourceLoader.getResource(resourceName)
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return content;
    }
}
