package com.codereview.aicodereviewer.model;

import lombok.Data;

/**
 * Stores information about a single method in a Java class
 */
@Data
public class MethodInfo {
    private String name;
    private int lineCount;
    private boolean hasLogging;
    private String visibility; // public, private, protected
}