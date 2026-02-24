package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.exception.AIServiceException;
import com.motif.ideaforge.exception.InvalidStateException;
import com.motif.ideaforge.exception.ResourceNotFoundException;
import com.motif.ideaforge.model.dto.request.AnalyzeIdeaRequest;
import com.motif.ideaforge.model.dto.response.AnalysisResponse;
import com.motif.ideaforge.model.dto.response.CompetitorDto;
import com.motif.ideaforge.model.dto.response.MarketSizeDto;
import com.motif.ideaforge.model.dto.response.SubmitReviewResponse;
import com.motif.ideaforge.model.entity.IdeaAnalysis;
import com.motif.ideaforge.repository.IdeaAnalysisRepository;
import com.motif.ideaforge.service.ai.OpenAIService.ChatMessage;
import com.motif.ideaforge.service.ai.OpenAIService.OpenAIResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for analyzing startup ideas using AI.
 * Returns structured JSON with competitors, market breakdown, and numeric confidence score.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdeaAnalyzerService {

    private final OpenAIService openAIService;
    private final IdeaAnalysisRepository ideaAnalysisRepository;
    private final ObjectMapper objectMapper;

    // Analysis settings
    private static final int MAX_TOKENS = 4000;
    private static final int TIMEOUT_SECONDS = 45;
    private static final double TEMPERATURE = 0.5;

    // Removed @Transactional to allow returning results even if DB save fails
    public AnalysisResponse analyzeIdea(UUID userId, AnalyzeIdeaRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("=== IDEA ANALYZER SERVICE START ===");
        log.info("User ID: {}", userId);

        String effectiveTitle = request.getEffectiveTitle();
        String effectiveDescription = request.getEffectiveDescription();
        String targetMarket = request.getTargetMarket();

        log.info("Effective Title: {}", effectiveTitle);
        log.info("Effective Description ({} chars): {}",
                effectiveDescription.length(),
                effectiveDescription.length() > 200 ? effectiveDescription.substring(0, 200) + "..." : effectiveDescription);
        log.info("Target Market: {}", targetMarket);

        String prompt = buildAnalysisPrompt(effectiveTitle, effectiveDescription, targetMarket);
        log.debug("=== PROMPT SENT TO OPENAI ===\n{}", prompt);

        List<ChatMessage> messages = List.of(
                ChatMessage.builder().role("system").content(getSystemPrompt()).build(),
                ChatMessage.builder().role("user").content(prompt).build()
        );

        log.info("Calling OpenAI API with timeout of {}s...", TIMEOUT_SECONDS);
        OpenAIResponse openAIResponse = openAIService.sendJsonChatCompletionWithTimeout(
                messages, TEMPERATURE, MAX_TOKENS, TIMEOUT_SECONDS);

        if (openAIResponse.getChoices() == null || openAIResponse.getChoices().isEmpty()) {
            throw new AIServiceException("AI service returned no response choices");
        }
        OpenAIService.Message msg = openAIResponse.getChoices().get(0).getMessage();
        if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
            throw new AIServiceException("AI service returned empty response content");
        }

        String aiResponse = msg.getContent();
        log.debug("=== RAW OPENAI RESPONSE ===\n{}", aiResponse);

        AnalysisResult analysis = parseAnalysisResponse(aiResponse);

        // Validate score
        if (analysis.getScore() == null) {
            throw new AIServiceException("AI returned analysis without a score field");
        }
        analysis.setScore(Math.max(0, Math.min(100, analysis.getScore())));

        // Clamp confidence score
        if (analysis.getConfidenceScore() != null) {
            analysis.setConfidenceScore(Math.max(0, Math.min(100, analysis.getConfidenceScore())));
        }

        // Default null list fields
        if (analysis.getStrengths() == null)      analysis.setStrengths(Collections.emptyList());
        if (analysis.getWeaknesses() == null)     analysis.setWeaknesses(Collections.emptyList());
        if (analysis.getRecommendations() == null) analysis.setRecommendations(Collections.emptyList());
        if (analysis.getCompetitors() == null)    analysis.setCompetitors(Collections.emptyList());

        log.info("=== PARSED ANALYSIS === Score: {}, Competitors: {}, ConfidenceScore: {}",
                analysis.getScore(),
                analysis.getCompetitors().size(),
                analysis.getConfidenceScore());

        // Serialize structured fields to JSON strings for the TEXT columns
        String competitionJson = buildCompetitionJson(analysis);
        String marketSizeJson = buildMarketSizeJson(analysis);

        try {
            Optional<IdeaAnalysis> existing = ideaAnalysisRepository.findByUserIdAndIdeaTitle(userId, effectiveTitle);
            IdeaAnalysis entity;
            if (existing.isPresent()) {
                entity = existing.get();
                entity.setIdeaDescription(effectiveDescription);
                entity.setTargetMarket(targetMarket);
                entity.setScore(analysis.getScore());
                entity.setStrengths(analysis.getStrengths());
                entity.setWeaknesses(analysis.getWeaknesses());
                entity.setRecommendations(analysis.getRecommendations());
                entity.setMarketSize(marketSizeJson);
                entity.setCompetition(competitionJson);
                entity.setViability(analysis.getViability());
                log.info("Updating existing analysis row for title: '{}'", effectiveTitle);
            } else {
                entity = IdeaAnalysis.builder()
                        .userId(userId)
                        .ideaTitle(effectiveTitle)
                        .ideaDescription(effectiveDescription)
                        .targetMarket(targetMarket)
                        .score(analysis.getScore())
                        .strengths(analysis.getStrengths())
                        .weaknesses(analysis.getWeaknesses())
                        .recommendations(analysis.getRecommendations())
                        .marketSize(marketSizeJson)
                        .competition(competitionJson)
                        .viability(analysis.getViability())
                        .build();
                log.info("Inserting new analysis row for title: '{}'", effectiveTitle);
            }

            IdeaAnalysis saved = ideaAnalysisRepository.save(entity);
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== ANALYSIS COMPLETE === ID: {}, Score: {}, Duration: {}ms",
                    saved.getId(), saved.getScore(), duration);

            return buildResponse(saved.getId().toString(), analysis, saved.getCreatedAt());

        } catch (Exception dbException) {
            log.warn("Failed to save analysis to database, returning result anyway: {}", dbException.getMessage());
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== ANALYSIS COMPLETE (not saved) === Score: {}, Duration: {}ms",
                    analysis.getScore(), duration);

            return buildResponse(null, analysis, Instant.now());
        }
    }

    /**
     * Mark a saved analysis as "pending_review" so an admin can approve it for VCs.
     *
     * <p>Security: {@code findByIdAndUserId} returns empty if the idea doesn't exist
     * OR belongs to a different user — both surface as 404 to avoid leaking the
     * existence of other users' ideas.
     *
     * <p>Valid transition: {@code draft → pending_review}.
     * All other source states are rejected with 400.
     */
    @Transactional
    public SubmitReviewResponse submitForReview(UUID ideaId, UUID userId) {
        IdeaAnalysis entity = ideaAnalysisRepository.findByIdAndUserId(ideaId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found: " + ideaId));

        String current = entity.getStatus() != null ? entity.getStatus() : "draft";

        switch (current) {
            case "pending_review" ->
                throw new InvalidStateException("Idea is already submitted for review.");
            case "approved_for_vc" ->
                throw new InvalidStateException(
                        "Idea is already approved for VC funding and cannot be re-submitted.");
            case "rejected" ->
                throw new InvalidStateException(
                        "Idea has been rejected. Please contact support to appeal.");
            case "draft" -> { /* valid — proceed */ }
            default ->
                throw new InvalidStateException(
                        "Cannot submit idea with status '" + current + "'.");
        }

        entity.setStatus("pending_review");
        ideaAnalysisRepository.save(entity);
        log.info("Idea {} submitted for review by user {}", ideaId, userId);

        return SubmitReviewResponse.builder()
                .ideaId(ideaId.toString())
                .status("pending_review")
                .submittedAt(Instant.now())
                .build();
    }

    // ── Response builder ─────────────────────────────────────────────────────

    private AnalysisResponse buildResponse(String analysisId, AnalysisResult analysis, Instant timestamp) {
        // Map CompetitorData → CompetitorDto
        List<CompetitorDto> competitorDtos = analysis.getCompetitors().stream()
                .map(c -> CompetitorDto.builder()
                        .name(c.getName())
                        .threat(c.getThreat())
                        .opportunity(c.getOpportunity())
                        .build())
                .toList();

        // Map MarketSizeData → MarketSizeDto
        MarketSizeDto marketDto = null;
        if (analysis.getMarket() != null) {
            MarketSizeData m = analysis.getMarket();
            marketDto = MarketSizeDto.builder()
                    .tam(m.getTam())
                    .sam(m.getSam())
                    .som(m.getSom())
                    .growthRate(m.getGrowthRate())
                    .sourceSummary(m.getSourceSummary())
                    .category(m.getCategory())
                    .build();
        }

        return AnalysisResponse.builder()
                .analysisId(analysisId)
                .score(analysis.getScore())
                .strengths(analysis.getStrengths())
                .weaknesses(analysis.getWeaknesses())
                .recommendations(analysis.getRecommendations())
                .viability(analysis.getViability())
                .competitors(competitorDtos)
                .competitiveAdvantage(analysis.getCompetitiveAdvantage())
                .market(marketDto)
                .confidenceScore(analysis.getConfidenceScore())
                .timestamp(timestamp)
                .build();
    }

    // ── JSON serialization helpers for DB TEXT columns ───────────────────────

    private String buildCompetitionJson(AnalysisResult analysis) {
        try {
            return objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
                put("competitors", analysis.getCompetitors());
                put("competitiveAdvantage", analysis.getCompetitiveAdvantage());
            }});
        } catch (Exception e) {
            log.warn("Failed to serialize competition data: {}", e.getMessage());
            return "{}";
        }
    }

    private String buildMarketSizeJson(AnalysisResult analysis) {
        try {
            return analysis.getMarket() != null
                    ? objectMapper.writeValueAsString(analysis.getMarket())
                    : null;
        } catch (Exception e) {
            log.warn("Failed to serialize market size data: {}", e.getMessage());
            return null;
        }
    }

    // ── Prompt builders ──────────────────────────────────────────────────────

    private String buildAnalysisPrompt(String title, String description, String targetMarket) {
        return String.format("""
                ═══════════════════════════════════════
                IDEA UNDER ANALYSIS
                ═══════════════════════════════════════
                Title:         %s
                Description:   %s
                Target Market: %s
                ═══════════════════════════════════════

                You are an investor conducting due diligence on the pitch above.
                Complete all 7 steps INTERNALLY in order, then output ONE JSON object.
                Do NOT output the steps. Do NOT output anything outside the JSON braces.

                ───────────────────────────────────────
                ABSOLUTE CONSTRAINTS
                ───────────────────────────────────────

                C1. LITERAL QUOTATION PROOF
                    Every key insight must contain 1–2 exact, word-for-word phrases from
                    the Description (in quotation marks) as the anchor for your reasoning.
                    If the description lacks enough detail, write in the relevant field:
                    "Insufficient detail — missing: [list exactly what is absent]"

                C2. FALSIFIABILITY TEST
                    Before finalising each sentence, ask: "Would this sentence be factually
                    wrong if applied to a different startup?" If NO, delete and rewrite with
                    a specific anchor — a quoted phrase, competitor name, or unique detail.

                C3. BANNED PHRASES (auto-invalid if present — rewrite immediately)
                    "scalable business model", "strong potential", "clear value proposition",
                    "room for innovation", "proven model", "significant opportunity",
                    "product-market fit", "market validation", "game-changing",
                    "innovative solution", "disrupts the market"

                C4. REVENUE MODEL HONESTY
                    Only describe the revenue model using language from the description.
                    If absent: "Revenue model not specified — missing: [what is absent]"

                C5. SCORE CEILING
                    Average ideas: 70 or below.
                    80+ ONLY with explicit evidence of proprietary moat (quote the evidence).
                    90+ only if all five dimensions score above 16/20.

                C6. MARKET SIZE HONESTY
                    All TAM/SAM/SOM figures are estimates based on domain reasoning.
                    Prefix each with "~" to signal estimation (e.g. "~$1.5B").
                    Do NOT cite specific reports, studies, or data sources you cannot verify.
                    The source_summary field must explain the domain logic, not a fake citation.

                C7. COMPETITOR NAMES
                    Name only real, publicly known companies. Do not invent names.
                    Provide 2–4 competitors — no duplicates.

                ───────────────────────────────────────
                7-STEP INTERNAL ANALYSIS (complete in order, do NOT output)
                ───────────────────────────────────────

                STEP 1 — QUOTED EVIDENCE EXTRACTION
                Select 2–3 exact phrases from the description. For each:
                  (a) Copy verbatim.
                  (b) State the single most important business implication.
                  (c) Confirm it would appear only in analysis of this idea.

                STEP 2 — BUSINESS MODEL CLARIFICATION
                State the revenue model in one sentence using only description language.
                Apply C4 if absent.

                STEP 3 — ASSUMPTION FAILURE ANALYSIS
                List exactly 5 assumptions this idea requires. For each:
                  (a) State precisely — must be specific to this idea, not generic SaaS.
                  (b) State the real-world condition under which it fails, with example.

                STEP 4 — COMPETITOR ANALYSIS (2–4 real companies)
                For each competitor:
                  THREAT:      one specific capability they have that this idea lacks.
                  OPPORTUNITY: one specific gap in their offering this idea could exploit.

                STEP 5 — UNIQUE FAILURE SCENARIO
                One failure mode that: (a) originates from a specific description detail,
                (b) would not exist for a generic SaaS company, (c) would cause failure
                even with a competent fully-funded team.

                STEP 6 — POINT DEDUCTION LEDGER
                Score each dimension starting at 20. Subtract line-by-line with reasons
                tied to specific description text.
                  Problem Severity     start=20  subtract: [description-anchored reason] = net
                  Market Size Realism  start=20  subtract: [description-anchored reason] = net
                  Defensibility        start=20  subtract: [description-anchored reason] = net
                  Monetization         start=20  subtract: [description-anchored reason] = net
                  Execution Complexity start=20  subtract: [description-anchored reason] = net

                STEP 7 — SELF-CHECK
                Re-read every planned sentence. Replace any that would survive a title swap
                with a sentence containing a specific anchor from this description.

                ───────────────────────────────────────
                OUTPUT — exact JSON only, no markdown, no text outside the braces
                ───────────────────────────────────────
                {
                  "score": <sum of the 5 Step-6 net scores, integer 0–100>,
                  "strengths": [
                    "<best Step-6 category> <net>/20: \\"<exact quoted phrase>\\" — <implication specific to this idea>",
                    "<second-best category> <net>/20: <idea-specific reasoning with specific anchor>",
                    "<third-best category> <net>/20: <idea-specific reasoning with specific anchor>"
                  ],
                  "weaknesses": [
                    "<Step-3 assumption most likely to fail — idea-specific, includes failure condition>",
                    "<Step-3 second-most-critical assumption — idea-specific, includes failure condition>",
                    "<Step-5 unique failure scenario — one sentence, would not apply to generic SaaS>"
                  ],
                  "recommendations": [
                    "<action targeting the lowest Step-6 score — references specific description detail>",
                    "<action targeting second-lowest Step-6 score — references specific detail>",
                    "<action that directly neutralises the Step-5 failure scenario>"
                  ],
                  "market": {
                    "category": "<Small|Medium|Large>",
                    "tam": "~$<estimate> (e.g. '~$3B') — <one-sentence domain rationale>",
                    "sam": "~$<estimate> — <rationale tied to stated target market>",
                    "som": "~$<estimate> — <realistic 3-year capture rationale>",
                    "growth_rate": "<slow|moderate|fast> — <brief reason tied to the space>",
                    "source_summary": "<domain-pattern reasoning; do NOT cite reports you cannot verify>"
                  },
                  "competitors": [
                    {
                      "name": "<real company name>",
                      "threat": "<one specific capability they have that this idea lacks>",
                      "opportunity": "<one specific gap in their offering this idea could exploit>"
                    }
                  ],
                  "competitive_advantage": "<2–3 specific, actionable strategic advantages tied to details stated in the description. Must reference at least one quoted phrase. Must not be generic.>",
                  "viability": "Problem=<x>/20 Market=<x>/20 Defense=<x>/20 Monetization=<x>/20 Execution=<x>/20 — <verdict containing at least one quoted phrase from the description>",
                  "confidence_score": <integer 0–100; 100 = description has full business detail incl. revenue model, target user, moat; 0 = idea is too vague to analyse meaningfully>
                }
                """,
                title,
                description,
                targetMarket != null ? targetMarket : "General"
        );
    }

    private String getSystemPrompt() {
        return """
                You are a skeptical VC analyst conducting real due diligence on a specific startup pitch.
                Output rules:
                (1) Every sentence must be falsifiable: it must contain a specific name, number, or
                    quoted phrase that would be factually wrong if applied to a different startup.
                (2) If you draft a generic sentence, delete it and replace it with a specific anchor.
                (3) Respond ONLY with valid JSON. No markdown. No text outside the JSON braces.
                (4) Average ideas score at or below 70. 80+ requires quoted evidence of a moat.
                (5) Never invent revenue models, market sizes, or features not in the pitch.
                (6) Market figures must be prefixed with "~" to signal they are estimates.
                (7) Competitor names must be real, publicly known companies. No fabricated names.
                (8) competitive_advantage must contain 2–3 actionable advantages, not generic praise.""";
    }

    // ── Response parsing ─────────────────────────────────────────────────────

    private AnalysisResult parseAnalysisResponse(String response) {
        try {
            String jsonString = extractJson(response);
            return objectMapper.readValue(jsonString, AnalysisResult.class);
        } catch (Exception e) {
            log.error("Failed to parse analysis response: {}", response, e);
            throw new AIServiceException("Failed to parse AI analysis response");
        }
    }

    /**
     * Robustly extract the outermost JSON object from an AI response.
     * Handles: plain JSON, markdown-fenced JSON, and JSON with surrounding text.
     */
    private String extractJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int newline = s.indexOf('\n');
            s = (newline != -1) ? s.substring(newline + 1) : s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.lastIndexOf("```"));
        }
        s = s.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s.trim();
    }

    // ── Inner types for AI JSON parsing ──────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisResult {
        private Integer score;
        private List<String> strengths;
        private List<String> weaknesses;
        private List<String> recommendations;
        private MarketSizeData market;
        private List<CompetitorData> competitors;
        @JsonProperty("competitive_advantage")
        private String competitiveAdvantage;
        private String viability;
        @JsonProperty("confidence_score")
        private Integer confidenceScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorData {
        private String name;
        private String threat;
        private String opportunity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketSizeData {
        private String category;
        private String tam;
        private String sam;
        private String som;
        @JsonProperty("growth_rate")
        private String growthRate;
        @JsonProperty("source_summary")
        private String sourceSummary;
    }
}
