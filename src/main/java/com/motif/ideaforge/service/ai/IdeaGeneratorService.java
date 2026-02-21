package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.exception.AIServiceException;
import com.motif.ideaforge.model.dto.request.GenerateIdeaRequest;
import com.motif.ideaforge.model.dto.response.IdeaResponse;
import com.motif.ideaforge.service.ai.OpenAIService.ChatMessage;
import com.motif.ideaforge.service.ai.OpenAIService.OpenAIResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdeaGeneratorService {

    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

    private static final int MAX_TOKENS = 300;
    private static final int TIMEOUT_SECONDS = 20;

    public IdeaResponse generateIdea(UUID userId, GenerateIdeaRequest request) {
        log.info("Generating startup idea for user: {}", userId);
        List<ChatMessage> messages = List.of(
            ChatMessage.builder().role("system").content("You are a startup ideator. Return ONLY valid JSON, no markdown.").build(),
            ChatMessage.builder().role("user").content(
                "Generate one unique startup idea. JSON only: {\"title\":\"<10 words max>\",\"description\":\"<2-3 sentences>\",\"targetMarket\":\"<e.g. B2B, SaaS>\"}"
            ).build()
        );
        OpenAIResponse resp = openAIService.sendChatCompletionWithTimeout(messages, 0.9, MAX_TOKENS, TIMEOUT_SECONDS);
        String content = resp.getChoices().get(0).getMessage().getContent();
        GeneratedIdea idea = parseResponse(content);
        return IdeaResponse.builder().title(idea.getTitle()).description(idea.getDescription()).targetMarket(idea.getTargetMarket()).build();
    }

    private GeneratedIdea parseResponse(String response) {
        try {
            String json = response.contains("```") ? response.substring(response.indexOf("{"), response.lastIndexOf("}")+1) : response.trim();
            return objectMapper.readValue(json, GeneratedIdea.class);
        } catch (Exception e) {
            throw new AIServiceException("Failed to parse AI response");
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GeneratedIdea {
        private String title;
        private String description;
        private String targetMarket;
    }
}
