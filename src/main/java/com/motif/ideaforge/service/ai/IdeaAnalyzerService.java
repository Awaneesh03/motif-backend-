package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.exception.AIServiceException;
import com.motif.ideaforge.model.dto.request.AnalyzeIdeaRequest;
import com.motif.ideaforge.model.dto.response.AnalysisResponse;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for analyzing startup ideas using AI
 * This handles all business logic for idea analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdeaAnalyzerService {

    private final OpenAIService openAIService;
    private final IdeaAnalysisRepository ideaAnalysisRepository;
    private final ObjectMapper objectMapper;
    
    // Analysis settings — max_tokens raised to fit the full 7-step JSON output
    private static final int MAX_TOKENS = 3000;
    private static final int TIMEOUT_SECONDS = 45;
    private static final double TEMPERATURE = 0.5;

    // Removed @Transactional to allow returning results even if DB save fails
    public AnalysisResponse analyzeIdea(UUID userId, AnalyzeIdeaRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("=== IDEA ANALYZER SERVICE START ===");
        log.info("User ID: {}", userId);
        
        // Get effective values (supports both simple 'idea' and detailed 'title'+'description' formats)
        String effectiveTitle = request.getEffectiveTitle();
        String effectiveDescription = request.getEffectiveDescription();
        String targetMarket = request.getTargetMarket();
        
        log.info("Effective Title: {}", effectiveTitle);
        log.info("Effective Description ({} chars): {}", 
                effectiveDescription.length(),
                effectiveDescription.length() > 200 ? effectiveDescription.substring(0, 200) + "..." : effectiveDescription);
        log.info("Target Market: {}", targetMarket);

        // Build prompt with effective values
        String prompt = buildAnalysisPrompt(effectiveTitle, effectiveDescription, targetMarket);
        log.debug("=== PROMPT SENT TO OPENAI ===\n{}", prompt);
        
        List<ChatMessage> messages = List.of(
                ChatMessage.builder()
                        .role("system")
                        .content(getSystemPrompt())
                        .build(),
                ChatMessage.builder()
                        .role("user")
                        .content(prompt)
                        .build()
        );

        // Call OpenAI API with proper timeout
        log.info("Calling OpenAI API with timeout of {}s...", TIMEOUT_SECONDS);
        OpenAIResponse openAIResponse = openAIService.sendChatCompletionWithTimeout(
                messages, TEMPERATURE, MAX_TOKENS, TIMEOUT_SECONDS);

        // Parse response
        String aiResponse = openAIResponse.getChoices().get(0).getMessage().getContent();
        log.debug("=== RAW OPENAI RESPONSE ===\n{}", aiResponse);
        
        AnalysisResult analysis = parseAnalysisResponse(aiResponse);
        log.info("=== PARSED ANALYSIS === Score: {}, Strengths: {}, Weaknesses: {}", 
                analysis.getScore(), 
                analysis.getStrengths() != null ? analysis.getStrengths().size() : 0,
                analysis.getWeaknesses() != null ? analysis.getWeaknesses().size() : 0);

        // Upsert: update the existing row for this user+title, or insert a new one.
        // This prevents duplicate rows when the same idea is analyzed multiple times.
        try {
            Optional<IdeaAnalysis> existing = ideaAnalysisRepository.findByUserIdAndIdeaTitle(userId, effectiveTitle);
            IdeaAnalysis entity;
            if (existing.isPresent()) {
                // UPDATE — overwrite analysis fields, keep the same row (and its id/status)
                entity = existing.get();
                entity.setIdeaDescription(effectiveDescription);
                entity.setTargetMarket(targetMarket);
                entity.setScore(analysis.getScore());
                entity.setStrengths(analysis.getStrengths());
                entity.setWeaknesses(analysis.getWeaknesses());
                entity.setRecommendations(analysis.getRecommendations());
                entity.setMarketSize(analysis.getMarketSize());
                entity.setCompetition(analysis.getCompetition());
                entity.setViability(analysis.getViability());
                log.info("Updating existing analysis row for title: '{}'", effectiveTitle);
            } else {
                // INSERT — first time this user analyzes this idea
                entity = IdeaAnalysis.builder()
                        .userId(userId)
                        .ideaTitle(effectiveTitle)
                        .ideaDescription(effectiveDescription)
                        .targetMarket(targetMarket)
                        .score(analysis.getScore())
                        .strengths(analysis.getStrengths())
                        .weaknesses(analysis.getWeaknesses())
                        .recommendations(analysis.getRecommendations())
                        .marketSize(analysis.getMarketSize())
                        .competition(analysis.getCompetition())
                        .viability(analysis.getViability())
                        .build();
                log.info("Inserting new analysis row for title: '{}'", effectiveTitle);
            }

            IdeaAnalysis saved = ideaAnalysisRepository.save(entity);
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== ANALYSIS COMPLETE === ID: {}, Score: {}, Duration: {}ms",
                    saved.getId(), saved.getScore(), duration);
            return AnalysisResponse.fromEntity(saved);

        } catch (Exception dbException) {
            log.warn("Failed to save analysis to database, returning result anyway: {}", dbException.getMessage());
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== ANALYSIS COMPLETE (not saved) === Score: {}, Duration: {}ms",
                    analysis.getScore(), duration);
            return AnalysisResponse.builder()
                    .score(analysis.getScore())
                    .strengths(analysis.getStrengths())
                    .weaknesses(analysis.getWeaknesses())
                    .recommendations(analysis.getRecommendations())
                    .marketSize(analysis.getMarketSize())
                    .competition(analysis.getCompetition())
                    .viability(analysis.getViability())
                    .build();
        }
    }

    /**
     * Mark a saved analysis as "pending_review" so an admin can approve it for VCs.
     * Only the owner of the idea may submit it.
     */
    @Transactional
    public void submitForReview(UUID ideaId, UUID userId) {
        IdeaAnalysis entity = ideaAnalysisRepository.findById(ideaId)
                .orElseThrow(() -> new RuntimeException("Idea not found: " + ideaId));

        if (!entity.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized: cannot modify another user's idea");
        }

        entity.setStatus("pending_review");
        ideaAnalysisRepository.save(entity);
        log.info("Idea {} submitted for review by user {}", ideaId, userId);
    }

    private String buildAnalysisPrompt(String title, String description, String targetMarket) {
        return String.format("""
                ═══════════════════════════════════════
                IDEA UNDER ANALYSIS
                ═══════════════════════════════════════
                Title:       %s
                Description: %s
                Target Market: %s
                ═══════════════════════════════════════

                You are an investor conducting due diligence on the pitch above.
                Complete all 7 steps INTERNALLY in order, then output ONE JSON object.
                Do NOT output the steps. Do NOT output anything outside the JSON.

                ───────────────────────────────────────
                ABSOLUTE CONSTRAINTS
                Any violation makes the entire response invalid. Check each before outputting.
                ───────────────────────────────────────

                C1. LITERAL QUOTATION PROOF
                    Copy-paste 2–3 exact, word-for-word phrases from the Description into your JSON
                    (mark them with quotation marks so they are identifiable as direct quotes).
                    If the description lacks enough detail to quote, write in the relevant field:
                    "Insufficient detail — missing: [list exactly what is absent]"

                C2. FALSIFIABILITY TEST
                    Before finalising each sentence, ask: "Would this sentence be factually wrong
                    if applied to a different startup?" If the answer is NO, the sentence is too
                    generic. Delete it and rewrite it with a specific anchor — a quoted phrase,
                    a competitor name, a dollar figure, or a detail unique to this description.

                C3. BANNED PHRASES
                    Any response containing the following is invalid — rewrite immediately:
                    "scalable business model", "strong potential", "clear value proposition",
                    "room for innovation", "proven model", "significant opportunity",
                    "large addressable market" (unless followed immediately by a specific $ figure),
                    "product-market fit", "market validation", "customer acquisition", "game-changing",
                    "innovative solution", "disrupts the market".

                C4. REVENUE MODEL HONESTY
                    Describe the revenue model using only language present in the description.
                    If it is not stated, write: "Revenue model not specified — missing: [what is absent]"
                    Do not invent or assume a monetisation mechanism.

                C5. SCORE CEILING
                    Average, undifferentiated ideas must score 70 or below.
                    Score 80 or above ONLY when the description contains explicit evidence of a
                    proprietary moat, technical barrier, or regulatory advantage — and quote that evidence.
                    Score 90 or above only for ideas with all five dimensions above 16/20.

                ───────────────────────────────────────
                7-STEP INTERNAL ANALYSIS (complete in order)
                ───────────────────────────────────────

                STEP 1 — QUOTED EVIDENCE EXTRACTION
                Select 2–3 exact phrases from the description. For each phrase:
                  (a) Copy it verbatim.
                  (b) State the single most important business implication it carries.
                  (c) Confirm it would appear only in analysis of this idea, not another.

                STEP 2 — BUSINESS MODEL CLARIFICATION
                State the revenue model in one sentence, using only language from the description.
                Apply C4 if model is absent or ambiguous. Do not fill gaps with assumptions.

                STEP 3 — ASSUMPTION FAILURE ANALYSIS
                List exactly 5 assumptions this idea requires to succeed. For each assumption:
                  (a) State it precisely — it must be specific enough that it cannot appear on a
                      generic SaaS startup's assumption list.
                  (b) State the real-world condition under which it fails, with a concrete example.

                STEP 4 — BIDIRECTIONAL COMPETITOR ANALYSIS
                Name 2–3 real, specific companies operating in this exact market.
                For each competitor provide both directions:
                  THREAT: one specific capability they possess that this idea currently lacks.
                  EDGE:   one specific gap in their offering this idea could realistically exploit.

                STEP 5 — UNIQUE FAILURE SCENARIO
                Identify one failure mode that satisfies all three conditions:
                  (a) Originates directly from a specific detail stated in the description.
                  (b) Would not exist for a standard SaaS subscription company.
                  (c) Would cause this idea to fail even with a competent, fully funded team.

                STEP 6 — POINT DEDUCTION LEDGER
                Score each dimension starting at 20. Subtract points line-by-line.
                Every deduction must reference specific text from the description.
                Show the net score for each category.

                  Problem Severity     start=20  subtract: [reason tied to description] = net
                  Market Size Realism  start=20  subtract: [reason tied to description] = net
                  Defensibility        start=20  subtract: [reason tied to description] = net
                  Monetization         start=20  subtract: [reason tied to description] = net
                  Execution Complexity start=20  subtract: [reason tied to description] = net
                  (Execution = 20 means trivial MVP; 0 means years of R&D required.)

                STEP 7 — SELF-CHECK
                Re-read every planned sentence. For each one that would survive a title swap
                to a different startup, replace it with a sentence that contains a specific anchor
                — quoted text, competitor name, number, or detail unique to this description.
                No sentence passes Step 7 unless it is falsifiable for this specific idea.

                ───────────────────────────────────────
                OUTPUT — exact JSON only, no markdown, no text outside the braces
                ───────────────────────────────────────
                {
                  "score": <sum of the 5 Step-6 net scores, max 100>,
                  "strengths": [
                    "Problem Severity <net>/20: \\"<exact quoted phrase>\\" — <implication specific to this idea>",
                    "Market Size <net>/20: <idea-specific reasoning referencing the stated target market>",
                    "<best remaining category> <net>/20: <idea-specific reasoning with a specific anchor>"
                  ],
                  "weaknesses": [
                    "<Step-3 assumption most likely to fail — idea-specific, includes the failure condition>",
                    "<Step-3 second-most-critical assumption — idea-specific, includes the failure condition>",
                    "<Step-5 unique failure scenario — one sentence, would not apply to generic SaaS>"
                  ],
                  "recommendations": [
                    "<action targeting the lowest Step-6 net score — references a specific detail of this idea>",
                    "<action targeting the second-lowest Step-6 net score — references a specific detail>",
                    "<action that directly neutralises the Step-5 failure scenario>"
                  ],
                  "marketSize": "Market Size <net>/20 — $<single specific figure, not a range> TAM because <domain-specific reasoning tied to description>",
                  "competition": "<Competitor 1> [THREAT: <their edge over this idea>] [EDGE: <gap this idea fills>]. <Competitor 2> [THREAT: <their edge>] [EDGE: <gap this idea fills>]",
                  "viability": "Problem=<x>/20 Market=<x>/20 Defense=<x>/20 Monetization=<x>/20 Execution=<x>/20 = <total>/100 — <verdict containing at least one quoted phrase that makes it specific to this idea only>"
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
                (2) If you draft a generic sentence, delete it and replace it with a specific anchor
                    from the idea description before outputting.
                (3) Respond ONLY with valid JSON. No markdown. No text outside the JSON braces.
                (4) Average ideas score at or below 70. 80+ requires quoted evidence of a moat.
                (5) Never invent revenue models, market sizes, or features not stated in the pitch.""";
    }

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

        // Strip markdown code fences (```json ... ``` or ``` ... ```)
        if (s.startsWith("```")) {
            int newline = s.indexOf('\n');
            s = (newline != -1) ? s.substring(newline + 1) : s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.lastIndexOf("```"));
        }
        s = s.trim();

        // Find the outermost {...} to tolerate any text before/after the object
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            s = s.substring(start, end + 1);
        }

        return s.trim();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisResult {
        private Integer score;
        private List<String> strengths;
        private List<String> weaknesses;
        private List<String> recommendations;
        private String marketSize;
        private String competition;
        private String viability;
    }
}
