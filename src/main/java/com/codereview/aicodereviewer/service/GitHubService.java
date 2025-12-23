package com.codereview.aicodereviewer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service to interact with GitHub API
 * NOW SUPPORTS GITHUB APP AUTHENTICATION
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubService {

    @Value("${github.token:}")
    private String githubToken;

    private final GitHubAppAuthService gitHubAppAuthService;
    private final RestTemplate restTemplate = new RestTemplate();

    // Thread-local storage for installation token
    private static final ThreadLocal<String> currentToken = new ThreadLocal<>();

    /**
     * Set the installation token for the current request
     * Call this at the start of processing a webhook
     */
    public void setInstallationToken(String token) {
        currentToken.set(token);
    }

    /**
     * Clear the installation token after processing
     */
    public void clearInstallationToken() {
        currentToken.remove();
    }

    /**
     * Get list of files changed in a pull request
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
     * Get the line numbers that were changed (added or modified) in a PR file
     */
    public List<Integer> getChangedLines(String owner, String repo, int prNumber, String fileName) {
        List<Integer> changedLines = new ArrayList<>();

        try {
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

            for (Object fileObj : response.getBody()) {
                Map<String, Object> file = (Map<String, Object>) fileObj;
                String fileNameInPR = (String) file.get("filename");

                if (fileNameInPR.equals(fileName)) {
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
     */
    private List<Integer> parseDiffPatch(String patch) {
        List<Integer> changedLines = new ArrayList<>();

        String[] lines = patch.split("\n");
        int currentLine = 0;

        for (String line : lines) {
            if (line.startsWith("@@")) {
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

            if (currentLine == 0) {
                continue;
            }

            if (line.startsWith("+")) {
                changedLines.add(currentLine);
                currentLine++;
            } else if (line.startsWith("-")) {
                // Removed line, don't increment
            } else {
                currentLine++;
            }
        }

        return changedLines;
    }

    /**
     * Create HTTP headers with authentication
     * UPDATED: Now uses installation token if available, falls back to PAT
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        // Priority 1: Use installation token if set (GitHub App mode)
        String installationToken = currentToken.get();
        if (installationToken != null && !installationToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + installationToken);
            log.debug("Using GitHub App installation token");
        }
        // Priority 2: Fall back to Personal Access Token
        else if (githubToken != null && !githubToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + githubToken);
            log.debug("Using Personal Access Token");
        }

        return headers;
    }
}