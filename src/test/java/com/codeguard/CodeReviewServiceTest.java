package com.codeguard;

import com.codeguard.dto.ReviewResultDTO;
import com.codeguard.model.ReviewProfile;
import com.codeguard.model.ReviewRequest;
import com.codeguard.service.CodeReviewService;
import com.codeguard.service.PromptEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeReviewServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private AssistantMessage assistantMessage;

    @Mock
    private PromptEngineService promptEngineService;

    @InjectMocks
    private CodeReviewService codeReviewService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // inject ObjectMapper manually since @InjectMocks doesn't pick it up
        codeReviewService = new CodeReviewService(chatClient, promptEngineService, objectMapper);
    }

    @Test
    @DisplayName("Should return structured review result on successful API response")
    void shouldReturnStructuredReviewResult() throws Exception {
        // Given
        String mockJsonResponse = """
            {
              "summary": "The code introduces a potential SQL injection risk.",
              "qualityScore": 5,
              "issues": [
                {
                  "severity": "HIGH",
                  "filePath": "src/main/java/UserService.java",
                  "lineNumber": 34,
                  "title": "SQL Injection via string concatenation",
                  "description": "User input is directly concatenated into a SQL query.",
                  "suggestion": "Use parameterized queries or JPA repository methods."
                }
              ],
              "suggestions": ["Consider adding input validation at the controller layer."]
            }
            """;

        when(promptEngineService.buildSystemPrompt(any())).thenReturn("system prompt");
        when(promptEngineService.buildUserPrompt(any(), any(), any())).thenReturn("user prompt");
        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(mockJsonResponse);

        ReviewRequest request = ReviewRequest.builder()
                .owner("testuser")
                .repositoryName("test-repo")
                .prNumber(42)
                .prTitle("Fix user authentication")
                .prDescription("Fixes login bug")
                .diff("+ String query = \"SELECT * FROM users WHERE id=\" + userId;")
                .profile(ReviewProfile.SECURITY)
                .build();

        // When
        ReviewResultDTO result = codeReviewService.performReview(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getQualityScore()).isEqualTo(5);
        assertThat(result.getIssues()).hasSize(1);
        assertThat(result.getIssues().get(0).getSeverity()).isEqualTo("HIGH");
        assertThat(result.getIssues().get(0).getFilePath()).isEqualTo("src/main/java/UserService.java");
    }

    @Test
    @DisplayName("Should return fallback response on API failure")
    void shouldReturnFallbackOnApiFailure() {
        // Given
        when(promptEngineService.buildSystemPrompt(any())).thenReturn("system prompt");
        when(promptEngineService.buildUserPrompt(any(), any(), any())).thenReturn("user prompt");
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("OpenAI API timeout"));

        ReviewRequest request = ReviewRequest.builder()
                .owner("testuser")
                .repositoryName("test-repo")
                .prNumber(99)
                .prTitle("Test PR")
                .diff("+ int x = 1;")
                .profile(ReviewProfile.GENERAL)
                .build();

        // When
        ReviewResultDTO result = codeReviewService.performReview(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSummary()).contains("Review could not be completed");
        assertThat(result.getPrNumber()).isEqualTo(99);
    }

    @Test
    @DisplayName("Should cache review result and return it on subsequent fetch")
    void shouldCacheAndReturnReviewResult() throws Exception {
        // Given
        String mockJson = """
            {
              "summary": "Looks good overall.",
              "qualityScore": 9,
              "issues": [],
              "suggestions": ["Nice use of Optional."]
            }
            """;

        when(promptEngineService.buildSystemPrompt(any())).thenReturn("system");
        when(promptEngineService.buildUserPrompt(any(), any(), any())).thenReturn("user");
        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn(mockJson);

        ReviewRequest request = ReviewRequest.builder()
                .owner("alice")
                .repositoryName("my-api")
                .prNumber(7)
                .prTitle("Refactor service layer")
                .diff("- oldCode\n+ newCode")
                .profile(ReviewProfile.PERFORMANCE)
                .build();

        // When
        codeReviewService.performReview(request);
        ReviewResultDTO cached = codeReviewService.getLatestReview("alice", "my-api", 7);

        // Then
        assertThat(cached).isNotNull();
        assertThat(cached.getQualityScore()).isEqualTo(9);
        assertThat(cached.getIssues()).isEmpty();
    }
}
