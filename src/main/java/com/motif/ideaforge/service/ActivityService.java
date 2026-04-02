package com.motif.ideaforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.model.ActivityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Centralized, fire-and-forget activity logger.
 *
 * <p>Design guarantees:
 * <ul>
 *   <li>{@code REQUIRES_NEW} — runs in its own transaction, completely independent
 *       of the caller's transaction. A rollback in the caller will NOT undo the
 *       activity log, and a failure here will NOT roll back the main action.</li>
 *   <li>ON CONFLICT DO NOTHING — the unique index on (user_id, dedup_key, dedup_bucket)
 *       makes every insert idempotent within a 10-second window.</li>
 *   <li>Never throws — all exceptions are swallowed and logged as WARN.</li>
 * </ul>
 *
 * <p>Inject via {@code @RequiredArgsConstructor} in any Spring-managed service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Logs a user activity event. Always returns — never throws.
     *
     * @param userId   the authenticated user's UUID
     * @param type     activity type (enum value maps directly to the DB string)
     * @param title    human-readable title; defaults to {@code type.getValue()} if blank
     * @param metadata optional JSON metadata (score, stage, etc.) — may be null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, ActivityType type, String title, Map<String, Object> metadata) {
        if (userId == null) {
            log.warn("[ActivityService] userId is null — skipping log for type={}", type);
            return;
        }

        try {
            String resolvedTitle = (title != null && !title.isBlank())
                    ? title.trim()
                    : type.getValue();
            String dedupKey    = type.getValue() + ":" + resolvedTitle;
            int    dedupBucket = (int) (System.currentTimeMillis() / 10_000L);
            String metaJson    = toJson(metadata);

            // ON CONFLICT DO NOTHING — unique index (user_id, dedup_key, dedup_bucket)
            // makes this INSERT idempotent within any 10-second window.
            em.createNativeQuery(
                    "INSERT INTO public.user_activity " +
                    "  (id, user_id, type, title, metadata, dedup_key, dedup_bucket) " +
                    "VALUES " +
                    "  (gen_random_uuid(), :userId, :type, :title, " +
                    "   CAST(:metadata AS jsonb), :dedupKey, :dedupBucket) " +
                    "ON CONFLICT (user_id, dedup_key, dedup_bucket) DO NOTHING")
                    .setParameter("userId",      userId)
                    .setParameter("type",        type.getValue())
                    .setParameter("title",       resolvedTitle)
                    .setParameter("metadata",    metaJson)
                    .setParameter("dedupKey",    dedupKey)
                    .setParameter("dedupBucket", dedupBucket)
                    .executeUpdate();

            log.debug("[ActivityService] logged type={} title='{}' user={}", type, resolvedTitle, userId);

        } catch (Exception e) {
            log.warn("[ActivityService] failed to log type={} user={}: {}", type, userId, e.getMessage());
            // Intentionally swallowed — activity logging must never break the main flow
        }
    }

    /** Convenience overload for events without metadata. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, ActivityType type, String title) {
        log(userId, type, title, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[ActivityService] failed to serialize metadata: {}", e.getMessage());
            return null;
        }
    }
}
