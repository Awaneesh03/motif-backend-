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
    
    // Analysis settings — keep tokens low for fast responses
    private static final int MAX_TOKENS = 1000;
    private static final int TIMEOUT_SECONDS = 45;
    private static final double TEMPERATURE = 0.3;

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

        // Try to save to database (optional - don't fail if DB is unavailable)
        IdeaAnalysis entity = IdeaAnalysis.builder()
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

        try {
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
            // Return the analysis result even if DB save failed
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

    private String buildAnalysisPrompt(String title, String description, String targetMarket) {
        return String.format("""
                Analyze this startup idea and respond ONLY with valid JSON — no markdown, no extra text.

                Title: %s
                Description: %s
                Target Market: %s

                JSON format (keep each string under 100 words):
                {
                  "score": <0-100>,
                  "strengths": ["strength 1", "strength 2", "strength 3"],
                  "weaknesses": ["weakness 1", "weakness 2", "weakness 3"],
                  "recommendations": ["action 1", "action 2", "action 3"],
                  "marketSize": "<TAM estimate with one concrete number>",
                  "competition": "<key competitors and one differentiator>",
                  "viability": "<High/Medium/Low Viability — one sentence reason>"
                }
                """,
                title,
                description,
                targetMarket != null ? targetMarket : "General"
        );
    }

    private String getSystemPrompt() {
        return "You are a startup analyst. Be specific and concise. Respond ONLY with valid JSON.";
    }

    private AnalysisResult parseAnalysisResponse(String response) {
        try {
            // Remove markdown code blocks if present
            String jsonString = response.trim();
            if (jsonString.startsWith("```json")) {
                jsonString = jsonString.substring(7);
            }
            if (jsonString.startsWith("```")) {
                jsonString = jsonString.substring(3);
            }
            if (jsonString.endsWith("```")) {
                jsonString = jsonString.substring(0, jsonString.length() - 3);
            }
            jsonString = jsonString.trim();

            return objectMapper.readValue(jsonString, AnalysisResult.class);
        } catch (Exception e) {
            log.error("Failed to parse analysis response: {}", response, e);
            throw new AIServiceException("Failed to parse AI analysis response");
        }
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
