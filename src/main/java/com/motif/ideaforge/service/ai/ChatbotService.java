package com.motif.ideaforge.service.ai;

import com.motif.ideaforge.model.dto.request.ChatMessageRequest;
import com.motif.ideaforge.model.dto.response.ChatResponse;
import com.motif.ideaforge.service.ai.GroqService.ChatMessage;
import com.motif.ideaforge.service.ai.GroqService.GroqResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for chatbot functionality
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final GroqService groqService;

    public ChatResponse processMessage(UUID userId, ChatMessageRequest request) {
        log.info("Processing chat message for user: {}", userId);

        // Build message history
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
                .role("system")
                .content(getChatbotSystemPrompt())
                .build());

        // Add conversation history
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            request.getHistory().forEach(historyItem ->
                    messages.add(ChatMessage.builder()
                            .role(historyItem.getRole())
                            .content(historyItem.getContent())
                            .build())
            );
        }

        // Add current message
        messages.add(ChatMessage.builder()
                .role("user")
                .content(request.getMessage())
                .build());

        // Call Groq API
        GroqResponse groqResponse = groqService
                .sendChatCompletion(messages, 0.7, 4000)
                .join();

        String response = groqResponse.getChoices().get(0).getMessage().getContent();

        log.info("Chat message processed successfully");

        return ChatResponse.builder()
                .message(response)
                .conversationId(request.getConversationId() != null ?
                        request.getConversationId() : UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .build();
    }

    private String getChatbotSystemPrompt() {
        return """
                You are Motif AI, a helpful assistant for the Motif platform that helps entrepreneurs
                and founders with their startup ideas. You specialize in:

                - Evaluating startup ideas and providing constructive feedback
                - Answering questions about business strategy, market validation, and product development
                - Offering insights on fundraising, growth strategies, and scaling
                - Providing resources and best practices for startups

                Be conversational, supportive, and practical. Provide actionable advice when possible.
                If you don't know something, admit it rather than making up information.
                Keep responses concise but informative.
                """;
    }
}
