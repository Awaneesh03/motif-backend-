package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for generated idea
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdeaResponse {
    private String title;
    private String description;
    private String targetMarket;
}
