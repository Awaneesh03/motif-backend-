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
    
    // Analysis settings
    private static final int MAX_TOKENS = 2500;  // Increased for comprehensive analysis
    private static final int TIMEOUT_SECONDS = 90;  // Increased timeout for detailed analysis
    private static final double TEMPERATURE = 0.3;  // Lower = more consistent results

    @Transactional
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

        // Save to database
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

        IdeaAnalysis saved = ideaAnalysisRepository.save(entity);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("=== ANALYSIS COMPLETE === ID: {}, Score: {}, Duration: {}ms", 
                saved.getId(), saved.getScore(), duration);

        return AnalysisResponse.fromEntity(saved);
    }

    private String buildAnalysisPrompt(String title, String description, String targetMarket) {
        return String.format("""
                You are a senior startup analyst and VC partner. Analyze this startup idea deeply and critically.

                **Startup Idea Details:**
                - Title: %s
                - Problem/Solution Description: %s
                - Target Market: %s

                Analyze using this framework, then provide your response as JSON:

                1. **Problem Clarity**: Who experiences this problem? How painful (1-10)? Current alternatives?
                2. **Target Customer**: ICP details, early adopters, who will NOT buy
                3. **Market Analysis**: TAM/SAM/SOM estimates, growth trajectory, trend durability
                4. **Competition**: Direct/indirect competitors, switching costs, barriers to entry
                5. **Monetization**: Pricing model options, CAC estimate, LTV logic, path to first revenue
                6. **Execution Difficulty**: Technical complexity (1-10), operational challenges, regulatory risks
                7. **Critical Risks**: Market, execution, distribution, and timing risks
                8. **Validation Signals**: What metrics prove traction? Early PMF indicators?
                9. **Verdict**: Venture-backable? Bootstrappable? Who should build this?

                Respond ONLY with this JSON format:

                {
                  "score": <0-100 viability score>,
                  "strengths": ["specific strength 1", "specific strength 2", "specific strength 3"],
                  "weaknesses": ["critical weakness 1", "critical weakness 2", "critical weakness 3"],
                  "recommendations": ["actionable recommendation 1", "actionable recommendation 2", "actionable recommendation 3"],
                  "marketSize": "<TAM/SAM/SOM analysis with specific estimates and assumptions>",
                  "competition": "<competitive landscape analysis: direct competitors, indirect substitutes, defensibility>",
                  "viability": "<honest verdict: venture-backable/bootstrappable, who should build this, key success factors>"
                }

                Be analytical, specific, and brutally honest. No generic statements. Make assumptions explicit.
                """,
                title,
                description,
                targetMarket != null ? targetMarket : "General market"
        );
    }

    private String getSystemPrompt() {
        return """
                You are a senior VC partner and startup analyst with 20+ years of experience evaluating 1000+ startups.
                
                Your analysis style:
                - Brutally honest, not motivational
                - Specific numbers and examples, not vague statements
                - Clear assumptions stated explicitly
                - Focus on risks and red flags equally with opportunities
                - Think like an investor deciding whether to write a check
                
                Always respond with valid JSON only. No markdown, no explanations outside JSON.
                Every strength must be specific to THIS idea. Every weakness must be actionable.
                """;
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
