package com.motif.ideaforge.controller;

import com.motif.ideaforge.exception.ValidationException;
import com.motif.ideaforge.model.dto.request.AnalyzeIdeaRequest;
import com.motif.ideaforge.model.dto.response.JobStatusResponse;
import com.motif.ideaforge.model.dto.response.StartAnalysisResponse;
import com.motif.ideaforge.model.dto.response.SubmitReviewResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.AnalysisJobService;
import com.motif.ideaforge.service.ai.IdeaAnalyzerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Job-based async analysis endpoints.
 *
 * Flow:
 *   1. POST /api/analysis/start  → returns { jobId, status } immediately
 *   2. GET  /api/analysis/status/{jobId}  → poll until status = COMPLETED or FAILED
 *
 * The OpenAI call runs on a dedicated background thread pool inside AnalysisJobService
 * and is completely independent of the HTTP request lifecycle.
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Async Analysis", description = "Job-based async idea analysis")
@SecurityRequirement(name = "Bearer Authentication")
public class AnalysisJobController {

    private final AnalysisJobService analysisJobService;
    private final IdeaAnalyzerService ideaAnalyzerService;

    /**
     * Start an async analysis job.
     * Returns 202 Accepted with a jobId immediately — the OpenAI call has NOT completed yet.
     */
    @PostMapping("/start")
    @Operation(summary = "Start async idea analysis — returns jobId immediately")
    public ResponseEntity<StartAnalysisResponse> startAnalysis(
            @Valid @RequestBody AnalyzeIdeaRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        if (!request.hasValidInput()) {
            throw new ValidationException(
                "Invalid request: Provide either 'idea' field OR both 'title' and 'description' fields",
                List.of(Map.of(
                    "field", "idea/title/description",
                    "message", "Provide either 'idea' OR both 'title' + 'description'"
                ))
            );
        }

        log.info("Start analysis request from user {}, title: {}", user.getId(), request.getEffectiveTitle());
        StartAnalysisResponse response = analysisJobService.startJob(user.getId(), request);
        return ResponseEntity.accepted().body(response);  // 202 Accepted
    }

    /**
     * Poll the status of an analysis job.
     * Returns 200 with { status: PENDING|PROCESSING|COMPLETED|FAILED, result: {...} }.
     * result is populated only when status = COMPLETED.
     */
    @GetMapping("/status/{jobId}")
    @Operation(summary = "Poll analysis job status")
    public ResponseEntity<JobStatusResponse> getStatus(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserPrincipal user) {

        log.debug("Status poll — job: {}, user: {}", jobId, user.getId());
        JobStatusResponse response = analysisJobService.getJobStatus(jobId, user.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Submit a saved analysis for admin review.
     * Valid transition: draft → pending_review.
     * Returns 200 with { ideaId, status, submittedAt }.
     * Returns 404 if the idea is not found or does not belong to the caller.
     * Returns 400 if the current status does not allow submission.
     */
    @PatchMapping("/{ideaId}/submit")
    @Operation(summary = "Submit a specific analyzed idea for admin review")
    public ResponseEntity<SubmitReviewResponse> submitForReview(
            @PathVariable UUID ideaId,
            @AuthenticationPrincipal UserPrincipal user) {

        log.info("Submit for review — ideaId: {}, user: {}", ideaId, user.getId());
        SubmitReviewResponse response = ideaAnalyzerService.submitForReview(ideaId, user.getId());
        return ResponseEntity.ok(response);
    }
}
