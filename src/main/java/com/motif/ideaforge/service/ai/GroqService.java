package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.exception.AIServiceException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with Groq AI API
 * CRITICAL: This service securely handles API keys on the backend
 */
@Service
@Slf4j
public class GroqService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.groq.api-key}")
    private String apiKey;

    @Value("${app.groq.base-url}")
    private String baseUrl;

    @Value("${app.groq.model}")
    private String model;

    @Value("${app.groq.timeout}")
    private long timeout;

    public GroqService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Send chat completion request to Groq API
     */
    public CompletableFuture<GroqResponse> sendChatCompletion(
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                GroqRequest request = GroqRequest.builder()
                        .model(model)
                        .messages(messages)
                        .temperature(temperature != null ? temperature : 0.7)
                        .maxTokens(maxTokens != null ? maxTokens : 1500)
                        .build();

                String requestBody = objectMapper.writeValueAsString(request);
                log.debug("Sending request to Groq API with model: {}", model);

                Request httpRequest = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(
                                requestBody,
                                MediaType.parse("application/json")
                        ))
                        .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        log.error("Groq API error: {} - {}", response.code(), errorBody);
                        throw new AIServiceException("Groq API error: " + response.code());
                    }

                    String responseBody = response.body().string();
                    GroqResponse groqResponse = objectMapper.readValue(responseBody, GroqResponse.class);

                    log.info("Groq API call successful. Tokens used: {}",
                            groqResponse.getUsage() != null ? groqResponse.getUsage().getTotalTokens() : 0);

                    return groqResponse;
                }
            } catch (IOException e) {
                log.error("Error calling Groq API", e);
                throw new AIServiceException("Failed to communicate with AI service", e);
            } catch (Exception e) {
                log.error("Unexpected error in Groq API call", e);
                throw new AIServiceException("Unexpected error occurred", e);
            }
        });
    }

    // DTOs for Groq API
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroqRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;
        private Integer maxTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroqResponse {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<Choice> choices;
        private Usage usage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private Integer index;
        private Message message;
        private String finishReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
