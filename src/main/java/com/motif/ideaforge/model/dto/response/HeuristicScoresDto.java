package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-dimension scores from the AI's heuristic scoring model.
 * Each dimension is scored 0–20 and the five scores sum to the overall viability score (0–100).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeuristicScoresDto {

    /** Problem Severity — how acute and widespread the problem is. */
    private Integer problem;

    /** Market Size Realism — realistic TAM/SAM estimate for the stated market. */
    private Integer market;

    /** Defensibility — moat, lock-in, and protection from copy-cats. */
    private Integer defensibility;

    /** Monetization — clarity and credibility of the revenue model. */
    private Integer monetization;

    /** Execution Complexity — feasibility given typical founding team constraints. */
    private Integer execution;
}
