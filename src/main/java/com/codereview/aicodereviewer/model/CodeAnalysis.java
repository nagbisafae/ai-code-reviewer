package com.codereview.aicodereviewer.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the complete analysis of a Java file
 */
@Data
public class CodeAnalysis {
    private String fileName;
    private String className;
    private List<MethodInfo> methods = new ArrayList<>();
    private List<String> annotations = new ArrayList<>();
    private int totalLines;
}