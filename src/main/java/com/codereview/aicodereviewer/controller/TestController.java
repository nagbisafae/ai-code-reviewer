package com.codereview.aicodereviewer.controller;

import com.codereview.aicodereviewer.model.CodeAnalysis;
import com.codereview.aicodereviewer.service.AIReviewService;
import com.codereview.aicodereviewer.service.CodeAnalysisService;
import com.codereview.aicodereviewer.service.JavaParserService;
import com.github.javaparser.ast.CompilationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller to verify JavaParser and Analysis are working
 */
@RestController
@RequiredArgsConstructor
public class TestController {

    private final JavaParserService parserService;
    private final CodeAnalysisService analysisService;
    private final AIReviewService aiReviewService;

    /**
     * Test endpoint 1: Just parsing (from Day 2)
     */
    @PostMapping("/test-parse")
    public Map<String, Object> testParse(@RequestBody String javaCode) {
        try {
            CompilationUnit cu = parserService.parseJavaCode(javaCode);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("classes", parserService.extractClassNames(cu));
            result.put("methods", parserService.extractMethodNames(cu));

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Test endpoint 2: Full analysis (NEW - Day 3)
     */
    @PostMapping("/test-analyze")
    public Map<String, Object> testAnalyze(@RequestBody String javaCode) {
        try {
            // Step 1: Parse
            CompilationUnit cu = parserService.parseJavaCode(javaCode);

            // Step 2: Analyze
            CodeAnalysis analysis = analysisService.analyzeCode(cu, "TestFile.java");

            // Step 3: Calculate score
            double score = analysisService.calculateQualityScore(analysis);

            // Return everything
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("analysis", analysis);
            result.put("qualityScore", score);

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    @GetMapping("/test-openai")
    public Map<String, Object> testOpenAI() {
        try {
            // We'll implement this fully tomorrow
            // For now, just check if API key is set
            Map<String, Object> result = new HashMap<>();

            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey != null && apiKey.startsWith("sk-")) {
                result.put("success", true);
                result.put("message", "OpenAI API key is configured!");
                result.put("keyPrefix", apiKey.substring(0, 7) + "...");
            } else {
                result.put("success", false);
                result.put("message", "OpenAI API key not found or invalid");
            }

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Test endpoint 3: Full AI review (NEW - Day 5)
     */
    @PostMapping("/test-ai-review")
    public Map<String, Object> testAIReview(@RequestBody String javaCode) {
        try {
            // Step 1: Parse
            CompilationUnit cu = parserService.parseJavaCode(javaCode);

            // Step 2: Analyze
            CodeAnalysis analysis = analysisService.analyzeCode(cu, "TestFile.java");

            // Step 3: Get AI feedback
            String aiFeedback = aiReviewService.reviewCode(javaCode, analysis);

            // Step 4: Calculate score
            double score = analysisService.calculateQualityScore(analysis);

            // Return everything
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("analysis", analysis);
            result.put("qualityScore", score);
            result.put("aiFeedback", aiFeedback);

            return result;

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }
}