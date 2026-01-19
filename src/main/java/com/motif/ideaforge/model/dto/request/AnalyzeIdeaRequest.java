package com.motif.ideaforge.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for analyzing a startup idea
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeIdeaRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 100, message = "Title must be between 5 and 100 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 20, max = 1000, message = "Description must be between 20 and 1000 characters")
    private String description;

    @Size(max = 200, message = "Target market cannot exceed 200 characters")
    private String targetMarket;
}
