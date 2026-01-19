package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.exception.AIServiceException;
import com.motif.ideaforge.model.dto.request.GenerateIdeaRequest;
import com.motif.ideaforge.model.dto.response.IdeaResponse;
import com.motif.ideaforge.service.ai.GroqService.ChatMessage;
import com.motif.ideaforge.service.ai.GroqService.GroqResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdeaGeneratorService {

    private final GroqService groqService;
    private final ObjectMapper objectMapper;

    public IdeaResponse generateIdea(UUID userId, GenerateIdeaRequest request) {
        log.info("Generating startup idea for user: {}", userId);
        String prompt = "Generate a unique startup idea. Return JSON: {title, description, targetMarket}";
        List<ChatMessage> messages = List.of(
            ChatMessage.builder().role("system").content("You are a creative startup ideator. Return valid JSON only.").build(),
            ChatMessage.builder().role("user").content(prompt).build()
        );
        GroqResponse resp = groqService.sendChatCompletion(messages, 0.9, 500).join();
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

    @Data @Builder @AllArgsConstructor
    public static class GeneratedIdea {
        private String title;
        private String description;
        private String targetMarket;
    }
}
