package com.motif.ideaforge.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating a startup idea
 * Currently empty as generation doesn't require input parameters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateIdeaRequest {
    
    private String industry;  // Optional: specify industry for idea generation
    private String theme;     // Optional: specify theme/focus area
}
