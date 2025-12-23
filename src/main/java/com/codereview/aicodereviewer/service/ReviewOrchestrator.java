package com.codereview.aicodereviewer.service;

import com.codereview.aicodereviewer.model.CodeAnalysis;
import com.codereview.aicodereviewer.model.CodeIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the entire code review workflow
 * Day 12: Connect webhook → fetch → review → comment
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewOrchestrator {

    private final GitHubService gitHubService;
    private final CodeAnalysisService codeAnalysisService;
    private final AIReviewService aiReviewService;
    private final GitHubCommentService commentService;
    private final GitHubAppAuthService gitHubAppAuthService;

    /**
     * Process a pull request: fetch files, review them, post comments
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param headSha Commit SHA of the PR head
     */
    /**public void processPullRequest(String owner, String repo, int prNumber, String headSha) {
        log.info("🚀 Starting review for PR #{} in {}/{}", prNumber, owner, repo);

        try {
            // Step 1: Fetch changed files
            List<Map<String, Object>> allFiles = gitHubService.getChangedFiles(owner, repo, prNumber);
            List<Map<String, Object>> javaFiles = gitHubService.filterJavaFiles(allFiles);

            if (javaFiles.isEmpty()) {
                log.info("No Java files changed in this PR");
                commentService.postReviewComment(owner, repo, prNumber,
                        "No Java files to review in this PR. ✅");
                return;
            }

            log.info("Found {} Java files to review", javaFiles.size());

            // Step 2: Review each Java file
            StringBuilder fullReview = new StringBuilder();
            fullReview.append("## Code Review Results\n\n");

            for (Map<String, Object> file : javaFiles) {
                String filename = (String) file.get("filename");
                log.info("Reviewing file: {}", filename);

                // Get file content
                String content = gitHubService.getFileContent(owner, repo, filename, headSha);
                if (content == null || content.isEmpty()) {
                    log.warn("Could not fetch content for {}", filename);
                    continue;
                }

                // Analyze the code
                CodeAnalysis analysis = codeAnalysisService.analyzeCode(content, filename);

                // Get AI review
                String aiReview = aiReviewService.reviewCode(content, analysis);

                // Add to full review
                fullReview.append("### 📄 ").append(filename).append("\n\n");
                fullReview.append(aiReview).append("\n\n");
                fullReview.append("---\n\n");
            }

            // Step 3: Post the complete review as a comment
            boolean posted = commentService.postReviewComment(owner, repo, prNumber, fullReview.toString());

            if (posted) {
                log.info("✅ Review completed and posted successfully!");
            } else {
                log.error("❌ Failed to post review comment");
            }

        } catch (Exception e) {
            log.error("Error processing PR: {}", e.getMessage(), e);

            // Try to post error comment
            try {
                commentService.postReviewComment(owner, repo, prNumber,
                        "❌ Error during code review: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Could not post error comment", ex);
            }
        }
    }**/

    /**
     * Process a pull request with inline comments on changed lines only
     * NOW SUPPORTS GITHUB APP AUTHENTICATION
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param headSha Commit SHA
     * @param installationId GitHub App installation ID (null for PAT mode)
     */
    public void processPullRequest(String owner, String repo, int prNumber, String headSha, Long installationId) {
        log.info("🚀 Starting review for PR #{} in {}/{}", prNumber, owner, repo);

        String installationToken = null;
        if (installationId != null) {
            try {
                installationToken = gitHubAppAuthService.getInstallationToken(installationId);
                gitHubService.setInstallationToken(installationToken);
                commentService.setInstallationToken(installationToken);
                log.info("🔐 Using GitHub App authentication (Installation ID: {})", installationId);
            } catch (Exception e) {
                log.error("Failed to get installation token, falling back to PAT: {}", e.getMessage());
            }
        } else {
            log.info("🔐 Using Personal Access Token authentication");
        }

        try {
            // Step 1: Fetch changed files
            List<Map<String, Object>> allFiles = gitHubService.getChangedFiles(owner, repo, prNumber);
            List<Map<String, Object>> javaFiles = gitHubService.filterJavaFiles(allFiles);

            if (javaFiles.isEmpty()) {
                log.info("No Java files changed in this PR");
                commentService.postReviewComment(owner, repo, prNumber,
                        "✅ No Java files to review in this PR.");
                return;
            }

            log.info("Found {} Java files to review", javaFiles.size());

            // Counters for summary
            int totalIssues = 0;
            int totalNewIssues = 0;
            StringBuilder summaryBuilder = new StringBuilder();
            summaryBuilder.append("## Code Review Summary\n\n");

            // Step 2: Review each Java file
            for (Map<String, Object> file : javaFiles) {
                String filename = (String) file.get("filename");
                log.info("Reviewing file: {}", filename);

                // Get file content
                String content = gitHubService.getFileContent(owner, repo, filename, headSha);
                if (content == null || content.isEmpty()) {
                    log.warn("Could not fetch content for {}", filename);
                    continue;
                }

                // Get changed lines
                List<Integer> changedLines = gitHubService.getChangedLines(owner, repo, prNumber, filename);
                log.info("Found {} changed lines in {}", changedLines.size(), filename);

                // Get AI review with line numbers
                List<CodeIssue> allIssues = aiReviewService.reviewCodeWithLineNumbers(content, filename);
                log.info("AI found {} total issues in {}", allIssues.size(), filename);

                // Filter to only issues on changed lines
                List<CodeIssue> newIssues = allIssues.stream()
                        .filter(issue -> changedLines.contains(issue.getLineNumber()))
                        .collect(Collectors.toList());

                log.info("Filtered to {} issues on changed lines", newIssues.size());

                // Post inline comments for new issues
                if (!newIssues.isEmpty()) {
                    int posted = commentService.postInlineComments(owner, repo, prNumber, headSha, newIssues);
                    log.info("Posted {} inline comments for {}", posted, filename);
                }

                // Update counters
                totalIssues += allIssues.size();
                totalNewIssues += newIssues.size();

                // Add to summary - CLEAN VERSION
                summaryBuilder.append(String.format("### %s\n\n", filename));
                summaryBuilder.append(String.format("- Lines reviewed: %d\n", changedLines.size()));
                summaryBuilder.append(String.format("- Issues found: %d total (%d in changed code)\n\n",
                        allIssues.size(), newIssues.size()));

                if (newIssues.isEmpty()) {
                    summaryBuilder.append("No issues found in changed code.\n\n");
                } else {
                    summaryBuilder.append(String.format("⚠️ **Action required:** Please review the **%d** inline comments above.\n\n", totalNewIssues));
                }

                // Mention old issues if any
                int oldIssues = allIssues.size() - newIssues.size();
                if (oldIssues > 0) {
                    summaryBuilder.append(String.format("*%d pre-existing issues in unchanged code*\n\n", oldIssues));
                }

                summaryBuilder.append("---\n\n");
            }

            // Step 3: Post summary comment - CLEAN VERSION
            summaryBuilder.append("### Overall\n\n");
            summaryBuilder.append(String.format("- Files reviewed: %d\n", javaFiles.size()));
            summaryBuilder.append(String.format("- Total issues: %d\n", totalIssues));
            summaryBuilder.append(String.format("- New issues: %d\n", totalNewIssues));
            summaryBuilder.append(String.format("- Pre-existing: %d\n\n", totalIssues - totalNewIssues));

            if (totalNewIssues == 0) {
                summaryBuilder.append("**All clear!** No new issues found.\n\n");
            } else {
                summaryBuilder.append(String.format("**Action required:** Please review the %d inline comments above.\n\n", totalNewIssues));
            }

            summaryBuilder.append("---\n");
            summaryBuilder.append("*Automated code review*");

            boolean posted = commentService.postReviewComment(owner, repo, prNumber, summaryBuilder.toString());

            if (posted) {
                log.info("✅ Review completed successfully!");
            } else {
                log.error("❌ Failed to post summary comment");
            }

        } catch (Exception e) {
            log.error("Error processing PR: {}", e.getMessage(), e);

            // Try to post error comment
            try {
                commentService.postReviewComment(owner, repo, prNumber,
                        "❌ Error during code review: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Could not post error comment", ex);
            }
        } finally {
            // IMPORTANT: Clean up tokens
            if (installationToken != null) {
                gitHubService.clearInstallationToken();
                commentService.clearInstallationToken();
            }
        }
    }

    /**
     * Quick health check - can we reach GitHub?
     */
    public boolean canConnectToGitHub() {
        try {
            // Pass null for installationId in health check
            gitHubService.getChangedFiles("octocat", "Hello-World", 1);
            return true;
        } catch (Exception e) {
            log.error("Cannot connect to GitHub: {}", e.getMessage());
            return false;
        }
    }
}