package com.motif.ideaforge.service;

import com.motif.ideaforge.model.dto.request.GeneratePitchRequest;
import com.motif.ideaforge.model.dto.response.PitchResponse;
import com.motif.ideaforge.model.dto.response.PitchResponse.SlideContent;
import com.motif.ideaforge.service.ai.OpenAIService;
import com.motif.ideaforge.service.ai.OpenAIService.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PitchGeneratorService {

    private final OpenAIService openAIService;

    private static final int MAX_TOKENS = 2000;
    private static final int TIMEOUT_SECONDS = 60;

    public PitchResponse generatePitch(GeneratePitchRequest request) {
        log.info("Generating pitch deck for idea: {}", request.getIdeaName());

        String prompt = buildPitchPrompt(request);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
            .role("system")
            .content("You are a pitch deck creator. Return ONLY valid JSON, no markdown.")
            .build());
        messages.add(ChatMessage.builder()
            .role("user")
            .content(prompt)
            .build());

        try {
            OpenAIService.OpenAIResponse response = openAIService.sendChatCompletionWithTimeout(
                messages, 0.7, MAX_TOKENS, TIMEOUT_SECONDS);

            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                OpenAIService.Message msg = response.getChoices().get(0).getMessage();
                if (msg != null && msg.getContent() != null && !msg.getContent().isBlank()) {
                    return parsePitchResponse(msg.getContent(), request);
                }
            }
        } catch (Exception e) {
            log.error("Error generating pitch deck: {}", e.getMessage());
        }

        return createDefaultPitch(request);
    }

    private String buildPitchPrompt(GeneratePitchRequest request) {
        return String.format(
            "Generate a 10-slide pitch deck. Be concise — each slide: title (3-5 words), content (1-2 sentences), bulletPoints (2-3 items, max 8 words each).\n\n" +
            "Idea: %s\nProblem: %s\nSolution: %s\nAudience: %s\nMarket: %s\nUSP: %s\n\n" +
            "Return ONLY this JSON (no extra text):\n" +
            "{\"slides\":[{\"title\":\"...\",\"content\":\"...\",\"bulletPoints\":[\"...\",\"...\"]}],\"speakerNotes\":\"<2 sentences>\"}",
            request.getIdeaName(), request.getProblem(), request.getSolution(),
            request.getAudience() != null ? request.getAudience() : "General",
            request.getMarket() != null ? request.getMarket() : "Large market",
            request.getUsp() != null ? request.getUsp() : "Unique solution"
        );
    }

    private PitchResponse parsePitchResponse(String aiResponse, GeneratePitchRequest request) {
        try {
            if (aiResponse.contains("{") && aiResponse.contains("}")) {
                int start = aiResponse.indexOf("{");
                int end = aiResponse.lastIndexOf("}") + 1;
                String json = aiResponse.substring(start, end);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(json, PitchResponse.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI response", e);
        }
        return createDefaultPitch(request);
    }

    private PitchResponse createDefaultPitch(GeneratePitchRequest request) {
        List<SlideContent> slides = new ArrayList<>();
        slides.add(SlideContent.builder().title(request.getIdeaName()).content("Pitch").bulletPoints(List.of("Innovation", "Opportunity")).build());
        slides.add(SlideContent.builder().title("Problem").content(request.getProblem()).bulletPoints(List.of("Challenge 1", "Challenge 2")).build());
        slides.add(SlideContent.builder().title("Solution").content(request.getSolution()).bulletPoints(List.of("Feature 1", "Feature 2")).build());
        slides.add(SlideContent.builder().title("Market").content(request.getMarket() != null ? request.getMarket() : "Large market").bulletPoints(List.of("TAM", "SAM", "SOM")).build());
        slides.add(SlideContent.builder().title("Product").content("Features").bulletPoints(List.of("Feature 1", "Feature 2")).build());
        slides.add(SlideContent.builder().title("Business Model").content("Revenue").bulletPoints(List.of("Stream 1", "Stream 2")).build());
        slides.add(SlideContent.builder().title("Traction").content("Progress").bulletPoints(List.of("Milestone 1", "Milestone 2")).build());
        slides.add(SlideContent.builder().title("Competition").content("Landscape").bulletPoints(List.of("Competitor A", "Our edge")).build());
        slides.add(SlideContent.builder().title("Team").content("Our team").bulletPoints(List.of("CEO", "CTO")).build());
        slides.add(SlideContent.builder().title("Ask").content("Investment").bulletPoints(List.of("Amount", "Use of funds")).build());
        return PitchResponse.builder().slides(slides).speakerNotes("Speak with passion.").build();
    }
}
