package com.codereview.aicodereviewer.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the final review result including AI feedback
 */
@Data
public class ReviewResult {
    private String fileName;
    private CodeAnalysis analysis;
    private String aiFeedback;
    private double qualityScore;
    private List<String> issues = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
}
