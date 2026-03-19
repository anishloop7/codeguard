package com.codeguard.service;

import com.codeguard.model.ReviewProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class PromptEngineService {

    private static final int MAX_DIFF_CHARS = 12_000; // ~3000 tokens budget for diff

    /**
     * Builds a structured system prompt based on the selected ReviewProfile.
     * Each profile injects different instructions and output schema expectations.
     */
    public String buildSystemPrompt(ReviewProfile profile) {
        String baseInstructions = loadPromptTemplate("prompts/base-system.txt");
        String profileInstructions = switch (profile) {
            case SECURITY    -> loadPromptTemplate("prompts/security-review.txt");
            case PERFORMANCE -> loadPromptTemplate("prompts/performance-review.txt");
            default          -> loadPromptTemplate("prompts/general-review.txt");
        };

        return baseInstructions + "\n\n" + profileInstructions;
    }

    /**
     * Builds the contextual user prompt with PR metadata and sanitized diff.
     * Truncates diff if it exceeds token budget to avoid context overflow.
     */
    public String buildUserPrompt(String diff, String prTitle, String prDescription) {
        String sanitizedDiff = sanitizeDiff(diff);

        return """
                ## Pull Request Information
                **Title:** %s
                
                **Description:**
                %s
                
                ## Code Diff
                ```diff
                %s
                ```
                
                Analyze the above diff and return your review in the specified JSON format.
                """.formatted(
                    prTitle != null ? prTitle : "N/A",
                    prDescription != null ? prDescription : "No description provided.",
                    sanitizedDiff
                );
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    private String sanitizeDiff(String diff) {
        if (diff == null || diff.isBlank()) {
            return "// No diff content provided";
        }
        if (diff.length() > MAX_DIFF_CHARS) {
            log.warn("Diff truncated from {} chars to {} chars to fit token budget",
                    diff.length(), MAX_DIFF_CHARS);
            return diff.substring(0, MAX_DIFF_CHARS) + "\n\n// [DIFF TRUNCATED — exceeds token budget]";
        }
        return diff;
    }

    private String loadPromptTemplate(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", resourcePath);
            return "";
        }
    }
}
