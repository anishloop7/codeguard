package com.codeguard.service;

import com.codeguard.dto.ReviewResultDTO;
import com.codeguard.model.ReviewProfile;
import com.codeguard.model.ReviewRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewService {

    private final ChatClient chatClient;
    private final PromptEngineService promptEngineService;
    private final ObjectMapper objectMapper;

    // In-memory store for review results (keyed by "owner/repo/prNumber")
    private final Map<String, ReviewResultDTO> reviewCache = new ConcurrentHashMap<>();

    /**
     * Core review pipeline:
     * 1. Build structured system prompt based on ReviewProfile
     * 2. Build contextual user prompt with diff content
     * 3. Call OpenAI with token-aware chunking
     * 4. Parse structured JSON response
     * 5. Cache and return result
     */
    public ReviewResultDTO performReview(ReviewRequest request) {
        log.info("Starting review pipeline for PR#{} — profile: {}",
                request.getPrNumber(), request.getProfile());

        try {
            String systemPrompt = promptEngineService.buildSystemPrompt(request.getProfile());
            String userPrompt   = promptEngineService.buildUserPrompt(
                    request.getDiff(),
                    request.getPrTitle(),
                    request.getPrDescription()
            );

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));

            log.debug("Sending prompt to OpenAI — estimated tokens: {}",
                    estimateTokenCount(systemPrompt + userPrompt));

            String rawResponse = chatClient.call(prompt).getResult().getOutput().getContent();

            ReviewResultDTO result = parseStructuredResponse(rawResponse, request);
            cacheResult(request, result);

            log.info("Review completed for PR#{} — {} issues found",
                    request.getPrNumber(), result.getIssues().size());
            return result;

        } catch (Exception e) {
            log.error("Review pipeline failed for PR#{}: {}", request.getPrNumber(), e.getMessage());
            return buildFallbackResponse(request, e.getMessage());
        }
    }

    public ReviewResultDTO getLatestReview(String owner, String repo, int prNumber) {
        String key = buildCacheKey(owner, repo, prNumber);
        return reviewCache.get(key);
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    private ReviewResultDTO parseStructuredResponse(String rawResponse, ReviewRequest request) {
        try {
            // Strip markdown code fences if model wraps response
            String cleaned = rawResponse
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("```", "")
                    .trim();
            return objectMapper.readValue(cleaned, ReviewResultDTO.class);
        } catch (Exception e) {
            log.warn("JSON parse failed, building fallback result: {}", e.getMessage());
            return ReviewResultDTO.builder()
                    .prNumber(request.getPrNumber())
                    .repositoryName(request.getRepositoryName())
                    .profile(request.getProfile())
                    .summary(rawResponse)
                    .build();
        }
    }

    private ReviewResultDTO buildFallbackResponse(ReviewRequest request, String errorMsg) {
        return ReviewResultDTO.builder()
                .prNumber(request.getPrNumber())
                .repositoryName(request.getRepositoryName())
                .profile(request.getProfile())
                .summary("Review could not be completed: " + errorMsg)
                .build();
    }

    private void cacheResult(ReviewRequest request, ReviewResultDTO result) {
        String key = buildCacheKey(request.getOwner(), request.getRepositoryName(), request.getPrNumber());
        reviewCache.put(key, result);
    }

    private String buildCacheKey(String owner, String repo, int prNumber) {
        return owner + "/" + repo + "/" + prNumber;
    }

    /**
     * Rough token estimation (1 token ≈ 4 characters for English text).
     * Used for logging and pre-flight checks before hitting rate limits.
     */
    private int estimateTokenCount(String text) {
        return text.length() / 4;
    }
}
