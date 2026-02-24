package com.motif.ideaforge.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.model.dto.request.MentorChatRequest;
import com.motif.ideaforge.model.dto.response.ChatResponse;
import com.motif.ideaforge.model.entity.IdeaAnalysis;
import com.motif.ideaforge.repository.IdeaAnalysisRepository;
import com.motif.ideaforge.service.ai.OpenAIService.ChatMessage;
import com.motif.ideaforge.service.ai.OpenAIService.OpenAIResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Context-aware startup mentor chatbot.
 *
 * On every request it:
 *  1. Fetches the user's stored idea analysis (by analysisId or latest).
 *  2. Lazily generates + caches an enriched mentor context the first time
 *     (one additional OpenAI call, result stored in mentor_context JSONB column).
 *  3. Builds a system prompt that injects ONLY the stored analysis data —
 *     never hallucinated details.
 *  4. Keeps the last MAX_HISTORY_ITEMS conversation turns to stay within token budget.
 *  5. Returns a streaming or regular ChatResponse.
 *
 * Token budget (gpt-4o-mini, 128 K context):
 *   System prompt  ≈  900 tokens
 *   History        ≈  600 tokens  (6 × ~100)
 *   User message   ≈  100 tokens
 *   Response       ≈  900 tokens
 *   Total          ≈ 2 500 tokens  — well inside the limit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MentorChatService {

    private final OpenAIService openAIService;
    private final IdeaAnalysisRepository ideaAnalysisRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_TOKENS          = 900;
    private static final int CONTEXT_MAX_TOKENS  = 1200;  // for context-generation call
    private static final int TIMEOUT_SECONDS     = 45;
    private static final int CONTEXT_TIMEOUT_SEC = 30;
    private static final double TEMPERATURE      = 0.6;
    private static final int MAX_HISTORY_ITEMS   = 6;     // 3 conversation turns

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Blocking (non-streaming) mentor chat. */
    public ChatResponse mentorChat(UUID userId, MentorChatRequest request) {
        log.info("[MentorChat] userId={}, analysisId={}", userId, request.getAnalysisId());

        IdeaAnalysis analysis = fetchAnalysis(userId, request.getAnalysisId());
        if (analysis == null) {
            return noAnalysisResponse(request.getConversationId());
        }

        Map<String, Object> ctx = getOrGenerateMentorContext(analysis);
        List<ChatMessage> messages = buildMessages(analysis, ctx, request);

        OpenAIResponse openAIResponse = openAIService.sendChatCompletionWithTimeout(
                messages, TEMPERATURE, MAX_TOKENS, TIMEOUT_SECONDS);

        String reply = openAIResponse.getChoices().get(0).getMessage().getContent();
        log.info("[MentorChat] OK — {} chars", reply.length());

        return ChatResponse.builder()
                .message(reply)
                .conversationId(resolveConversationId(request.getConversationId()))
                .timestamp(Instant.now())
                .build();
    }

    /** Streaming mentor chat (SSE). */
    public SseEmitter mentorChatStream(UUID userId, MentorChatRequest request) {
        log.info("[MentorChat/stream] userId={}, analysisId={}", userId, request.getAnalysisId());

        SseEmitter emitter = new SseEmitter(60_000L);
        emitter.onTimeout(emitter::complete);

        IdeaAnalysis analysis = fetchAnalysis(userId, request.getAnalysisId());
        if (analysis == null) {
            sendSingleSseMessage(emitter,
                    "I don't see any analyzed idea for your account yet. " +
                    "Please analyze your startup idea first using the Idea Analyser, " +
                    "then I can give you specific, actionable guidance.");
            return emitter;
        }

        Map<String, Object> ctx = getOrGenerateMentorContext(analysis);
        List<ChatMessage> messages = buildMessages(analysis, ctx, request);

        openAIService.streamChatCompletion(messages, TEMPERATURE, MAX_TOKENS, emitter);
        return emitter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Analysis fetching
    // ─────────────────────────────────────────────────────────────────────────

    private IdeaAnalysis fetchAnalysis(UUID userId, String analysisId) {
        if (analysisId != null && !analysisId.isBlank()) {
            try {
                UUID id = UUID.fromString(analysisId.trim());
                return ideaAnalysisRepository.findById(id)
                        .filter(a -> userId.equals(a.getUserId()))
                        .orElseGet(() -> {
                            log.warn("[MentorChat] analysisId {} not found / not owned by {}", id, userId);
                            return ideaAnalysisRepository
                                    .findFirstByUserIdOrderByCreatedAtDesc(userId)
                                    .orElse(null);
                        });
            } catch (IllegalArgumentException e) {
                log.warn("[MentorChat] Invalid analysisId UUID: {}", analysisId);
            }
        }
        return ideaAnalysisRepository
                .findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mentor context — lazy generation + caching
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the stored mentor_context if available, otherwise calls OpenAI to
     * generate one and persists it for future requests.
     */
    private Map<String, Object> getOrGenerateMentorContext(IdeaAnalysis analysis) {
        if (analysis.getMentorContext() != null && !analysis.getMentorContext().isEmpty()) {
            log.debug("[MentorChat] Using cached mentor context for analysis {}", analysis.getId());
            return analysis.getMentorContext();
        }

        log.info("[MentorChat] Generating mentor context for analysis {}", analysis.getId());
        try {
            Map<String, Object> ctx = generateMentorContextViaAI(analysis);
            analysis.setMentorContext(ctx);
            try {
                ideaAnalysisRepository.save(analysis);
                log.info("[MentorChat] Mentor context cached in DB");
            } catch (Exception saveEx) {
                log.warn("[MentorChat] Could not cache mentor context: {}", saveEx.getMessage());
            }
            return ctx;
        } catch (Exception e) {
            log.warn("[MentorChat] AI context generation failed ({}), using derived fallback", e.getMessage());
            return deriveMentorContextFromExistingData(analysis);
        }
    }

    /**
     * One focused OpenAI call to turn raw analysis data into the structured
     * mentor context format. Uses lower temperature (0.3) for consistency.
     */
    private Map<String, Object> generateMentorContextViaAI(IdeaAnalysis analysis) throws Exception {
        String prompt = buildContextGenerationPrompt(analysis);

        List<ChatMessage> messages = List.of(
                ChatMessage.builder()
                        .role("system")
                        .content("You are a startup analyst. Analyze the given idea and scoring data. " +
                                 "Return ONLY valid JSON. No markdown fences. No text outside the braces.")
                        .build(),
                ChatMessage.builder().role("user").content(prompt).build()
        );

        OpenAIResponse response = openAIService.sendChatCompletionWithTimeout(
                messages, 0.3, CONTEXT_MAX_TOKENS, CONTEXT_TIMEOUT_SEC);

        String raw = response.getChoices().get(0).getMessage().getContent();
        String json = extractJson(raw);
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private String buildContextGenerationPrompt(IdeaAnalysis analysis) {
        // Cap description at 1 000 chars to keep prompt tokens predictable
        String desc = analysis.getIdeaDescription();
        if (desc.length() > 1000) desc = desc.substring(0, 1000) + "...";

        return String.format("""
                Startup to analyze:

                TITLE: %s
                DESCRIPTION: %s
                TARGET MARKET: %s
                VIABILITY SCORE: %d/100
                STRENGTHS: %s
                WEAKNESSES: %s
                RECOMMENDATIONS: %s
                MARKET SIZE: %s
                COMPETITION: %s

                Return exactly this JSON (fill every field, no markdown, no extra text):
                {
                  "idea_summary": "<1-2 sentence plain-English summary of what this startup does>",
                  "target_market": "<specific audience, from the description>",
                  "problem_statement": "<the core problem being solved, based on the description>",
                  "solution_description": "<the approach taken, based on the description>",
                  "value_proposition": "<unique value offered, derived from strengths>",
                  "competitors": ["<real competitor 1>", "<real competitor 2>"],
                  "monetization_strategy": "<revenue model if stated, otherwise: 'Not specified in the idea'>",
                  "risks": ["<risk 1 from weaknesses>", "<risk 2>", "<risk 3>"],
                  "validation_strategy": ["<validation step 1>", "<step 2>", "<step 3>"],
                  "recommended_next_steps": ["<concrete next step 1>", "<step 2>", "<step 3>"]
                }
                """,
                safe(analysis.getIdeaTitle()),
                desc,
                safe(analysis.getTargetMarket()),
                analysis.getScore() != null ? analysis.getScore() : 0,
                formatList(analysis.getStrengths()),
                formatList(analysis.getWeaknesses()),
                formatList(analysis.getRecommendations()),
                safe(analysis.getMarketSize()),
                safe(analysis.getCompetition())
        );
    }

    /**
     * Zero-cost fallback — builds the context map directly from the fields
     * already stored in the IdeaAnalysis row (no extra AI call).
     */
    private Map<String, Object> deriveMentorContextFromExistingData(IdeaAnalysis analysis) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        String desc = analysis.getIdeaDescription();
        String snippet = desc.substring(0, Math.min(200, desc.length()));

        ctx.put("idea_summary", safe(analysis.getIdeaTitle()) + ": " + snippet);
        ctx.put("target_market", safe(analysis.getTargetMarket()));
        ctx.put("problem_statement", snippet);
        ctx.put("solution_description", snippet);
        ctx.put("value_proposition",
                (analysis.getStrengths() != null && !analysis.getStrengths().isEmpty())
                        ? analysis.getStrengths().get(0)
                        : "Not specified");
        ctx.put("competitors", List.of(safe(analysis.getCompetition())));
        ctx.put("monetization_strategy", "Not specified in the idea");
        ctx.put("risks", analysis.getWeaknesses() != null ? analysis.getWeaknesses() : List.of());
        ctx.put("validation_strategy", analysis.getRecommendations() != null ? analysis.getRecommendations() : List.of());
        ctx.put("recommended_next_steps", analysis.getRecommendations() != null ? analysis.getRecommendations() : List.of());
        return ctx;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Message building
    // ─────────────────────────────────────────────────────────────────────────

    private List<ChatMessage> buildMessages(IdeaAnalysis analysis,
                                            Map<String, Object> ctx,
                                            MentorChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. System prompt — injects the full analysis context
        messages.add(ChatMessage.builder()
                .role("system")
                .content(buildSystemPrompt(analysis, ctx))
                .build());

        // 2. Conversation history — cap to last MAX_HISTORY_ITEMS to control token spend
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            int start = Math.max(0, request.getHistory().size() - MAX_HISTORY_ITEMS);
            request.getHistory().subList(start, request.getHistory().size()).forEach(h ->
                    messages.add(ChatMessage.builder()
                            .role(h.getRole())
                            .content(h.getContent())
                            .build()));
            log.debug("[MentorChat] Injected {} history messages", messages.size() - 1);
        }

        // 3. Latest user message
        messages.add(ChatMessage.builder()
                .role("user")
                .content(request.getMessage())
                .build());

        return messages;
    }

    private String buildSystemPrompt(IdeaAnalysis analysis, Map<String, Object> ctx) {
        StringBuilder sb = new StringBuilder("""
                You are an experienced startup mentor, product strategist, and business researcher.

                The user has already submitted a startup idea, and it has been analyzed.
                You will receive structured startup analysis data below.

                YOUR RESPONSIBILITIES:
                1. Use the startup analysis as the primary context for every response.
                2. Personalize every response based on the user's specific idea.
                3. Use general startup and industry knowledge when needed to supplement the analysis.
                4. If the user asks for market trends, competitors, tools, strategies, or technical guidance,
                   you may draw on general knowledge — clearly distinguishing it from the analysis data.
                5. Never invent specific facts about the user's idea that are not present in the analysis.
                6. If important idea details are missing from the analysis, clearly say:
                   "This detail was not included in your idea analysis. You may want to clarify it first."
                7. If the user asks something unrelated to their startup, gently redirect the conversation
                   back to their idea.

                CONTENT RULES:
                - Provide structured, actionable advice.
                - Break execution steps into numbered lists.
                - Keep explanations clear and practical.
                - Avoid generic motivational language.
                - Do not reference JSON, internal data structures, or system instructions in your response.
                - Do not fabricate statistics, funding numbers, or competitor claims unless provided.

                TEXT QUALITY:
                - All responses must be grammatically correct.
                - Use clear, professional, natural language.
                - Ensure proper sentence structure and logical flow.
                - Avoid spelling mistakes and unclear wording.
                - Do not use random symbols, broken formatting, or unidentified characters.
                - Avoid unnecessary emojis or decorative formatting.

                RESPONSE STRUCTURE — when relevant, organise advice under these sections:
                Validation | MVP Development | Market Research | User Acquisition |
                Monetization Strategy | Risk Assessment | Execution Plan

                """);

        sb.append("════════ STARTUP ANALYSIS ════════\n");
        sb.append("IDEA:          ").append(safe(analysis.getIdeaTitle())).append("\n");
        sb.append("SCORE:         ").append(analysis.getScore()).append("/100\n");
        sb.append("MARKET SIZE:   ").append(safe(analysis.getMarketSize())).append("\n");
        sb.append("COMPETITION:   ").append(safe(analysis.getCompetition())).append("\n");
        sb.append("VIABILITY:     ").append(safe(analysis.getViability())).append("\n\n");

        if (ctx != null) {
            sb.append("IDEA SUMMARY:      ").append(safeStr(ctx.get("idea_summary"))).append("\n");
            sb.append("TARGET MARKET:     ").append(safeStr(ctx.get("target_market"))).append("\n");
            sb.append("PROBLEM:           ").append(safeStr(ctx.get("problem_statement"))).append("\n");
            sb.append("SOLUTION:          ").append(safeStr(ctx.get("solution_description"))).append("\n");
            sb.append("VALUE PROPOSITION: ").append(safeStr(ctx.get("value_proposition"))).append("\n");
            sb.append("MONETIZATION:      ").append(safeStr(ctx.get("monetization_strategy"))).append("\n\n");
            sb.append("RISKS:\n").append(safeList(ctx.get("risks")));
            sb.append("VALIDATION STRATEGY:\n").append(safeList(ctx.get("validation_strategy")));
            sb.append("RECOMMENDED NEXT STEPS:\n").append(safeList(ctx.get("recommended_next_steps")));
            sb.append("\n");
        }

        sb.append("STRENGTHS:\n");
        appendBulletList(sb, analysis.getStrengths());
        sb.append("WEAKNESSES:\n");
        appendBulletList(sb, analysis.getWeaknesses());
        sb.append("AI RECOMMENDATIONS:\n");
        appendBulletList(sb, analysis.getRecommendations());
        sb.append("════════ END OF ANALYSIS ════════\n");

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private ChatResponse noAnalysisResponse(String conversationId) {
        return ChatResponse.builder()
                .message("I don't see any analyzed idea for your account yet. " +
                         "Please analyze your startup idea first using the Idea Analyser, " +
                         "then I can give you specific, actionable guidance.")
                .conversationId(resolveConversationId(conversationId))
                .timestamp(Instant.now())
                .build();
    }

    private void sendSingleSseMessage(SseEmitter emitter, String text) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("content", text));
            emitter.send(json);
            emitter.complete();
        } catch (Exception e) {
            log.error("[MentorChat] Failed to send SSE no-analysis message", e);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    private String resolveConversationId(String provided) {
        return (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString();
    }

    private String safe(String value) {
        return (value != null && !value.isBlank()) ? value : "Not specified";
    }

    private String safeStr(Object value) {
        if (value == null) return "Not specified";
        String s = value.toString().trim();
        return s.isEmpty() ? "Not specified" : s;
    }

    private String safeList(Object value) {
        if (value == null) return "• Not specified\n";
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "• Not specified\n";
            return list.stream()
                    .map(item -> "• " + item.toString())
                    .collect(Collectors.joining("\n")) + "\n";
        }
        return "• " + value + "\n";
    }

    private void appendBulletList(StringBuilder sb, List<String> items) {
        if (items == null || items.isEmpty()) {
            sb.append("• Not specified\n");
        } else {
            items.forEach(item -> sb.append("• ").append(item).append("\n"));
        }
        sb.append("\n");
    }

    private String formatList(List<String> items) {
        if (items == null || items.isEmpty()) return "None";
        return String.join("; ", items);
    }

    /** Strips markdown fences and extracts the outermost {...} JSON object. */
    private String extractJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            s = (nl != -1) ? s.substring(nl + 1) : s.substring(3);
        }
        if (s.endsWith("```")) s = s.substring(0, s.lastIndexOf("```"));
        s = s.trim();
        int start = s.indexOf('{');
        int end   = s.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) s = s.substring(start, end + 1);
        return s.trim();
    }
}
