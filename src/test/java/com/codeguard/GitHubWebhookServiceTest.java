package com.codeguard;

import com.codeguard.service.CodeReviewService;
import com.codeguard.service.GitHubWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GitHubWebhookServiceTest {

    @Mock
    private CodeReviewService codeReviewService;

    private GitHubWebhookService webhookService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String testSecret = "test-webhook-secret";

    @BeforeEach
    void setUp() {
        webhookService = new GitHubWebhookService(codeReviewService, objectMapper);
        ReflectionTestUtils.setField(webhookService, "webhookSecret", testSecret);
        ReflectionTestUtils.setField(webhookService, "defaultProfile", "GENERAL");
    }

    @Test
    @DisplayName("Should accept valid HMAC-SHA256 signature")
    void shouldAcceptValidSignature() throws Exception {
        String payload = "{\"action\":\"opened\"}";

        // Pre-computed HMAC-SHA256 of payload with testSecret
        // sha256("test-webhook-secret", "{\"action\":\"opened\"}") = known value
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                testSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hex = java.util.HexFormat.of().formatHex(bytes);
        String validSignature = "sha256=" + hex;

        assertThat(webhookService.isValidSignature(payload, validSignature)).isTrue();
    }

    @Test
    @DisplayName("Should reject tampered payload with mismatched signature")
    void shouldRejectTamperedPayload() {
        String originalPayload = "{\"action\":\"opened\"}";
        String tamperedPayload = "{\"action\":\"closed\"}";
        String signatureForOriginal = "sha256=somehashfororiginal";

        assertThat(webhookService.isValidSignature(tamperedPayload, signatureForOriginal)).isFalse();
    }

    @Test
    @DisplayName("Should reject signature without sha256= prefix")
    void shouldRejectMalformedSignature() {
        assertThat(webhookService.isValidSignature("{}", "invalidheader")).isFalse();
    }

    @Test
    @DisplayName("Should reject null signature header")
    void shouldRejectNullSignature() {
        assertThat(webhookService.isValidSignature("{}", null)).isFalse();
    }
}
