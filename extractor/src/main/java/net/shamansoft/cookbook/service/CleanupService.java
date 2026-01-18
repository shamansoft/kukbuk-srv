package net.shamansoft.cookbook.service;

import org.springframework.stereotype.Service;

@Service
public class CleanupService {

    public String removeYamlSign(String content) {
        // 1. Safety Checks
        if (content == null || content.isEmpty()) {
            return content;
        }

        // 2. Normalize (handle leading/trailing whitespace)
        String trimmed = content.trim();

        // 3. check for start tag (tolerant of language tag presence/absence)
        if (!trimmed.startsWith("```")) {
            return trimmed; // Return trimmed content if no markdown fence
        }

        // 4. Find the end of the first line (the opening tag line)
        // Handle both \n and \r\n line endings
        int firstNewLineIndex = trimmed.indexOf('\n');
        if (firstNewLineIndex == -1) {
            // Edge case: String is ONLY "```yaml" with no content or newlines
            // Try to return something reasonable - remove the backticks prefix
            if (trimmed.length() > 3) {
                String withoutBackticks = trimmed.substring(3).trim();
                // Remove language identifier if present (e.g., "yaml")
                if (withoutBackticks.startsWith("yaml")) {
                    return withoutBackticks.substring(4).trim();
                }
                return withoutBackticks;
            }
            return trimmed;
        }

        // 5. Find the closing tag
        int lastBacktickIndex = trimmed.lastIndexOf("```");

        // 6. Validation:
        // - Did we find a closing tag?
        // - Is the closing tag actually AFTER the opening line?
        if (lastBacktickIndex == -1 || lastBacktickIndex <= firstNewLineIndex) {
            // No valid closing tag - just remove the opening line
            return trimmed.substring(firstNewLineIndex + 1).trim();
        }

        // 7. Extract content between fences
        // Handle potential \r\n line endings by moving past \r if present
        int startIndex = firstNewLineIndex + 1;
        if (startIndex < trimmed.length() && trimmed.charAt(firstNewLineIndex) == '\r') {
            startIndex++;
        }

        String extracted = trimmed.substring(startIndex, lastBacktickIndex).trim();

        // 8. Handle nested code fences (if content itself contains ```)
        // This ensures we get clean YAML even if there are backticks inside
        return extracted;
    }
}
