package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Optional web-search service backed by the Tavily Search API.
 *
 * <p>If {@code TAVILY_API_KEY} is not set the service returns an empty string
 * and the caller falls back to pure LLM reasoning (no web context).
 *
 * <p>Timeouts are intentionally short (10 s connect / 12 s read) so a slow
 * Tavily response does not block the analysis pipeline.
 */
@Service
@Slf4j
public class WebSearchService {

    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Value("${tavily.api.key:}")
    private String tavilyApiKey;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /** @return true if a Tavily API key is configured. */
    public boolean isAvailable() {
        return tavilyApiKey != null && !tavilyApiKey.isBlank();
    }

    /**
     * Run a single Tavily search and return a plain-text summary of the top
     * results suitable for injection into an LLM prompt.
     *
     * @param query  The search query (max ~200 chars is best).
     * @return       Formatted search-result context, or {@code ""} on failure.
     */
    public String search(String query) {
        if (!isAvailable()) {
            log.debug("Tavily API key not configured — skipping web search for query: {}", query);
            return "";
        }

        try {
            String bodyJson = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("api_key", tavilyApiKey);
                put("query", query);
                put("search_depth", "basic");
                put("max_results", 3);
                put("include_answer", false);
            }});

            Request request = new Request.Builder()
                    .url(TAVILY_URL)
                    .post(RequestBody.create(bodyJson, JSON))
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Tavily search failed — status {}, query: {}", response.code(), query);
                    return "";
                }

                String raw = response.body().string();
                JsonNode root = objectMapper.readTree(raw);
                JsonNode results = root.path("results");

                if (!results.isArray() || results.isEmpty()) {
                    return "";
                }

                StringBuilder sb = new StringBuilder();
                int count = 0;
                for (JsonNode result : results) {
                    if (count >= 3) break;
                    String title   = result.path("title").asText("").trim();
                    String content = result.path("content").asText("").trim();
                    if (content.isBlank()) continue;

                    // Truncate individual snippets to avoid context bloat
                    if (content.length() > 400) content = content.substring(0, 400) + "…";

                    sb.append("- ").append(title).append(": ").append(content).append("\n");
                    count++;
                }

                return sb.toString().trim();
            }

        } catch (Exception e) {
            // Any network / parse error → fail silently, analysis continues without web context
            log.warn("Tavily search error (non-fatal): {}", e.getMessage());
            return "";
        }
    }
}
