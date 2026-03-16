package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.exception.AIServiceException;
import com.motif.ideaforge.model.dto.request.EvaluateCaseRequest;
import com.motif.ideaforge.model.dto.response.CaseEvaluationResponse;
import com.motif.ideaforge.service.ai.OpenAIService.ChatMessage;
import com.motif.ideaforge.service.ai.OpenAIService.OpenAIResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseEvaluatorService {

    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

    public CaseEvaluationResponse evaluateCase(UUID userId, EvaluateCaseRequest request) {
        log.info("Evaluating case for user: {}", userId);
        String prompt = String.format("Evaluate this solution. Case: %s, Company: %s, Problem: %s, Solution: %s. Return JSON: {score, verdict, feedback[], strengths[], improvements[]}. Pass if score>=70.", request.getCaseTitle(), request.getCompany(), request.getProblem(), request.getSolution());
        List<ChatMessage> messages = List.of(
            ChatMessage.builder().role("system").content("You are an expert startup advisor. Evaluate solutions. Return valid JSON only.").build(),
            ChatMessage.builder().role("user").content(prompt).build()
        );
        OpenAIResponse resp = openAIService.sendChatCompletion(messages, 0.3, 1000).join();
        if (resp.getChoices() == null || resp.getChoices().isEmpty()) {
            throw new AIServiceException("AI service returned no response choices");
        }
        OpenAIService.Message msg = resp.getChoices().get(0).getMessage();
        if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
            throw new AIServiceException("AI service returned empty response content");
        }
        String content = msg.getContent();
        EvalResult result = parseResponse(content);
        return CaseEvaluationResponse.builder().score(result.getScore()).verdict(result.getVerdict()).feedback(result.getFeedback()).strengths(result.getStrengths()).improvements(result.getImprovements()).timestamp(Instant.now()).build();
    }

    private EvalResult parseResponse(String response) {
        try {
            String json = response.contains("```") ? response.substring(response.indexOf("{"), response.lastIndexOf("}")+1) : response.trim();
            return objectMapper.readValue(json, EvalResult.class);
        } catch (Exception e) {
            throw new AIServiceException("Failed to parse evaluation response");
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EvalResult {
        private Integer score;
        private String verdict;
        private List<String> feedback;
        private List<String> strengths;
        private List<String> improvements;
    }
}
