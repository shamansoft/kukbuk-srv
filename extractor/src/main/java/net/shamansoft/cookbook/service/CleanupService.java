package net.shamansoft.cookbook.service;

import org.springframework.stereotype.Service;

@Service
public class CleanupService {

    public String removeYamlSign(String content) {
        if (content.startsWith("```yaml")) {
            return content.substring(content.indexOf("\n") + 1, content.lastIndexOf("```"));
        } else {
            return content;
        }
    }
}
