package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        // Increased timeouts for complex AI requests
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)  // Increased for long AI responses
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Send chat completion request to OpenAI API with timeout
     * @param timeoutSeconds timeout in seconds (default 90 if null)
     */
    public OpenAIResponse sendChatCompletionWithTimeout(
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens,
            Integer timeoutSeconds) {
        
        int timeout = timeoutSeconds != null ? timeoutSeconds : 90;
        
        try {
            return sendChatCompletion(messages, temperature, maxTokens)
                    .get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("OpenAI API call timed out after {} seconds", timeout);
            throw new AIServiceException("AI service took too long to respond. Please try again with a simpler request.");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("OpenAI API call failed: {}", cause != null ? cause.getMessage() : e.getMessage(), cause);
            if (cause instanceof AIServiceException) {
                throw (AIServiceException) cause;
            }
            throw new AIServiceException("AI service error: " + (cause != null ? cause.getMessage() : e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("OpenAI API call interrupted", e);
            throw new AIServiceException("AI service call was interrupted");
        }
    }

    /**
     * Send chat completion request to OpenAI API
     */
    public CompletableFuture<OpenAIResponse> sendChatCompletion(
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens) {

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                OpenAIRequest request = OpenAIRequest.builder()
                        .model(model)
                        .messages(messages)
                        .temperature(temperature != null ? temperature : 0.7)
                        .maxTokens(maxTokens != null ? maxTokens : 1000)
                        .build();

                String requestBody = objectMapper.writeValueAsString(request);
                log.info("Sending request to OpenAI API - model: {}, maxTokens: {}, messagesCount: {}", 
                        model, maxTokens, messages.size());

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
                    long duration = System.currentTimeMillis() - startTime;
                    
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        log.error("OpenAI API error after {}ms: {} - {}", duration, response.code(), errorBody);
                        
                        // More specific error messages
                        if (response.code() == 429) {
                            throw new AIServiceException("AI service rate limit exceeded. Please wait a moment and try again.");
                        } else if (response.code() == 401) {
                            throw new AIServiceException("AI service authentication failed. Please contact support.");
                        } else if (response.code() >= 500) {
                            throw new AIServiceException("AI service is temporarily unavailable. Please try again later.");
                        }
                        throw new AIServiceException("AI service error (code: " + response.code() + ")");
                    }

                    if (response.body() == null) {
                        throw new AIServiceException("AI service returned empty response");
                    }
                    String responseBody = response.body().string();
                    log.info("OpenAI API response received in {}ms, length: {}", duration, responseBody.length());

                    OpenAIResponse openAIResponse = objectMapper.readValue(responseBody, OpenAIResponse.class);

                    int tokensUsed = 0;
                    if (openAIResponse.getUsage() != null && openAIResponse.getUsage().getTotalTokens() != null) {
                        tokensUsed = openAIResponse.getUsage().getTotalTokens();
                    }
                    log.info("OpenAI API call successful in {}ms. Tokens used: {}", duration, tokensUsed);

                    return openAIResponse;
                }
            } catch (IOException e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Network error calling OpenAI API after {}ms: {}", duration, e.getMessage(), e);
                throw new AIServiceException("Failed to connect to AI service. Please check your internet connection.", e);
            } catch (AIServiceException e) {
                throw e;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Unexpected error in OpenAI API call after {}ms: {}", duration, e.getMessage(), e);
                throw new AIServiceException("Unexpected error: " + e.getMessage(), e);
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

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean stream;
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

    // Streaming response DTOs
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamChunk {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<StreamChoice> choices;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamChoice {
        private Integer index;
        private StreamDelta delta;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamDelta {
        private String role;
        private String content;
    }

    /**
     * Stream chat completion from OpenAI API using SSE.
     * Tokens are pushed to the SseEmitter as they arrive.
     */
    public void streamChatCompletion(
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens,
            SseEmitter emitter) {

        try {
            OpenAIRequest request = OpenAIRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(temperature != null ? temperature : 0.7)
                    .maxTokens(maxTokens != null ? maxTokens : 1000)
                    .stream(true)
                    .build();

            String requestBody = objectMapper.writeValueAsString(request);
            log.info("Starting streaming request to OpenAI API - model: {}, maxTokens: {}", model, maxTokens);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            httpClient.newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("OpenAI streaming request failed: {}", e.getMessage());
                    try { emitter.completeWithError(e); } catch (Exception ignored) {}
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        log.error("OpenAI streaming API error: {} - {}", response.code(), errorBody);
                        try {
                            emitter.completeWithError(new AIServiceException("AI service error (code: " + response.code() + ")"));
                        } catch (Exception ignored) {}
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        try { emitter.completeWithError(new AIServiceException("Empty streaming response from OpenAI")); } catch (Exception ignored) {}
                        return;
                    }

                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(body.byteStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data: ")) continue;

                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                emitter.complete();
                                return;
                            }

                            try {
                                StreamChunk chunk = objectMapper.readValue(data, StreamChunk.class);
                                if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                                    StreamDelta delta = chunk.getChoices().get(0).getDelta();
                                    if (delta != null && delta.getContent() != null) {
                                        emitter.send(delta.getContent());
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("Skipping malformed SSE chunk: {}", data);
                            }
                        }
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Error reading streaming response: {}", e.getMessage());
                        try { emitter.completeWithError(e); } catch (Exception ignored) {}
                    }
                }
            });

        } catch (Exception e) {
            log.error("Failed to initiate streaming request: {}", e.getMessage());
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }
}
