package com.codereview.aicodereviewer.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Represents a code issue found during review
 * Includes location information for inline commenting
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeIssue {

    /**
     * File where the issue was found
     */
    private String fileName;

    /**
     * Line number where the issue occurs
     */
    private int lineNumber;

    /**
     * Severity level: "error", "warning", "info"
     */
    private String severity;

    /**
     * Type of issue (e.g., "sql_injection", "null_check", "hardcoded_secret")
     */
    private String issueType;

    /**
     * Human-readable description of the issue
     */
    private String description;

    /**
     * Suggested fix or improvement
     */
    private String suggestion;

    /**
     * Code snippet showing the problematic line (optional)
     */
    private String codeSnippet;

    /**
     * Helper method to format as markdown comment
     */
    public String toMarkdownComment() {
        StringBuilder comment = new StringBuilder();

        // Add emoji based on severity
        String emoji = switch (severity.toLowerCase()) {
            case "error" -> "❌";
            case "warning" -> "⚠️";
            case "info" -> "💡";
            default -> "📝";
        };

        comment.append(emoji).append(" **").append(description).append("**\n\n");

        if (suggestion != null && !suggestion.isEmpty()) {
            comment.append("**Suggestion:**\n");
            comment.append(suggestion).append("\n\n");
        }

        if (codeSnippet != null && !codeSnippet.isEmpty()) {
            comment.append("```java\n");
            comment.append(codeSnippet).append("\n");
            comment.append("```\n");
        }

        return comment.toString();
    }
}