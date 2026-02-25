package com.motif.ideaforge.model.dto.response;

import com.motif.ideaforge.model.entity.IdeaAnalysis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for idea analysis.
 *
 * <p>Includes both legacy string fields (for Supabase cache compat) and new
 * structured fields (competitors, market, confidenceScore) populated from
 * fresh AI analyses. The frontend always prefers the structured fields when present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {

    private String analysisId;
    private Integer score;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> recommendations;

    // ── Legacy string fields (Supabase cache read / old format) ───────────────
    /** Raw competition string — populated for legacy cache reads. Null for fresh AI results. */
    private String competition;
    /** Raw market-size string — populated for legacy cache reads. Null for fresh AI results. */
    private String marketSize;
    private String viability;

    // ── Structured fields (fresh AI analyses) ────────────────────────────────
    /** 1–3 sentence plain-language summary of the idea. */
    private String ideaSummary;
    /** Structured competitor list — 2–4 named competitors with threat/opportunity detail. */
    private List<CompetitorDto> competitors;
    /** 2–3 actionable strategic advantages tied to the idea description. */
    private String competitiveAdvantage;
    /** Structured market-size breakdown with TAM / SAM / SOM estimates. */
    private MarketSizeDto market;
    /** Per-dimension heuristic scores (each 0–20, sum = overall score). */
    private HeuristicScoresDto heuristicScores;
    /** Investor simulation: bull case, bear case, and key due-diligence questions. */
    private InvestorAnalysisDto investorAnalysis;
    /**
     * AI confidence in the analysis quality, 0–100.
     * 100 = full business detail provided; 0 = idea too vague to analyse.
     */
    private Integer confidenceScore;

    private Instant timestamp;

    /**
     * Build a response from a cached Supabase entity (legacy string fields).
     * New structured fields will be null — the frontend falls back to the legacy bridge.
     */
    public static AnalysisResponse fromEntity(IdeaAnalysis entity) {
        return AnalysisResponse.builder()
                .analysisId(entity.getId().toString())
                .score(entity.getScore())
                .strengths(entity.getStrengths())
                .weaknesses(entity.getWeaknesses())
                .recommendations(entity.getRecommendations())
                .marketSize(entity.getMarketSize())
                .competition(entity.getCompetition())
                .viability(entity.getViability())
                .timestamp(entity.getCreatedAt())
                .build();
    }
}
