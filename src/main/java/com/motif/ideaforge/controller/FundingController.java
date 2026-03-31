package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.request.FundingQualificationRequest;
import com.motif.ideaforge.model.dto.response.FundingQualificationResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.FundingQualificationService;
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
 * Funding qualification endpoints.
 *
 *   GET  /api/funding/qualification  → fetch saved profile (found=true) or empty (found=false)
 *   POST /api/funding/qualification  → upsert profile; returns the persisted state
 *
 * One row per user — no duplicate rows possible. Safe to call repeatedly.
 */
@RestController
@RequestMapping("/api/funding")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Funding", description = "Founder qualification for VC funding flow")
@SecurityRequirement(name = "Bearer Authentication")
public class FundingController {

    private final FundingQualificationService qualificationService;

    /**
     * Returns the caller's saved qualification profile.
     * Always returns 200:
     *   - found=true + fields populated  → returning user (pre-fill the form)
     *   - found=false + fields null      → first-time user (show empty form)
     */
    @GetMapping("/qualification")
    @Operation(summary = "Fetch the caller's saved funding qualification profile")
    public ResponseEntity<FundingQualificationResponse> getQualification(
            @AuthenticationPrincipal UserPrincipal user) {

        log.debug("Get qualification — user: {}", user.getId());
        FundingQualificationResponse response = qualificationService.getByUserId(user.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Upsert the caller's qualification profile.
     * Idempotent — safe to call on every "Next" click; no duplicate rows are created.
     * Returns the persisted state including server-side timestamps.
     */
    @PostMapping("/qualification")
    @Operation(summary = "Save or update the caller's funding qualification profile")
    public ResponseEntity<FundingQualificationResponse> upsertQualification(
            @Valid @RequestBody FundingQualificationRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        log.info("Upsert qualification — user: {}", user.getId());
        FundingQualificationResponse response = qualificationService.upsert(user.getId(), request);
        return ResponseEntity.ok(response);
    }
}
