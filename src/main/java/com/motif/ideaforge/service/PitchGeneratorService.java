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
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PitchGeneratorService {

    private final OpenAIService openAIService;

    public PitchResponse generatePitch(GeneratePitchRequest request) {
        log.info("Generating pitch deck for idea: {}", request.getIdeaName());

        String prompt = buildPitchPrompt(request);
        
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.builder()
                .role("system")
                .content("You are an expert pitch deck creator. Generate structured, professional pitch decks.")
                .build());
            messages.add(ChatMessage.builder()
                .role("user")
                .content(prompt)
                .build());
            
            OpenAIService.OpenAIResponse response = openAIService.sendChatCompletion(messages, 0.7, 4000).get();
            
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                String aiResponse = response.getChoices().get(0).getMessage().getContent();
                return parsePitchResponse(aiResponse, request);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error generating pitch deck", e);
            Thread.currentThread().interrupt();
        }
        
        return createDefaultPitch(request);
    }

    private String buildPitchPrompt(GeneratePitchRequest request) {
        return String.format(
            "Generate a 10-slide pitch deck for:\n" +
            "Idea: %s\nProblem: %s\nSolution: %s\n" +
            "Audience: %s\nMarket: %s\nUSP: %s\n" +
            "Return JSON: {slides: [{title, content, bulletPoints}], speakerNotes}",
            request.getIdeaName(), request.getProblem(), request.getSolution(),
            request.getAudience() != null ? request.getAudience() : "N/A",
            request.getMarket() != null ? request.getMarket() : "N/A",
            request.getUsp() != null ? request.getUsp() : "N/A"
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
