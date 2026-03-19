package com.codeguard.service;

import com.codeguard.model.GitHubPullRequestEvent;
import com.codeguard.model.ReviewProfile;
import com.codeguard.model.ReviewRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubWebhookService {

    private final CodeReviewService codeReviewService;
    private final ObjectMapper objectMapper;

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    @Value("${codeguard.default-review-profile:GENERAL}")
    private String defaultProfile;

    /**
     * Validates the HMAC-SHA256 signature sent by GitHub.
     * GitHub signs the raw request body with the webhook secret.
     */
    public boolean isValidSignature(String rawPayload, String signatureHeader) {
        try {
            if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
                return false;
            }
            String receivedHex = signatureHeader.substring("sha256=".length());

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedBytes = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expectedBytes);

            return constantTimeEquals(receivedHex, expectedHex);
        } catch (Exception e) {
            log.error("Signature validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses GitHub PR webhook payload and kicks off async AI review.
     * Only triggers on 'opened' and 'synchronize' actions to avoid redundant reviews.
     */
    @Async
    public void processPullRequestEvent(String rawPayload) {
        try {
            GitHubPullRequestEvent event = objectMapper.readValue(rawPayload, GitHubPullRequestEvent.class);

            String action = event.getAction();
            log.info("PR event action: {} — PR#{} in {}", action,
                    event.getPullRequest().getNumber(),
                    event.getRepository().getFullName());

            if (!"opened".equals(action) && !"synchronize".equals(action)) {
                log.debug("Skipping non-review action: {}", action);
                return;
            }

            ReviewProfile profile = resolveProfile(event);
            ReviewRequest request = ReviewRequest.builder()
                    .owner(event.getRepository().getOwner().getLogin())
                    .repositoryName(event.getRepository().getName())
                    .prNumber(event.getPullRequest().getNumber())
                    .prTitle(event.getPullRequest().getTitle())
                    .prDescription(event.getPullRequest().getBody())
                    .diff(event.getPullRequest().getDiff())
                    .profile(profile)
                    .build();

            codeReviewService.performReview(request);

        } catch (Exception e) {
            log.error("Failed to process PR event: {}", e.getMessage(), e);
        }
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Resolves review profile from PR labels.
     * Labels 'security-review' or 'performance-review' override the default.
     */
    private ReviewProfile resolveProfile(GitHubPullRequestEvent event) {
        if (event.getPullRequest().getLabels() != null) {
            for (var label : event.getPullRequest().getLabels()) {
                if ("security-review".equalsIgnoreCase(label.getName())) return ReviewProfile.SECURITY;
                if ("performance-review".equalsIgnoreCase(label.getName())) return ReviewProfile.PERFORMANCE;
            }
        }
        try {
            return ReviewProfile.valueOf(defaultProfile.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ReviewProfile.GENERAL;
        }
    }

    /** Timing-safe string comparison to prevent timing attacks on HMAC validation. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
