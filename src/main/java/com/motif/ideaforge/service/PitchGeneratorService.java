package com.motif.ideaforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.model.dto.request.GeneratePitchRequest;
import com.motif.ideaforge.model.dto.response.PitchResponse;
import com.motif.ideaforge.model.dto.response.PitchResponse.SlideContent;
import com.motif.ideaforge.service.ai.OpenAIService;
import com.motif.ideaforge.service.ai.OpenAIService.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PitchGeneratorService {

    private final OpenAIService openAIService;
    /** Injected Spring-managed ObjectMapper — shared, thread-safe, avoids per-call allocation. */
    private final ObjectMapper objectMapper;

    private static final int MAX_TOKENS = 3000;
    private static final int TIMEOUT_SECONDS = 90;

    /** Cumulative fallback metrics — logged on each event for monitoring. */
    private final AtomicInteger fullFallbackCount = new AtomicInteger(0);
    private final AtomicInteger totalRepairCount  = new AtomicInteger(0);

    /** Required slide titles in mandatory order. Used for positional normalization. */
    private static final List<String> CANONICAL_TITLES = List.of(
        "Startup Introduction",
        "Problem",
        "Solution",
        "Market Opportunity",
        "Product Overview",
        "Business Model",
        "Traction / Roadmap",
        "Competitive Landscape",
        "Go-To-Market Strategy",
        "Financials / Revenue Projection",
        "Team",
        "Vision / Closing"
    );

    public PitchResponse generatePitch(GeneratePitchRequest request) {
        long startMs = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        log.info("Generating pitch deck requestId={} idea={}", requestId, request.getIdeaName());

        String prompt = buildPitchPrompt(request);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
            .role("system")
            .content("You are an expert startup pitch consultant. Return ONLY valid JSON, no markdown, no explanation.")
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
                    PitchResponse result = parsePitchResponse(msg.getContent(), request, requestId);
                    log.info("[analytics] pitch.completed requestId={} durationMs={} idea={}",
                            requestId, System.currentTimeMillis() - startMs, request.getIdeaName());
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("[analytics] pitch.error requestId={} durationMs={} idea={} error={}",
                    requestId, System.currentTimeMillis() - startMs, request.getIdeaName(), e.getMessage());
        }

        int fallbacks = fullFallbackCount.incrementAndGet();
        log.warn("[analytics] pitch.fallback type=api-no-content requestId={} durationMs={} idea={} totalFallbacks={}",
                requestId, System.currentTimeMillis() - startMs, request.getIdeaName(), fallbacks);
        return createDefaultPitch(request);
    }

    private String buildPitchPrompt(GeneratePitchRequest request) {
        return String.format(
            "Generate an investor-ready 12-slide pitch deck.\n\n" +
            "STARTUP DETAILS:\n" +
            "Name: %s\nProblem: %s\nSolution: %s\nAudience: %s\nMarket: %s\nUSP: %s\n\n" +
            "MANDATORY SLIDE ORDER (use these exact titles, in this exact order):\n" +
            "1. Startup Introduction\n" +
            "2. Problem\n" +
            "3. Solution\n" +
            "4. Market Opportunity\n" +
            "5. Product Overview\n" +
            "6. Business Model\n" +
            "7. Traction / Roadmap\n" +
            "8. Competitive Landscape\n" +
            "9. Go-To-Market Strategy\n" +
            "10. Financials / Revenue Projection\n" +
            "11. Team\n" +
            "12. Vision / Closing\n\n" +
            "RULES:\n" +
            "- Exactly 12 slides, in the order above, with exact titles\n" +
            "- 3 to 5 bullet points per slide\n" +
            "- Each bullet point: maximum 12 words, start with a verb or a number\n" +
            "- No paragraphs, no filler phrases (e.g. 'innovative solution', 'cutting-edge')\n" +
            "- No duplicate content across slides\n" +
            "- Investor-grade tone: sharp, specific, credible\n" +
            "- Make content realistic and specific to the startup above\n\n" +
            "Return ONLY this JSON (no markdown, no extra text):\n" +
            "{\"slides\":[{\"title\":\"Startup Introduction\",\"points\":[\"...\",\"...\",\"...\"]},{\"title\":\"Problem\",\"points\":[\"...\",\"...\",\"...\"]}]}",
            request.getIdeaName(), request.getProblem(), request.getSolution(),
            request.getAudience() != null ? request.getAudience() : "General market",
            request.getMarket() != null ? request.getMarket() : "Large growing market",
            request.getUsp() != null ? request.getUsp() : "Unique differentiated approach"
        );
    }

    private PitchResponse parsePitchResponse(String aiResponse, GeneratePitchRequest request, String requestId) {
        try {
            if (aiResponse.contains("{") && aiResponse.contains("}")) {
                int start = aiResponse.indexOf("{");
                int end = aiResponse.lastIndexOf("}") + 1;
                String json = aiResponse.substring(start, end);
                PitchResponse raw = objectMapper.readValue(json, PitchResponse.class);
                return validateAndNormalize(raw, request, requestId);
            }
        } catch (Exception e) {
            int fallbacks = fullFallbackCount.incrementAndGet();
            log.warn("[analytics] pitch.fallback type=parse-error requestId={} idea={} error={} totalFallbacks={}",
                    requestId, request.getIdeaName(), e.getMessage(), fallbacks);
        }
        return createDefaultPitch(request);
    }

    /**
     * Validates the parsed AI response and normalises it:
     *  1. Requires exactly 12 slides — full fallback if count is wrong (can't repair positionally).
     *  2. For each slide: 3–5 points required — repairs that slide from the default if violated
     *     (keeps all other AI-generated slides intact).
     *  3. Normalises each slide title to the canonical title for its position.
     *  4. Truncates every bullet to max 12 words, then applies formatBullet formatting.
     */
    private PitchResponse validateAndNormalize(PitchResponse raw, GeneratePitchRequest request, String requestId) {
        if (raw == null || raw.getSlides() == null || raw.getSlides().size() != 12) {
            int count = (raw == null || raw.getSlides() == null) ? 0 : raw.getSlides().size();
            int fallbacks = fullFallbackCount.incrementAndGet();
            log.warn("[analytics] pitch.fallback type=slide-count requestId={} got={} idea={} totalFallbacks={}",
                    requestId, count, request.getIdeaName(), fallbacks);
            return createDefaultPitch(request);
        }

        PitchResponse defaultPitch = null;  // built lazily — only if a slide needs repair
        List<SlideContent> normalised = new ArrayList<>(12);
        int repairedCount = 0;

        for (int i = 0; i < 12; i++) {
            SlideContent slide = raw.getSlides().get(i);
            List<String> points = (slide.getPoints() != null) ? slide.getPoints() : List.of();

            if (points.size() < 3 || points.size() > 5) {
                if (defaultPitch == null) defaultPitch = createDefaultPitch(request);
                normalised.add(defaultPitch.getSlides().get(i));
                repairedCount++;
                int repairs = totalRepairCount.incrementAndGet();
                log.warn("[analytics] pitch.repair requestId={} slideIndex={} title='{}' pointCount={} idea={} totalRepairs={}",
                        requestId, i + 1, CANONICAL_TITLES.get(i), points.size(), request.getIdeaName(), repairs);
            } else {
                List<String> processed = points.stream()
                        .map(this::truncateToTwelveWords)
                        .map(this::sanitiseLongTokens)
                        .map(this::formatBullet)
                        .collect(Collectors.toList());
                normalised.add(SlideContent.builder()
                        .title(CANONICAL_TITLES.get(i))
                        .points(processed)
                        .build());
            }
        }

        if (repairedCount > 0) {
            log.info("[analytics] pitch.repaired requestId={} repaired={}/12 idea={}",
                    requestId, repairedCount, request.getIdeaName());
        }

        return PitchResponse.builder().slides(normalised).build();
    }

    /** Truncates a bullet point to a maximum of 12 words. */
    private String truncateToTwelveWords(String text) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.trim().split("\\s+");
        if (words.length <= 12) return text.trim();
        return String.join(" ", Arrays.copyOf(words, 12));
    }

    /**
     * Truncates any whitespace-delimited token longer than 30 characters.
     * Guards against AI-hallucinated URLs, hashes, or runaway compound words
     * that would overflow PDF cells or look broken in the UI.
     */
    private String sanitiseLongTokens(String text) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 30) {
                words[i] = words[i].substring(0, 30);
            }
        }
        return String.join(" ", words);
    }

    /** Capitalises the first letter and removes trailing punctuation. */
    private String formatBullet(String text) {
        if (text == null || text.isBlank()) return text;
        String s = text.trim().replaceAll("[.,;:!?]+$", "");
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private PitchResponse createDefaultPitch(GeneratePitchRequest request) {
        String ideaName = request.getIdeaName();
        String market = request.getMarket() != null ? request.getMarket() : "Large growing market";
        List<SlideContent> slides = new ArrayList<>();
        slides.add(SlideContent.builder().title("Startup Introduction").points(List.of(ideaName + " — AI-powered startup platform", "Serves " + (request.getAudience() != null ? request.getAudience() : "general market"), "Solves real pain with a scalable product")).build());
        slides.add(SlideContent.builder().title("Problem").points(List.of(request.getProblem().substring(0, Math.min(60, request.getProblem().length())), "Existing solutions are fragmented and slow", "Millions of users face this daily with no fix")).build());
        slides.add(SlideContent.builder().title("Solution").points(List.of(request.getSolution().substring(0, Math.min(60, request.getSolution().length())), "Built specifically for speed and ease of use", "Reduces effort by over 70% vs manual process")).build());
        slides.add(SlideContent.builder().title("Market Opportunity").points(List.of(market, "Market growing at 20%+ CAGR annually", "Target segment: 50M+ potential users globally")).build());
        slides.add(SlideContent.builder().title("Product Overview").points(List.of("Core platform live with key features shipped", "Mobile-first with web and API access", "Intuitive UX built for non-technical users")).build());
        slides.add(SlideContent.builder().title("Business Model").points(List.of("SaaS subscription: $29/month per user", "Enterprise tier with custom pricing", "Marketplace revenue from third-party integrations")).build());
        slides.add(SlideContent.builder().title("Traction / Roadmap").points(List.of("MVP launched; early users onboarded", "Q2: Beta with 500 paying customers", "Q4: Full launch across 3 markets")).build());
        slides.add(SlideContent.builder().title("Competitive Landscape").points(List.of("Competitors lack AI-native architecture", "No incumbent owns this niche segment", "We ship 3x faster with half the cost")).build());
        slides.add(SlideContent.builder().title("Go-To-Market Strategy").points(List.of("Direct sales via LinkedIn outreach and content", "Product-led growth with freemium onboarding", "Strategic partnerships with industry leaders")).build());
        slides.add(SlideContent.builder().title("Financials / Revenue Projection").points(List.of("Year 1: $500K ARR from 200 paying customers", "Year 2: $2M ARR with 30% gross margin", "Break-even projected in month 18")).build());
        slides.add(SlideContent.builder().title("Team").points(List.of("CEO: 10 years in SaaS and product leadership", "CTO: Ex-Google engineer with AI/ML expertise", "Advisor network with 3 successful exits")).build());
        slides.add(SlideContent.builder().title("Vision / Closing").points(List.of("Become the default platform for this category", "Expand to 10 markets within 3 years", "Raise $2M seed to accelerate product and hiring")).build());
        return PitchResponse.builder().slides(slides).build();
    }
}
