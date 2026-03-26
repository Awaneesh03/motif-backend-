package com.motif.ideaforge.service.ai;

import com.motif.ideaforge.exception.AIServiceException;
import com.motif.ideaforge.exception.ValidationException;
import com.motif.ideaforge.model.dto.request.ChatMessageRequest;
import com.motif.ideaforge.model.dto.response.ChatResponse;
import com.motif.ideaforge.service.ai.OpenAIService.ChatMessage;
import com.motif.ideaforge.service.ai.OpenAIService.OpenAIResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for chatbot functionality
 * Optimized for fast responses
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final OpenAIService openAIService;
    
    // Reduced token limits for faster responses
    private static final int MAX_TOKENS = 800;  // Was 4000 - much faster now
    private static final int TIMEOUT_SECONDS = 45;  // Timeout for chat responses
    private static final double TEMPERATURE = 0.7;

    public ChatResponse processMessage(UUID userId, ChatMessageRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("=== CHAT REQUEST === User: {}, Message length: {}", userId,
                request.getMessage() != null ? request.getMessage().length() : 0);

        List<ChatMessage> messages = buildMessages(request);

        // Call OpenAI API with timeout
        OpenAIResponse openAIResponse = openAIService.sendChatCompletionWithTimeout(
                messages, TEMPERATURE, MAX_TOKENS, TIMEOUT_SECONDS);

        if (openAIResponse.getChoices() == null || openAIResponse.getChoices().isEmpty()) {
            throw new AIServiceException("AI service returned no response choices");
        }
        OpenAIService.Message msg = openAIResponse.getChoices().get(0).getMessage();
        if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
            throw new AIServiceException("AI service returned empty response content");
        }
        String response = msg.getContent();

        long duration = System.currentTimeMillis() - startTime;
        log.info("=== CHAT SUCCESS === Duration: {}ms, Response length: {}", duration, response.length());

        return ChatResponse.builder()
                .message(response)
                .conversationId(request.getConversationId() != null ?
                        request.getConversationId() : UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .build();
    }

    public SseEmitter streamMessage(UUID userId, ChatMessageRequest request) {
        log.info("=== CHAT STREAM REQUEST === User: {}, Message length: {}", userId,
                request.getMessage() != null ? request.getMessage().length() : 0);

        SseEmitter emitter = new SseEmitter(60_000L); // 60 second timeout
        emitter.onTimeout(emitter::complete);

        List<ChatMessage> messages = buildMessages(request);
        openAIService.streamChatCompletion(messages, TEMPERATURE, MAX_TOKENS, emitter);

        return emitter;
    }

    private List<ChatMessage> buildMessages(ChatMessageRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ValidationException("Message cannot be empty");
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
                .role("system")
                .content(getChatbotSystemPrompt())
                .build());

        // Add conversation history (limit to last 10 messages to keep context small)
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            int historyStart = Math.max(0, request.getHistory().size() - 10);
            request.getHistory().subList(historyStart, request.getHistory().size())
                    .forEach(historyItem -> {
                        // Skip history entries with null content to avoid NPE in OpenAI call
                        if (historyItem.getRole() != null && historyItem.getContent() != null
                                && !historyItem.getContent().isBlank()) {
                            messages.add(ChatMessage.builder()
                                    .role(historyItem.getRole())
                                    .content(historyItem.getContent())
                                    .build());
                        }
                    });
            log.debug("Added {} history messages", Math.min(request.getHistory().size(), 10));
        }

        // Add current message
        messages.add(ChatMessage.builder()
                .role("user")
                .content(request.getMessage().trim())
                .build());

        return messages;
    }

    private String getChatbotSystemPrompt() {
        // Shortened prompt for faster processing
        return """
                You are Motif AI, a helpful startup assistant. You help entrepreneurs with:
                - Evaluating and refining startup ideas
                - Business strategy and market validation
                - Fundraising and growth advice
                
                Be concise, practical, and actionable. Keep responses under 300 words unless more detail is needed.
                """;
    }
}
