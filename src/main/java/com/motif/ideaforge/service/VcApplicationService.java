package com.motif.ideaforge.service;

import com.motif.ideaforge.exception.ResourceNotFoundException;
import com.motif.ideaforge.exception.UnauthorizedException;
import com.motif.ideaforge.model.dto.request.UpdateApplicationStatusRequest;
import com.motif.ideaforge.model.dto.response.VcApplicationResponse;
import com.motif.ideaforge.model.entity.VcApplication;
import com.motif.ideaforge.repository.ProfileRepository;
import com.motif.ideaforge.repository.VcApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VcApplicationService {

    private static final Set<String> VC_ROLES = Set.of("vc", "admin", "super_admin");

    private final VcApplicationRepository vcApplicationRepository;
    private final ProfileRepository       profileRepository;

    // ── Founder: my applications ──────────────────────────────────────────────

    /**
     * Returns the authenticated founder's own funding applications, newest first.
     * Enriched with the idea title via a LEFT JOIN on idea_analyses.
     */
    public List<VcApplicationResponse> getMyApplications(UUID founderId) {
        log.debug("getMyApplications — founderId={}", founderId);
        List<Object[]> rows = vcApplicationRepository.findWithIdeaTitleByFounderId(founderId);
        return rows.stream().map(this::mapRow).toList();
    }

    // ── VC / Admin: pipeline ──────────────────────────────────────────────────

    /**
     * Returns all applications (VC/admin only). Optionally filtered by status.
     * Throws UnauthorizedException if the caller is not VC/admin/super_admin.
     */
    public List<VcApplicationResponse> getAllApplications(UUID requestingUserId, String statusFilter) {
        requireVcOrAdminRole(requestingUserId);
        log.debug("getAllApplications — requestedBy={}, statusFilter={}", requestingUserId, statusFilter);
        List<Object[]> rows = vcApplicationRepository.findAllWithIdeaTitle(statusFilter);
        return rows.stream().map(this::mapRow).toList();
    }

    /**
     * Updates the status (and optional notes) on an application.
     * Throws UnauthorizedException if the caller is not VC/admin/super_admin.
     * Throws ResourceNotFoundException if the application does not exist.
     */
    @Transactional
    public VcApplicationResponse updateStatus(UUID applicationId,
                                              UUID requestingUserId,
                                              UpdateApplicationStatusRequest req) {
        requireVcOrAdminRole(requestingUserId);
        log.info("updateStatus — id={}, status={}, by={}", applicationId, req.getStatus(), requestingUserId);

        VcApplication app = vcApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application not found: " + applicationId));

        app.setStatus(req.getStatus());
        app.setReviewedAt(Instant.now());

        // Only overwrite notes if the caller actually sent something (allows status-only updates)
        if (req.getVcNotes() != null) {
            app.setVcNotes(req.getVcNotes());
        }

        VcApplication saved = vcApplicationRepository.save(app);
        return toResponse(saved, null /* ideaTitle not needed after update */);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireVcOrAdminRole(UUID userId) {
        String role = profileRepository.findById(userId)
                .map(p -> p.getRole())
                .orElse(null);

        if (role == null || !VC_ROLES.contains(role)) {
            log.warn("Access denied — userId={}, role={}", userId, role);
            throw new UnauthorizedException("VC or admin role required");
        }
    }

    /**
     * Maps a native-query Object[] row to a VcApplicationResponse.
     * Column order must match the SELECT in VcApplicationRepository.
     *
     * Index: 0=id, 1=vc_id, 2=founder_id, 3=idea_id, 4=status,
     *        5=vc_notes, 6=reviewed_at, 7=created_at, 8=updated_at, 9=idea_title
     */
    private VcApplicationResponse mapRow(Object[] row) {
        return VcApplicationResponse.builder()
                .id(toUuid(row[0]))
                .vcId(toUuid(row[1]))
                .founderId(toUuid(row[2]))
                .ideaId(toUuid(row[3]))
                .status(str(row[4]))
                .vcNotes(str(row[5]))
                .reviewedAt(toInstant(row[6]))
                .createdAt(toInstant(row[7]))
                .updatedAt(toInstant(row[8]))
                .ideaTitle(str(row[9]))
                .build();
    }

    private VcApplicationResponse toResponse(VcApplication app, String ideaTitle) {
        return VcApplicationResponse.builder()
                .id(app.getId())
                .vcId(app.getVcId())
                .founderId(app.getFounderId())
                .ideaId(app.getIdeaId())
                .status(app.getStatus())
                .vcNotes(app.getVcNotes())
                .reviewedAt(app.getReviewedAt())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .ideaTitle(ideaTitle)
                .build();
    }

    private UUID toUuid(Object o) {
        if (o == null) return null;
        if (o instanceof UUID u) return u;
        return UUID.fromString(o.toString());
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        return Instant.parse(o.toString());
    }
}
