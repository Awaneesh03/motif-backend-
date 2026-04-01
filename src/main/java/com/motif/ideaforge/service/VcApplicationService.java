package com.motif.ideaforge.service;

import com.motif.ideaforge.exception.InvalidStateException;
import com.motif.ideaforge.exception.RateLimitException;
import com.motif.ideaforge.exception.ResourceNotFoundException;
import com.motif.ideaforge.exception.UnauthorizedException;
import com.motif.ideaforge.model.ApplicationStatus;
import com.motif.ideaforge.model.dto.request.UpdateApplicationStatusRequest;
import com.motif.ideaforge.model.dto.response.PagedResponse;
import com.motif.ideaforge.model.dto.response.VcApplicationResponse;
import com.motif.ideaforge.model.dto.response.VcApplicationResponse.HistoryEntry;
import com.motif.ideaforge.model.entity.VcApplication;
import com.motif.ideaforge.model.entity.VcApplicationHistory;
import com.motif.ideaforge.repository.ProfileRepository;
import com.motif.ideaforge.repository.VcApplicationHistoryRepository;
import com.motif.ideaforge.repository.VcApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VcApplicationService {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final Set<String> VC_ROLES        = Set.of("vc", "admin", "super_admin");
    private static final int         DEFAULT_PAGE     = 0;
    private static final int         DEFAULT_SIZE     = 10;
    private static final int         MAX_SIZE         = 100;

    // Status-update rate limit: 5 per user per minute
    private static final int  STATUS_UPDATE_LIMIT_PER_MIN = 5;
    private static final long RATE_WINDOW_MS               = 60_000L; // 1 minute

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final VcApplicationRepository        vcApplicationRepository;
    private final VcApplicationHistoryRepository historyRepository;
    private final ProfileRepository              profileRepository;

    // ── Per-user status-update rate limiter ───────────────────────────────────
    // key: userId → deque of request timestamps (ms) within the last minute
    private final Map<UUID, Deque<Long>> statusUpdateLog = new ConcurrentHashMap<>();

    // ── Founder: my applications ──────────────────────────────────────────────

    /**
     * Returns the authenticated founder's own funding applications, paginated.
     * Enriched with idea title via LEFT JOIN.
     */
    public PagedResponse<VcApplicationResponse> getMyApplications(UUID founderId, int page, int size) {
        size   = clampSize(size);
        page   = Math.max(0, page);
        long offset = (long) page * size;

        log.debug("getMyApplications — founderId={}, page={}, size={}", founderId, page, size);

        List<Object[]> rows  = vcApplicationRepository.findPageByFounderId(founderId, size, offset);
        long           total = vcApplicationRepository.countByFounderId(founderId);

        return PagedResponse.of(rows.stream().map(this::mapRow).toList(), total, page, size);
    }

    // ── VC / Admin: pipeline ──────────────────────────────────────────────────

    /**
     * Returns all applications (VC/admin only), paginated and optionally filtered.
     * Sorted by updated_at DESC so most recently active appear first.
     */
    public PagedResponse<VcApplicationResponse> getAllApplications(
            UUID requestingUserId, String statusFilter, int page, int size) {

        requireVcOrAdminRole(requestingUserId);
        size   = clampSize(size);
        page   = Math.max(0, page);
        long offset = (long) page * size;

        // Validate status filter if provided
        String dbFilter = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            ApplicationStatus parsed = ApplicationStatus.fromString(statusFilter);
            dbFilter = parsed.toJson();
        }

        log.debug("getAllApplications — requestedBy={}, statusFilter={}, page={}, size={}",
                requestingUserId, dbFilter, page, size);

        List<Object[]> rows  = vcApplicationRepository.findPageAll(dbFilter, size, offset);
        long           total = vcApplicationRepository.countAll(dbFilter);

        return PagedResponse.of(rows.stream().map(this::mapRow).toList(), total, page, size);
    }

    /**
     * Updates status + optional notes on an application.
     *
     * Enforces:
     *   1. VC/admin role
     *   2. Per-user rate limit (5 updates / minute)
     *   3. Valid status transition (e.g. SUBMITTED → UNDER_REVIEW)
     *
     * Writes an audit row to vc_application_history on every change.
     */
    @Transactional
    public VcApplicationResponse updateStatus(UUID applicationId,
                                              UUID requestingUserId,
                                              UpdateApplicationStatusRequest req) {
        requireVcOrAdminRole(requestingUserId);
        checkStatusUpdateRateLimit(requestingUserId);

        VcApplication app = vcApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application not found: " + applicationId));

        ApplicationStatus current = ApplicationStatus.fromDb(app.getStatus());
        ApplicationStatus next    = req.getStatus();

        // Validate transition
        if (!ApplicationStatus.isValidTransition(current, next)) {
            throw new InvalidStateException(String.format(
                    "Cannot transition from '%s' to '%s'. Allowed next states: %s",
                    current != null ? current.toJson() : "unknown",
                    next.toJson(),
                    ApplicationStatus.allowedTransitions(current)
            ));
        }

        String oldStatus = app.getStatus();
        String newStatus = next.toJson();

        app.setStatus(newStatus);
        app.setReviewedAt(Instant.now());

        if (req.getVcNotes() != null) {
            app.setVcNotes(req.getVcNotes());
        }

        VcApplication saved = vcApplicationRepository.save(app);

        // Write audit trail entry
        historyRepository.save(VcApplicationHistory.builder()
                .applicationId(applicationId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(requestingUserId)
                .changedAt(Instant.now())
                .build());

        log.info("Status updated — id={}, {} → {}, by={}", applicationId, oldStatus, newStatus, requestingUserId);

        // Return the saved entity enriched with audit history
        List<HistoryEntry> history = buildHistory(applicationId);
        return toResponse(saved, null, history);
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

    /** Sliding-window rate limiter: max 5 status updates per user per minute. */
    private void checkStatusUpdateRateLimit(UUID userId) {
        long now         = Instant.now().toEpochMilli();
        long windowStart = now - RATE_WINDOW_MS;

        Deque<Long> timestamps = statusUpdateLog.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= STATUS_UPDATE_LIMIT_PER_MIN) {
                log.warn("Status-update rate limit exceeded — userId={}", userId);
                throw new RateLimitException(
                        "Too many status updates. Maximum " + STATUS_UPDATE_LIMIT_PER_MIN
                        + " updates per minute allowed. Please wait before trying again.");
            }
            timestamps.addLast(now);
        }
    }

    /** Purge stale entries from the per-minute rate limiter every 5 minutes. */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void evictStaleRateLimitEntries() {
        long windowStart = Instant.now().toEpochMilli() - RATE_WINDOW_MS;
        statusUpdateLog.entrySet().removeIf(entry -> {
            Deque<Long> ts = entry.getValue();
            synchronized (ts) {
                while (!ts.isEmpty() && ts.peekFirst() < windowStart) ts.pollFirst();
                return ts.isEmpty();
            }
        });
    }

    private int clampSize(int size) {
        if (size <= 0)    return DEFAULT_SIZE;
        if (size > MAX_SIZE) return MAX_SIZE;
        return size;
    }

    private List<HistoryEntry> buildHistory(UUID applicationId) {
        return historyRepository
                .findByApplicationIdOrderByChangedAtAsc(applicationId)
                .stream()
                .map(h -> HistoryEntry.builder()
                        .oldStatus(h.getOldStatus())
                        .newStatus(h.getNewStatus())
                        .changedBy(h.getChangedBy())
                        .changedAt(h.getChangedAt())
                        .build())
                .toList();
    }

    /**
     * Maps a native-query Object[] row to VcApplicationResponse.
     * Column order mirrors the SELECT in VcApplicationRepository:
     *   0=id, 1=vc_id, 2=founder_id, 3=idea_id, 4=status,
     *   5=vc_notes, 6=reviewed_at, 7=created_at, 8=updated_at, 9=idea_title
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

    private VcApplicationResponse toResponse(VcApplication app, String ideaTitle,
                                              List<HistoryEntry> history) {
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
                .history(history)
                .build();
    }

    // ── Type-conversion utilities ─────────────────────────────────────────────

    private UUID toUuid(Object o) {
        if (o == null) return null;
        if (o instanceof UUID u) return u;
        return UUID.fromString(o.toString());
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Instant toInstant(Object o) {
        if (o == null)                       return null;
        if (o instanceof Instant i)          return i;
        if (o instanceof Timestamp ts)       return ts.toInstant();
        if (o instanceof Date d)             return d.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(o.toString());
    }
}
