package com.codeguard.controller;

import com.codeguard.dto.PullRequestDTO;
import com.codeguard.service.GitHubWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final GitHubWebhookService webhookService;

    /**
     * Entry point for GitHub webhook events.
     * Validates the HMAC-SHA256 signature before processing.
     */
    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> handleGitHubWebhook(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String rawPayload) {

        log.info("Received GitHub webhook event: {}", eventType);

        if (!webhookService.isValidSignature(rawPayload, signature)) {
            log.warn("Invalid webhook signature received — rejecting request");
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        if ("pull_request".equals(eventType)) {
            webhookService.processPullRequestEvent(rawPayload);
            return ResponseEntity.ok(Map.of("status", "PR event accepted for review"));
        }

        return ResponseEntity.ok(Map.of("status", "Event type not handled: " + eventType));
    }

    /**
     * Health check for webhook endpoint.
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "CodeGuard webhook listener is active"));
    }
}
