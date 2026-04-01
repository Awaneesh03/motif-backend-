package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.request.UpdateApplicationStatusRequest;
import com.motif.ideaforge.model.dto.response.PagedResponse;
import com.motif.ideaforge.model.dto.response.VcApplicationResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.VcApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * VC / Admin endpoints for the funding pipeline.
 *
 *   GET /api/vc/applications               → all applications, paginated (VC/admin only)
 *   PUT /api/vc/applications/{id}/status   → update status + notes (VC/admin only)
 *
 * Role check, transition validation, and rate limiting are enforced in VcApplicationService.
 */
@RestController
@RequestMapping("/api/vc")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "VC Pipeline", description = "VC/Admin endpoints for reviewing funding applications")
@SecurityRequirement(name = "Bearer Authentication")
public class VcApplicationController {

    private final VcApplicationService vcApplicationService;

    /**
     * Returns all founder funding applications, paginated.
     * Optional ?status= filter; optional ?page= and ?size= pagination.
     * Requires: vc | admin | super_admin role.
     */
    @GetMapping("/applications")
    @Operation(summary = "List all funding applications, paginated (VC/admin only)")
    public ResponseEntity<PagedResponse<VcApplicationResponse>> getAllApplications(
            @AuthenticationPrincipal UserPrincipal user,
            @Parameter(description = "Filter by status: submitted|under_review|interested|rejected|funded")
            @RequestParam(required = false) String status,
            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, max 100 (default 10)")
            @RequestParam(defaultValue = "10") int size) {

        log.debug("getAllApplications — requestedBy={}, status={}, page={}, size={}",
                user.getId(), status, page, size);
        return ResponseEntity.ok(
                vcApplicationService.getAllApplications(user.getId(), status, page, size));
    }

    /**
     * Updates status and optional notes on a single application.
     * Enforces valid transition rules. Sets reviewed_at automatically.
     * Rate limited: 5 updates/minute per user.
     * Requires: vc | admin | super_admin role.
     */
    @PutMapping("/applications/{id}/status")
    @Operation(summary = "Update application status and VC notes (VC/admin only)")
    public ResponseEntity<VcApplicationResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationStatusRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        log.info("updateStatus — applicationId={}, status={}, by={}",
                id, request.getStatus(), user.getId());
        return ResponseEntity.ok(
                vcApplicationService.updateStatus(id, user.getId(), request));
    }
}
