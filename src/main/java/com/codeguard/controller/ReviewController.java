package com.codeguard.controller;

import com.codeguard.dto.ReviewResultDTO;
import com.codeguard.model.ReviewRequest;
import com.codeguard.model.ReviewProfile;
import com.codeguard.service.CodeReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final CodeReviewService codeReviewService;

    /**
     * Manually trigger a code review for a given PR diff.
     * Supports profile-based reviews: SECURITY, PERFORMANCE, GENERAL
     */
    @PostMapping("/analyze")
    public ResponseEntity<ReviewResultDTO> analyzeCode(@RequestBody ReviewRequest request) {
        log.info("Manual review triggered — profile: {}, repo: {}",
                request.getProfile(), request.getRepositoryName());

        ReviewResultDTO result = codeReviewService.performReview(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Get the latest review result for a specific PR.
     */
    @GetMapping("/{owner}/{repo}/pr/{prNumber}")
    public ResponseEntity<ReviewResultDTO> getReviewResult(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int prNumber) {

        log.info("Fetching review result for {}/{} PR#{}", owner, repo, prNumber);
        ReviewResultDTO result = codeReviewService.getLatestReview(owner, repo, prNumber);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result);
    }
}
