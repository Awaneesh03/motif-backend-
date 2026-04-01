package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.request.UpdateApplicationStatusRequest;
import com.motif.ideaforge.model.dto.response.VcApplicationResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.VcApplicationService;
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
import java.util.UUID;

/**
 * VC / Admin endpoints for the funding pipeline.
 *
 *   GET /api/vc/applications               → all applications (VC/admin only)
 *   PUT /api/vc/applications/{id}/status   → update status + notes (VC/admin only)
 *
 * Role check is enforced in VcApplicationService by querying the profiles table.
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
     * Returns all founder funding applications.
     * Optional ?status= query param filters by lifecycle status.
     * Requires: vc | admin | super_admin role.
     */
    @GetMapping("/applications")
    @Operation(summary = "List all funding applications (VC/admin only)")
    public ResponseEntity<List<VcApplicationResponse>> getAllApplications(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String status) {

        log.debug("getAllApplications — requestedBy={}, statusFilter={}", user.getId(), status);
        List<VcApplicationResponse> apps = vcApplicationService.getAllApplications(user.getId(), status);
        return ResponseEntity.ok(apps);
    }

    /**
     * Update the status and optional notes on a single application.
     * Sets reviewed_at to NOW() automatically.
     * Requires: vc | admin | super_admin role.
     */
    @PutMapping("/applications/{id}/status")
    @Operation(summary = "Update application status and VC notes (VC/admin only)")
    public ResponseEntity<VcApplicationResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationStatusRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        log.info("updateStatus — applicationId={}, status={}, by={}", id, request.getStatus(), user.getId());
        VcApplicationResponse updated = vcApplicationService.updateStatus(id, user.getId(), request);
        return ResponseEntity.ok(updated);
    }
}
