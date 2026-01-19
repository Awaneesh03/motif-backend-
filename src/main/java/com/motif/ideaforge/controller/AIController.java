package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.request.AnalyzeIdeaRequest;
import com.motif.ideaforge.model.dto.request.ChatMessageRequest;
import com.motif.ideaforge.model.dto.request.GenerateIdeaRequest;
import com.motif.ideaforge.model.dto.request.EvaluateCaseRequest;
import com.motif.ideaforge.model.dto.response.AnalysisResponse;
import com.motif.ideaforge.model.dto.response.ChatResponse;
import com.motif.ideaforge.model.dto.response.IdeaResponse;
import com.motif.ideaforge.model.dto.response.CaseEvaluationResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.ai.ChatbotService;
import com.motif.ideaforge.service.ai.IdeaAnalyzerService;
import com.motif.ideaforge.service.ai.IdeaGeneratorService;
import com.motif.ideaforge.service.ai.CaseEvaluatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Services", description = "AI-powered endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AIController {

    private final IdeaAnalyzerService ideaAnalyzerService;
    private final ChatbotService chatbotService;
    private final IdeaGeneratorService ideaGeneratorService;
    private final CaseEvaluatorService caseEvaluatorService;

    @PostMapping("/analyze-idea")
    @Operation(summary = "Analyze a startup idea")
    public ResponseEntity<AnalysisResponse> analyzeIdea(
            @Valid @RequestBody AnalyzeIdeaRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        log.info("Analyzing idea for user: {}", user.getId());
        AnalysisResponse response = ideaAnalyzerService.analyzeIdea(user.getId(), request);
        return ResponseEntity.ok(response);
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
}
