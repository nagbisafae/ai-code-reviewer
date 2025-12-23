package com.codereview.aicodereviewer.service;

import com.codereview.aicodereviewer.model.CodeAnalysis;
import com.codereview.aicodereviewer.model.MethodInfo;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service that analyzes parsed Java code and extracts quality metrics
 */
@Service
@Slf4j
public class CodeAnalysisService {

    private final JavaParser javaParser = new JavaParser();

    /**
     * Analyzes Java code from a String
     *
     * @param code Java source code as string
     * @param fileName Name of the file being analyzed
     * @return CodeAnalysis object with all metrics
     */
    public CodeAnalysis analyzeCode(String code, String fileName) {
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(code);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                return analyzeCode(cu, fileName);
            } else {
                log.error("Failed to parse {}: {}", fileName, parseResult.getProblems());
                // Return empty analysis on parse failure
                CodeAnalysis emptyAnalysis = new CodeAnalysis();
                emptyAnalysis.setFileName(fileName);
                return emptyAnalysis;
            }
        } catch (Exception e) {
            log.error("Error parsing {}: {}", fileName, e.getMessage());
            CodeAnalysis emptyAnalysis = new CodeAnalysis();
            emptyAnalysis.setFileName(fileName);
            return emptyAnalysis;
        }
    }

    /**
     * Analyzes a CompilationUnit and extracts detailed metrics
     *
     * @param cu Parsed Java code
     * @param fileName Name of the file being analyzed
     * @return CodeAnalysis object with all metrics
     */
    public CodeAnalysis analyzeCode(CompilationUnit cu, String fileName) {
        CodeAnalysis analysis = new CodeAnalysis();
        analysis.setFileName(fileName);

        // Extract class information
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(classDecl -> {
            // Get class name
            analysis.setClassName(classDecl.getNameAsString());

            // Get all annotations on the class
            classDecl.getAnnotations().forEach(annotation -> {
                analysis.getAnnotations().add(annotation.getNameAsString());
            });
        });

        // Extract method information
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            MethodInfo methodInfo = analyzeMethod(method);
            analysis.getMethods().add(methodInfo);
        });

        // Calculate total lines in file
        cu.getRange().ifPresent(range -> {
            analysis.setTotalLines(range.end.line);
        });

        log.info("Analyzed {}: {} class with {} methods",
                fileName, analysis.getClassName(), analysis.getMethods().size());

        return analysis;
    }

    /**
     * Analyzes a single method and extracts its metrics
     */
    private MethodInfo analyzeMethod(MethodDeclaration method) {
        MethodInfo info = new MethodInfo();

        // Get method name
        info.setName(method.getNameAsString());

        // Get visibility (public, private, protected, package-private)
        info.setVisibility(method.getAccessSpecifier().asString());

        // Count lines in method
        method.getRange().ifPresent(range -> {
            int lineCount = range.end.line - range.begin.line + 1;
            info.setLineCount(lineCount);
        });

        // Check if method has logging
        String methodBody = method.toString();
        boolean hasLogging =
                methodBody.contains("log.") ||
                        methodBody.contains("logger.") ||
                        methodBody.contains("Logger.") ||
                        methodBody.contains("System.out.println") ||
                        methodBody.contains("System.err.println");

        info.setHasLogging(hasLogging);

        return info;
    }

    /**
     * Calculates a simple quality score based on metrics
     * Higher score = better quality
     */
    public double calculateQualityScore(CodeAnalysis analysis) {
        double score = 10.0; // Start with perfect score

        // Penalize for methods without logging
        long methodsWithoutLogging = analysis.getMethods().stream()
                .filter(m -> !m.isHasLogging())
                .count();
        score -= (methodsWithoutLogging * 0.5);

        // Penalize for long methods (> 20 lines)
        long longMethods = analysis.getMethods().stream()
                .filter(m -> m.getLineCount() > 20)
                .count();
        score -= (longMethods * 1.0);

        // Penalize for very long methods (> 50 lines)
        long veryLongMethods = analysis.getMethods().stream()
                .filter(m -> m.getLineCount() > 50)
                .count();
        score -= (veryLongMethods * 2.0);

        // Bonus for Spring annotations (good structure)
        if (!analysis.getAnnotations().isEmpty()) {
            score += 0.5;
        }

        // Keep score between 0 and 10
        return Math.max(0, Math.min(10, score));
    }
}