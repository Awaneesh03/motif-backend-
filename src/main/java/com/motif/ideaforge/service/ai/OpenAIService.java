package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
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
 * Service for interacting with OpenAI (ChatGPT) API
 * CRITICAL: This service securely handles API keys on the backend
 */
@Service
@Slf4j
public class OpenAIService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.model}")
    private String model;

    public OpenAIService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Send chat completion request to OpenAI API
     */
    public CompletableFuture<OpenAIResponse> sendChatCompletion(
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                OpenAIRequest request = OpenAIRequest.builder()
                        .model(model)
                        .messages(messages)
                        .temperature(temperature != null ? temperature : 0.7)
                        .maxTokens(maxTokens != null ? maxTokens : 1500)
                        .build();

                String requestBody = objectMapper.writeValueAsString(request);
                log.debug("Sending request to OpenAI API with model: {}", model);

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
                        log.error("OpenAI API error: {} - {}", response.code(), errorBody);
                        throw new AIServiceException("OpenAI API error: " + response.code());
                    }

                    if (response.body() == null) {
                        throw new AIServiceException("OpenAI API returned empty response");
                    }
                    String responseBody = response.body().string();
                    log.debug("OpenAI API response received, length: {}", responseBody.length());

                    OpenAIResponse openAIResponse = objectMapper.readValue(responseBody, OpenAIResponse.class);

                    int tokensUsed = 0;
                    if (openAIResponse.getUsage() != null && openAIResponse.getUsage().getTotalTokens() != null) {
                        tokensUsed = openAIResponse.getUsage().getTotalTokens();
                    }
                    log.info("OpenAI API call successful. Tokens used: {}", tokensUsed);

                    return openAIResponse;
                }
            } catch (IOException e) {
                log.error("Error calling OpenAI API", e);
                throw new AIServiceException("Failed to communicate with AI service", e);
            } catch (AIServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("Unexpected error in OpenAI API call", e);
                throw new AIServiceException("Unexpected error occurred", e);
            }
        });
    }

    // DTOs for OpenAI API
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;

        @JsonProperty("max_tokens")
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
    public static class OpenAIResponse {
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

        @JsonProperty("finish_reason")
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
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
