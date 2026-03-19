package com.codeguard.dto;

import com.codeguard.model.ReviewProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewResultDTO {

    private int prNumber;
    private String repositoryName;
    private ReviewProfile profile;

    /** High-level summary of the review. */
    private String summary;

    /** Overall quality score out of 10, assigned by the model. */
    private int qualityScore;

    /** Structured list of issues found in the diff. */
    private List<CodeIssue> issues;

    /** Suggestions that are positive or informational. */
    private List<String> suggestions;

    @Builder.Default
    private Instant reviewedAt = Instant.now();

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeIssue {

        /** CRITICAL, HIGH, MEDIUM, LOW */
        private String severity;

        /** File path within the repository. */
        private String filePath;

        /** Line number in the diff. */
        private int lineNumber;

        /** Short title of the issue. */
        private String title;

        /** Detailed explanation of the problem. */
        private String description;

        /** Suggested fix or remediation. */
        private String suggestion;
    }
}
