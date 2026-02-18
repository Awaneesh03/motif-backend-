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

    @Transactional
    public AnalysisResponse analyzeIdea(UUID userId, AnalyzeIdeaRequest request) {
        log.info("Analyzing idea for user: {}", userId);

        // Build prompt
        String prompt = buildAnalysisPrompt(request);
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

        // Call OpenAI API
        OpenAIResponse openAIResponse = openAIService
                .sendChatCompletion(messages, 0.3, 1500)
                .join();

        // Parse response
        String aiResponse = openAIResponse.getChoices().get(0).getMessage().getContent();
        AnalysisResult analysis = parseAnalysisResponse(aiResponse);

        // Save to database
        IdeaAnalysis entity = IdeaAnalysis.builder()
                .userId(userId)
                .ideaTitle(request.getTitle())
                .ideaDescription(request.getDescription())
                .targetMarket(request.getTargetMarket())
                .score(analysis.getScore())
                .strengths(analysis.getStrengths())
                .weaknesses(analysis.getWeaknesses())
                .recommendations(analysis.getRecommendations())
                .marketSize(analysis.getMarketSize())
                .competition(analysis.getCompetition())
                .viability(analysis.getViability())
                .build();

        IdeaAnalysis saved = ideaAnalysisRepository.save(entity);

        log.info("Idea analyzed successfully. Analysis ID: {}, Score: {}", saved.getId(), saved.getScore());

        return AnalysisResponse.fromEntity(saved);
    }

    private String buildAnalysisPrompt(AnalyzeIdeaRequest request) {
        return String.format("""
                Analyze this startup idea and provide a detailed evaluation.

                **Idea Title:** %s
                **Description:** %s
                **Target Market:** %s

                Please provide your analysis in the following JSON format (respond ONLY with valid JSON):

                {
                  "score": <integer between 0-100>,
                  "strengths": ["strength 1", "strength 2", "strength 3"],
                  "weaknesses": ["weakness 1", "weakness 2", "weakness 3"],
                  "recommendations": ["recommendation 1", "recommendation 2", "recommendation 3"],
                  "marketSize": "<2-3 sentence analysis of market size and opportunity>",
                  "competition": "<2-3 sentence analysis of competitive landscape>",
                  "viability": "<2-3 sentence analysis of overall viability>"
                }

                Evaluate based on:
                - Market opportunity and demand
                - Competitive advantage
                - Feasibility and scalability
                - Revenue potential
                - Target market fit
                """,
                request.getTitle(),
                request.getDescription(),
                request.getTargetMarket() != null ? request.getTargetMarket() : "General market"
        );
    }

    private String getSystemPrompt() {
        return """
                You are an expert startup advisor and venture capital analyst with 20+ years of experience.
                You provide honest, constructive feedback on startup ideas.
                Always respond with valid JSON format only, no markdown formatting or additional text.
                Be specific and actionable in your analysis.
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
