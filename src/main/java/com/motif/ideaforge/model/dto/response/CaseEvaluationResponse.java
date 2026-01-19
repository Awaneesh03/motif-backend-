package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for case study evaluation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseEvaluationResponse {

    private Integer score;
    private String verdict;
    private List<String> feedback;
    private List<String> strengths;
    private List<String> improvements;
    private Instant timestamp;
}
