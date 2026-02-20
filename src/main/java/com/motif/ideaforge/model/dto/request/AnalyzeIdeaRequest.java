package com.motif.ideaforge.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for analyzing a startup idea
 * Supports two input formats:
 * 1. Simple: {"idea": "your idea text"}
 * 2. Detailed: {"title": "...", "description": "...", "targetMarket": "..."}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeIdeaRequest {

    /**
     * Simple idea input - use this for quick analysis
     * If provided, title and description can be omitted
     */
    @Size(min = 10, max = 2000, message = "Idea must be between 10 and 2000 characters")
    private String idea;

    /**
     * Detailed title - optional if 'idea' is provided
     */
    @Size(min = 5, max = 100, message = "Title must be between 5 and 100 characters")
    private String title;

    /**
     * Detailed description - optional if 'idea' is provided
     */
    @Size(min = 20, max = 10000, message = "Description must be between 20 and 10000 characters")
    private String description;

    /**
     * Target market - always optional
     */
    @Size(max = 200, message = "Target market cannot exceed 200 characters")
    private String targetMarket;

    /**
     * Check if this request has valid input (either 'idea' or 'title'+'description')
     */
    public boolean hasValidInput() {
        boolean hasIdea = idea != null && !idea.isBlank();
        boolean hasDetailedInput = (title != null && !title.isBlank()) 
                && (description != null && !description.isBlank());
        return hasIdea || hasDetailedInput;
    }

    /**
     * Get the effective title for analysis
     */
    public String getEffectiveTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        // Extract title from idea (first sentence or first 100 chars)
        if (idea != null && !idea.isBlank()) {
            String ideaText = idea.trim();
            int endIndex = ideaText.indexOf('.');
            if (endIndex > 0 && endIndex <= 100) {
                return ideaText.substring(0, endIndex);
            }
            return ideaText.length() > 100 ? ideaText.substring(0, 100) : ideaText;
        }
        return "Untitled Idea";
    }

    /**
     * Get the effective description for analysis
     */
    public String getEffectiveDescription() {
        if (description != null && !description.isBlank()) {
            return description;
        }
        if (idea != null && !idea.isBlank()) {
            return idea;
        }
        return "";
    }
}
