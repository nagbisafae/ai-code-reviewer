package com.codereview.aicodereviewer.service;

import com.codereview.aicodereviewer.model.CodeAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import com.codereview.aicodereviewer.model.CodeIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Service that uses Google Gemini to provide intelligent code review feedback
 */
@Service
@Slf4j
public class AIReviewService {

    private final ChatModel chatModel;
    private final VulnerabilityDetectionService mlDetectionService;

    public AIReviewService(ChatModel chatModel, VulnerabilityDetectionService mlDetectionService) {
        this.chatModel = chatModel;
        this.mlDetectionService = mlDetectionService;
    }

    /**
     * Reviews Java code using Google Gemini and returns intelligent feedback
     *
     * @param code The Java code to review
     * @param analysis The structural analysis of the code
     * @return AI-generated feedback as a string
     */
    public String reviewCode(String code, CodeAnalysis analysis) {
        log.info("Requesting AI review for class: {}", analysis.getClassName());

        // Build the prompt with context
        String promptText = buildReviewPrompt();

        PromptTemplate template = new PromptTemplate(promptText);
        Prompt prompt = template.create(Map.of(
                "fileName", analysis.getFileName(),
                "className", analysis.getClassName() != null ? analysis.getClassName() : "Unknown",
                "methodCount", String.valueOf(analysis.getMethods().size()),
                "annotations", analysis.getAnnotations().isEmpty() ? "None" : String.join(", ", analysis.getAnnotations()),
                "totalLines", String.valueOf(analysis.getTotalLines()),
                "code", code
        ));

        try {
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();
            log.info("Received AI review for {}", analysis.getClassName());
            return content;
        } catch (Exception e) {
            log.error("Error getting AI review: {}", e.getMessage());
            throw new RuntimeException("Failed to get AI review: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the prompt template for code review
     */
    private String buildReviewPrompt() {
        return """
            You are an expert Java/Spring code reviewer with 10+ years of experience.
            
            Analyze this Java code and provide detailed, constructive feedback.
            
            FILE: {fileName}
            CLASS: {className}
            METHODS: {methodCount}
            ANNOTATIONS: {annotations}
            TOTAL LINES: {totalLines}
            
            CODE:
            ```java
            {code}
            ```
            
            Provide detailed feedback on:
            
            1. **Security Issues** (SQL injection, hardcoded secrets, etc.)
            2. **Code Complexity** (methods > 20 lines, deep nesting)
            3. **Best Practices** (Spring patterns, error handling, logging)
            4. **Naming Conventions** (clear, descriptive names)
            5. **Potential Bugs** (null checks, resource leaks)
            
            Format your response as:
            
            ## Issues Found
            [List critical issues with ❌]
            
            ## Warnings
            [List warnings with ⚠️]
            
            ## Suggestions
            [List improvement suggestions with 💡]
            
            ## Good Practices
            [List what's done well with ✅]
            
            ## Overall Score
            [Score out of 10 with brief justification]
            
            Be specific, actionable, and constructive. Focus on the most important issues first.
            """;
    }

    /**
     * Review code and return structured issues with line numbers
     * PURE AI APPROACH - AI does everything!
     *
     * @param code Java source code
     * @param fileName Name of the file being reviewed
     * @return List of issues with line numbers from AI
     */
    /**public List<CodeIssue> reviewCodeWithLineNumbers(String code, String fileName) {
        List<CodeIssue> issues = new ArrayList<>();

        try {
            // Ask AI for structured JSON response with line numbers
            String prompt = buildStructuredPrompt(code, fileName);

            PromptTemplate template = new PromptTemplate(prompt);
            Prompt aiPrompt = template.create(Map.of(
                    "fileName", fileName,
                    "code", code
            ));

            // Get AI response
            ChatResponse response = chatModel.call(aiPrompt);
            String aiResponse = response.getResult().getOutput().getText();

            log.info("Received AI response for {}", fileName);

            // Parse AI's JSON response into CodeIssue objects
            issues = parseAIJsonResponse(aiResponse, fileName);

            log.info("AI found {} issues with line numbers in {}", issues.size(), fileName);

        } catch (Exception e) {
            log.error("Error getting AI review: {}", e.getMessage(), e);
        }

        return issues;
    }**/

    /**
     * Build prompt asking AI for structured JSON with line numbers
     */
    private String buildStructuredPrompt(String code, String fileName) {
        return """
            You are an expert code reviewer. Analyze this Java code and identify ALL issues.
            
            For EACH issue, carefully identify the EXACT line number where it occurs by counting lines in the code.
            
            Return your response as a JSON array. Each issue must have:
            - lineNumber: the exact line number (integer)
            - severity: "error" or "warning" or "info"
            - issueType: short identifier (e.g., "sql_injection", "null_check", "hardcoded_secret")
            - description: clear explanation of the issue
            - suggestion: how to fix it
            
            CRITICAL: Return ONLY the JSON array, no markdown formatting, no code blocks, no explanation.
            
            CODE TO REVIEW:
            
            {code}
            
            RESPONSE FORMAT (example):
            [
              {
                "lineNumber": 3,
                "severity": "error",
                "issueType": "hardcoded_secret",
                "description": "API key is hardcoded in the code",
                "suggestion": "Move to environment variable or secure configuration file"
              },
              {
                "lineNumber": 8,
                "severity": "error",
                "issueType": "sql_injection",
                "description": "SQL query uses string concatenation with user input",
                "suggestion": "Use PreparedStatement to prevent SQL injection"
              }
            ]
            
            If no issues found, return an empty array: []
            
            REMEMBER: Return ONLY the JSON array, nothing else.
            """;
    }

    /**
     * Review code and return structured issues with line numbers
     * BEST APPROACH - Clean and reliable!
     */
    public List<CodeIssue> reviewCodeWithLineNumbers(String code, String fileName) {
        List<CodeIssue> issues = new ArrayList<>();

        try {
            // Build prompt with String.format (clean and reliable)
            String prompt = """
                You are an expert code reviewer. Analyze this Java code and identify ALL issues.
                
                For EACH issue, carefully identify the EXACT line number where it occurs by counting lines in the code.
                
                Return your response as a JSON array. Each issue must have:
                - lineNumber: the exact line number (integer)
                - severity: "error" or "warning" or "info"
                - issueType: short identifier (e.g., "sql_injection", "null_check", "hardcoded_secret")
                - description: clear explanation of the issue
                - suggestion: how to fix it
                
                CRITICAL: Return ONLY the JSON array, no markdown formatting, no code blocks, no explanation.
                
                CODE TO REVIEW:
                
                %s
                
                RESPONSE FORMAT (example):
                [
                  {
                    "lineNumber": 3,
                    "severity": "error",
                    "issueType": "hardcoded_secret",
                    "description": "API key is hardcoded in the code",
                    "suggestion": "Move to environment variable or secure configuration file"
                  }
                ]
                
                If no issues found, return an empty array: []
                
                REMEMBER: Return ONLY the JSON array, nothing else.
                """.formatted(code);  // ← Clean string interpolation!

            // Create simple prompt
            Prompt aiPrompt = new Prompt(prompt);

            // Get AI response
            ChatResponse response = chatModel.call(aiPrompt);
            String aiResponse = response.getResult().getOutput().getText();

            log.info("Received AI response for {}", fileName);

            // Parse AI's JSON response
            issues = parseAIJsonResponse(aiResponse, fileName);

            log.info("AI found {} issues with line numbers in {}", issues.size(), fileName);

        } catch (Exception e) {
            log.error("Error getting AI review: {}", e.getMessage(), e);
        }

        return issues;
    }

    /**
     * Parse AI's JSON response into CodeIssue objects
     */
    private List<CodeIssue> parseAIJsonResponse(String aiResponse, String fileName) {
        List<CodeIssue> issues = new ArrayList<>();

        try {
            // Clean up response (remove markdown if AI added it despite instructions)
            String jsonString = cleanJsonResponse(aiResponse);

            // Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonString);

            if (!rootNode.isArray()) {
                log.warn("AI response is not a JSON array");
                return issues;
            }

            // Convert each JSON object to CodeIssue
            for (JsonNode issueNode : rootNode) {
                try {
                    CodeIssue issue = CodeIssue.builder()
                            .fileName(fileName)
                            .lineNumber(issueNode.get("lineNumber").asInt())
                            .severity(issueNode.get("severity").asText())
                            .issueType(issueNode.get("issueType").asText())
                            .description(issueNode.get("description").asText())
                            .suggestion(issueNode.get("suggestion").asText())
                            .build();

                    issues.add(issue);
                } catch (Exception e) {
                    log.warn("Failed to parse issue: {}", e.getMessage());
                }
            }

            log.info("Successfully parsed {} issues from AI response", issues.size());

        } catch (Exception e) {
            log.error("Failed to parse AI JSON response: {}", e.getMessage());
            log.debug("AI Response was: {}", aiResponse);
        }

        return issues;
    }

    /**
     * Clean AI response to extract just the JSON
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();

        // Remove markdown code blocks if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * HYBRID APPROACH: Review code using BOTH ML model and Gemini AI
     *
     * 1. ML Model (GraphCodeBERT) - Fast, accurate vulnerability detection (83.93%)
     * 2. Gemini AI - Detailed code quality review and explanations
     *
     * This combines the strengths of both approaches for comprehensive review.
     *
     * @param code Java source code
     * @param fileName Name of the file being reviewed
     * @return Combined list of issues from both ML and AI
     */
    public List<CodeIssue> reviewCodeWithMLAndAI(String code, String fileName) {
        List<CodeIssue> allIssues = new ArrayList<>();

        log.info("Starting HYBRID review for: {}", fileName);
        log.info("============================================================");

        // STEP 1: ML Model Analysis (Fast & Accurate for Vulnerabilities)
        try {
            log.info("Step 1/2: ML Model Analysis...");
            List<CodeIssue> mlIssues = mlDetectionService.analyzeCode(code, fileName);

            if (!mlIssues.isEmpty()) {
                allIssues.addAll(mlIssues);
                log.info("ML Model found {} potential vulnerabilities", mlIssues.size());
            } else {
                log.info("ML Model: No high-confidence vulnerabilities detected");
            }
        } catch (Exception e) {
            log.warn("ML model analysis failed (degrading gracefully): {}", e.getMessage());
            // Continue with Gemini even if ML fails
        }

        // STEP 2: Gemini AI Analysis (Detailed Review)
        try {
            log.info("Step 2/2: Gemini AI Analysis...");
            List<CodeIssue> aiIssues = reviewCodeWithLineNumbers(code, fileName);

            if (!aiIssues.isEmpty()) {
                allIssues.addAll(aiIssues);
                log.info("Gemini AI found {} issues", aiIssues.size());
            } else {
                log.info("Gemini AI: No issues found");
            }
        } catch (Exception e) {
            log.error("Gemini AI analysis failed: {}", e.getMessage());
            // If we have ML results, we can still provide feedback
            if (allIssues.isEmpty()) {
                // Both failed - return empty list
                log.error("Both ML and AI analysis failed");
            }
        }

        log.info("============================================================");
        log.info("HYBRID REVIEW COMPLETE: {} total issues found", allIssues.size());

        return allIssues;
    }
}