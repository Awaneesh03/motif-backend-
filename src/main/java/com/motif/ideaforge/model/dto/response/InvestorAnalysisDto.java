package com.motif.ideaforge.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Investor-simulation output: bull case, bear case, and due-diligence questions
 * that a VC would ask before writing a check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorAnalysisDto {

    /** Optimistic scenario — why this could be a breakout company. */
    @JsonProperty("bull_case")
    private String bullCase;

    /** Pessimistic scenario — why this could fail. */
    @JsonProperty("bear_case")
    private String bearCase;

    /** Hard due-diligence questions an investor would ask (2–4 items). */
    @JsonProperty("key_questions")
    private List<String> keyQuestions;
}
