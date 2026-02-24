package com.motif.ideaforge.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured market-size data returned by the AI analysis.
 * All figures are AI estimates based on description context — not real data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSizeDto {

    /** Estimated Total Addressable Market, e.g. "~$2B (estimate)". */
    private String tam;

    /** Estimated Serviceable Addressable Market tied to stated target. */
    private String sam;

    /** Realistic 3-year capturable share. */
    private String som;

    /** Annual growth assessment: "slow" | "moderate" | "fast" + brief reason. */
    @JsonProperty("growth_rate")
    private String growthRate;

    /** Plain-language basis for the estimates — domain reasoning, not fabricated source. */
    @JsonProperty("source_summary")
    private String sourceSummary;

    /** Bucketed size: "Small" | "Medium" | "Large". */
    private String category;
}
