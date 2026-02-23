package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.request.AnalyzeIdeaRequest;
import com.motif.ideaforge.model.dto.request.ChatMessageRequest;
import com.motif.ideaforge.model.dto.request.GenerateIdeaRequest;
import com.motif.ideaforge.model.dto.request.EvaluateCaseRequest;
import com.motif.ideaforge.model.dto.request.GeneratePitchRequest;
import com.motif.ideaforge.model.dto.request.ImproveDescriptionRequest;
import com.motif.ideaforge.model.dto.request.MentorChatRequest;
import com.motif.ideaforge.model.dto.response.AnalysisResponse;
import com.motif.ideaforge.model.dto.response.ChatResponse;
import com.motif.ideaforge.model.dto.response.IdeaResponse;
import com.motif.ideaforge.model.dto.response.CaseEvaluationResponse;
import com.motif.ideaforge.model.dto.response.PitchResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.ai.ChatbotService;
import com.motif.ideaforge.service.ai.IdeaAnalyzerService;
import com.motif.ideaforge.service.ai.IdeaGeneratorService;
import com.motif.ideaforge.service.ai.CaseEvaluatorService;
import com.motif.ideaforge.service.ai.MentorChatService;
import com.motif.ideaforge.service.PitchGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Services", description = "AI-powered endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AIController {

    private final IdeaAnalyzerService ideaAnalyzerService;
    private final ChatbotService chatbotService;
    private final MentorChatService mentorChatService;
    private final IdeaGeneratorService ideaGeneratorService;
    private final CaseEvaluatorService caseEvaluatorService;
    private final PitchGeneratorService pitchGeneratorService;

    /**
     * Analyze a startup idea - Primary endpoint
     * Supports both simple {"idea": "..."} and detailed {"title": "...", "description": "..."} formats
     */
    @PostMapping({"/analyze-idea", "/analyze"})
    @Operation(summary = "Analyze a startup idea")
    public ResponseEntity<AnalysisResponse> analyzeIdea(
            @Valid @RequestBody AnalyzeIdeaRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        
        // Log incoming request for debugging
        log.info("=== ANALYZE IDEA REQUEST ===");
        log.info("User ID: {}", user.getId());
        log.info("Request Body - idea: {}", request.getIdea());
        log.info("Request Body - title: {}", request.getTitle());
        log.info("Request Body - description: {}", request.getDescription());
        log.info("Request Body - targetMarket: {}", request.getTargetMarket());
        
        // Validate that we have either 'idea' or 'title'+'description'
        if (!request.hasValidInput()) {
            log.error("Invalid request: No valid input provided. Need 'idea' OR ('title' AND 'description')");
            throw new com.motif.ideaforge.exception.ValidationException(
                "Invalid request: Provide either 'idea' field OR both 'title' and 'description' fields",
                java.util.List.of(java.util.Map.of(
                    "field", "idea/title/description",
                    "message", "Provide either 'idea' field OR both 'title' and 'description' fields"
                ))
            );
        }
        
        log.info("Effective title: {}", request.getEffectiveTitle());
        log.info("Effective description: {} chars", 
                request.getEffectiveDescription() != null ? request.getEffectiveDescription().length() : 0);
        
        try {
            AnalysisResponse response = ideaAnalyzerService.analyzeIdea(user.getId(), request);
            log.info("=== ANALYZE IDEA SUCCESS === Score: {}", response.getScore());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("=== ANALYZE IDEA FAILED ===", e);
            throw e;
        }
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat with AI assistant")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("Processing chat for user: {}", user.getId());
        ChatResponse response = chatbotService.processMessage(user.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream chat with AI assistant (SSE)")
    public SseEmitter chatStream(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("Processing streaming chat for user: {}", user.getId());
        return chatbotService.streamMessage(user.getId(), request);
    }

    @PostMapping("/generate-idea")
    @Operation(summary = "Generate a startup idea")
    public ResponseEntity<IdeaResponse> generateIdea(
            @RequestBody(required = false) GenerateIdeaRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("Generating idea for user: {}", user.getId());
        IdeaResponse response = ideaGeneratorService.generateIdea(user.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/evaluate-case")
    @Operation(summary = "Evaluate a case study solution")
    public ResponseEntity<CaseEvaluationResponse> evaluateCase(
            @Valid @RequestBody EvaluateCaseRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("Evaluating case for user: {}", user.getId());
        CaseEvaluationResponse response = caseEvaluatorService.evaluateCase(user.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-pitch")
    @Operation(summary = "Generate a pitch deck")
    public ResponseEntity<PitchResponse> generatePitch(
            @Valid @RequestBody GeneratePitchRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("Generating pitch deck for user: {}", user.getId());
        PitchResponse response = pitchGeneratorService.generatePitch(request);
        return ResponseEntity.ok(response);
    }

    // ── Mentor Chat ───────────────────────────────────────────────────────────

    /**
     * Context-aware mentor chat (blocking).
     * Fetches the user's stored idea analysis, injects it into the system prompt,
     * and answers using ONLY that data as the source of truth.
     */
    @PostMapping("/mentor-chat")
    @Operation(summary = "Context-aware startup mentor chat")
    public ResponseEntity<ChatResponse> mentorChat(
            @Valid @RequestBody MentorChatRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("MentorChat for user: {}", user.getId());
        ChatResponse response = mentorChatService.mentorChat(user.getId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Context-aware mentor chat — streaming (SSE).
     * Same as /mentor-chat but tokens are pushed as Server-Sent Events.
     */
    @PostMapping(value = "/mentor-chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Context-aware startup mentor chat (SSE streaming)")
    public SseEmitter mentorChatStream(
            @Valid @RequestBody MentorChatRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("MentorChat/stream for user: {}", user.getId());
        return mentorChatService.mentorChatStream(user.getId(), request);
    }

    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/improve-description")
    @Operation(summary = "Improve a startup description")
    public ResponseEntity<ChatResponse> improveDescription(
            @Valid @RequestBody ImproveDescriptionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("Improving description for user: {}", user.getId());

        // Use chatbot service with specific prompt for description improvement
        String prompt = "Please improve this startup description to make it more compelling, clear, and professional. Keep it concise but impactful:\n\n" + request.getDescription();

        ChatMessageRequest chatRequest = ChatMessageRequest.builder()
                .message(prompt)
                .history(java.util.Collections.emptyList())
                .build();

        ChatResponse response = chatbotService.processMessage(user.getId(), chatRequest);
        return ResponseEntity.ok(response);
    }
}
