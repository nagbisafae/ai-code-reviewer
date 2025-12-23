package com.codereview.aicodereviewer.controller;

import com.codereview.aicodereviewer.service.GitHubService;
import com.codereview.aicodereviewer.service.ReviewOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Webhook endpoint to receive GitHub events
 * Day 8-12: Complete GitHub integration
 */
@RestController
@RequestMapping("/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final ReviewOrchestrator reviewOrchestrator;
    private final GitHubService gitHubService;

    /**
     * Receives GitHub webhook events (pull request opened/synchronized)
     */
    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> handleGitHubWebhook(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody Map<String, Object> payload) {

        log.info("Received GitHub event: {}", eventType);

        if ("pull_request".equals(eventType)) {
            String action = (String) payload.get("action");
            log.info("Pull request action: {}", action);

            // Process "opened" and "synchronize" (new commits)
            if ("opened".equals(action) || "synchronize".equals(action)) {
                Map<String, Object> pullRequest = (Map<String, Object>) payload.get("pull_request");
                Map<String, Object> repo = (Map<String, Object>) payload.get("repository");

                // IMPORTANT: Extract installation ID for GitHub App
                Map<String, Object> installation = (Map<String, Object>) payload.get("installation");
                Long installationId = null;
                if (installation != null) {
                    installationId = ((Number) installation.get("id")).longValue();
                    log.info("Installation ID: {}", installationId);
                }

                // Extract PR details
                int prNumber = (int) pullRequest.get("number");
                String fullName = (String) repo.get("full_name");
                String[] parts = fullName.split("/");
                String owner = parts[0];
                String repoName = parts[1];

                Map<String, Object> head = (Map<String, Object>) pullRequest.get("head");
                String headSha = (String) head.get("sha");

                log.info("Triggering review for PR #{} in {}/{}", prNumber, owner, repoName);

                // Trigger async review with installation ID
                final Long finalInstallationId = installationId;
                new Thread(() -> {
                    reviewOrchestrator.processPullRequest(owner, repoName, prNumber, headSha, finalInstallationId);
                }).start();

                return ResponseEntity.ok(Map.of(
                        "status", "review_started",
                        "pr", String.valueOf(prNumber)
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "ignored",
                "event", eventType
        ));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean githubOk = reviewOrchestrator.canConnectToGitHub();
        return ResponseEntity.ok(Map.of(
                "status", githubOk ? "healthy" : "degraded",
                "github", githubOk ? "connected" : "disconnected"
        ));
    }

    @GetMapping("/test-changed-lines")
    public ResponseEntity<?> testChangedLines() {
        try {
            // Test with your existing PR
            List<Integer> changedLines = gitHubService.getChangedLines(
                    "nagbisafae",
                    "ai-code-review-test",
                    1,  // Your PR number
                    "BadCode.java"
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "changedLines", changedLines,
                    "count", changedLines.size()
            ));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}