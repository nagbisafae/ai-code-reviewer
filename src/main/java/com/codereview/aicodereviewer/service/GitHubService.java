package com.codereview.aicodereviewer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service to interact with GitHub API
 * Day 10: Fetch files from pull requests
 */
@Service
@Slf4j
public class GitHubService {

    @Value("${github.token:}")
    private String githubToken;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Get list of files changed in a pull request
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @return List of changed files with their content
     */
    public List<Map<String, Object>> getChangedFiles(String owner, String repo, int prNumber) {
        String url = String.format(
                "https://api.github.com/repos/%s/%s/pulls/%d/files",
                owner, repo, prNumber
        );

        log.info("Fetching changed files from: {}", url);

        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            List<Map<String, Object>> files = response.getBody();
            log.info("Found {} changed files", files != null ? files.size() : 0);

            return files != null ? files : new ArrayList<>();

        } catch (Exception e) {
            log.error("Error fetching files: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get the raw content of a file from GitHub
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param path File path
     * @param ref Git reference (branch/commit SHA)
     * @return File content as string
     */
    public String getFileContent(String owner, String repo, String path, String ref) {
        String url = String.format(
                "https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                owner, repo, path, ref
        );

        log.info("Fetching file content: {}", path);

        HttpHeaders headers = createHeaders();
        headers.setAccept(Collections.singletonList(MediaType.valueOf("application/vnd.github.raw")));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Error fetching file content: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Filter only Java files from changed files
     */
    public List<Map<String, Object>> filterJavaFiles(List<Map<String, Object>> files) {
        return files.stream()
                .filter(file -> {
                    String filename = (String) file.get("filename");
                    return filename != null && filename.endsWith(".java");
                })
                .toList();
    }

    /**
     * Create HTTP headers with GitHub token
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        if (githubToken != null && !githubToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + githubToken);
        }

        return headers;
    }

    /**
     * Get the line numbers that were changed (added or modified) in a PR file
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param fileName Name of the file to check
     * @return List of line numbers that were added/modified
     */
    public List<Integer> getChangedLines(String owner, String repo, int prNumber, String fileName) {
        List<Integer> changedLines = new ArrayList<>();

        try {
            // Fetch PR files to get the diff
            String url = String.format("https://api.github.com/repos/%s/%s/pulls/%d/files",
                    owner, repo, prNumber);

            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    List.class
            );

            if (response.getBody() == null) {
                log.warn("No files found in PR");
                return changedLines;
            }

            // Find the specific file
            for (Object fileObj : response.getBody()) {
                Map<String, Object> file = (Map<String, Object>) fileObj;
                String fileNameInPR = (String) file.get("filename");

                if (fileNameInPR.equals(fileName)) {
                    // Get the patch (diff) for this file
                    String patch = (String) file.get("patch");

                    if (patch != null) {
                        changedLines = parseDiffPatch(patch);
                        log.info("Found {} changed lines in {}", changedLines.size(), fileName);
                    }
                    break;
                }
            }

        } catch (Exception e) {
            log.error("Error getting changed lines: {}", e.getMessage(), e);
        }

        return changedLines;
    }

    /**
     * Parse GitHub diff patch format to extract line numbers
     *
     * GitHub patch format example:
     * @@ -1,5 +1,20 @@
     *  unchanged line
     * +added line
     * -removed line
     *
     * @param patch The diff patch string
     * @return List of line numbers for added/modified lines
     */
    private List<Integer> parseDiffPatch(String patch) {
        List<Integer> changedLines = new ArrayList<>();

        String[] lines = patch.split("\n");
        int currentLine = 0;

        for (String line : lines) {
            // Parse diff header to get starting line number
            // Format: @@ -old_start,old_count +new_start,new_count @@
            if (line.startsWith("@@")) {
                // Extract new file line number
                // Example: "@@ -1,5 +1,20 @@" -> we want the "1" after the +
                String[] parts = line.split("\\+");
                if (parts.length > 1) {
                    String lineInfo = parts[1].split(",")[0].split(" ")[0];
                    try {
                        currentLine = Integer.parseInt(lineInfo);
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse line number from: {}", line);
                    }
                }
                continue;
            }

            // Skip if we haven't found a header yet
            if (currentLine == 0) {
                continue;
            }

            // Process different line types
            if (line.startsWith("+")) {
                // This is an added line
                changedLines.add(currentLine);
                currentLine++;
            } else if (line.startsWith("-")) {
                // This is a removed line (don't increment currentLine)
                // We don't add removed lines to our list
            } else {
                // This is an unchanged line (context)
                currentLine++;
            }
        }

        return changedLines;
    }
}