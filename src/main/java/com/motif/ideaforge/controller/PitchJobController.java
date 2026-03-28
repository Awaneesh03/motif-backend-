package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.request.GeneratePitchRequest;
import com.motif.ideaforge.model.dto.response.PitchJobStatusResponse;
import com.motif.ideaforge.model.dto.response.StartPitchResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.PitchJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Job-based async pitch generation endpoints.
 *
 * Flow:
 *   1. POST /api/pitch/start  → returns { jobId, status } immediately (202 Accepted)
 *   2. GET  /api/pitch/status/{jobId}  → poll until status = COMPLETED or FAILED
 *
 * The OpenAI call runs on a dedicated background thread pool inside PitchJobService
 * and is completely independent of the HTTP request lifecycle.
 */
@RestController
@RequestMapping("/api/pitch")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Async Pitch", description = "Job-based async pitch deck generation")
@SecurityRequirement(name = "Bearer Authentication")
public class PitchJobController {

    private final PitchJobService pitchJobService;

    /**
     * Start an async pitch generation job.
     * Returns 202 Accepted with a jobId immediately — the OpenAI call has NOT completed yet.
     */
    @PostMapping("/start")
    @Operation(summary = "Start async pitch generation — returns jobId immediately")
    public ResponseEntity<StartPitchResponse> startPitch(
            @Valid @RequestBody GeneratePitchRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        log.info("Start pitch request from user {}, idea: {}", user.getId(), request.getIdeaName());
        StartPitchResponse response = pitchJobService.startJob(user.getId(), request);
        return ResponseEntity.accepted().body(response);  // 202 Accepted
    }

    /**
     * Poll the status of a pitch generation job.
     * Returns 200 with { status: PENDING|PROCESSING|COMPLETED|FAILED, result: {...} }.
     * result is populated only when status = COMPLETED.
     */
    @GetMapping("/status/{jobId}")
    @Operation(summary = "Poll pitch job status")
    public ResponseEntity<PitchJobStatusResponse> getStatus(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserPrincipal user) {

        log.debug("Pitch status poll — job: {}, user: {}", jobId, user.getId());
        PitchJobStatusResponse response = pitchJobService.getJobStatus(jobId, user.getId());
        return ResponseEntity.ok(response);
    }
}
