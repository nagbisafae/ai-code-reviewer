package com.codereview.aicodereviewer.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing Java code using JavaParser library
 */
@Service
public class JavaParserService {

    /**
     * Parse Java code from a String
     */
    public CompilationUnit parseJavaCode(String code) {
        try {
            return StaticJavaParser.parse(code);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Java code: " + e.getMessage(), e);
        }
    }

    /**
     * Parse Java code from a File
     */
    public CompilationUnit parseJavaFile(File file) throws IOException {
        try {
            return StaticJavaParser.parse(file);
        } catch (Exception e) {
            throw new IOException("Failed to parse Java file: " + file.getName(), e);
        }
    }

    /**
     * Extract all class names from parsed code
     */
    public List<String> extractClassNames(CompilationUnit cu) {
        List<String> classNames = new ArrayList<>();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
            classNames.add(c.getNameAsString());
        });
        return classNames;
    }

    /**
     * Extract all method names from parsed code
     */
    public List<String> extractMethodNames(CompilationUnit cu) {
        List<String> methodNames = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(m -> {
            methodNames.add(m.getNameAsString());
        });
        return methodNames;
    }
}