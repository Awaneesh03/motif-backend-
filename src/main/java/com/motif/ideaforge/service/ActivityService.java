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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

    // ── Metadata schema per type ──────────────────────────────────────────────
    // Defines the ONLY keys allowed in each event's metadata JSONB.
    // Unknown keys are stripped before insert — keeps the schema documented in
    // code and prevents unbounded metadata growth from misconfigured callers.

    private static final Map<ActivityType, Set<String>> ALLOWED_META_KEYS;

    static {
        Map<ActivityType, Set<String>> m = new java.util.EnumMap<>(ActivityType.class);
        m.put(ActivityType.IDEA_ANALYZED,     Set.of("score", "jobId"));
        m.put(ActivityType.PITCH_CREATED,     Set.of("jobId"));
        m.put(ActivityType.FUNDING_SUBMITTED, Set.of("stage", "fundingAmount"));
        m.put(ActivityType.CASE_VIEWED,       Set.of("score"));
        m.put(ActivityType.COMMUNITY_ACTION,  Set.of("action"));
        m.put(ActivityType.PROFILE_UPDATED,   Set.of());
        ALLOWED_META_KEYS = Collections.unmodifiableMap(m);
    }

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

            // Strip metadata keys not in the allowed set for this type.
            // Prevents schema drift and metadata bloat from misconfigured callers.
            Map<String, Object> sanitized = sanitizeMetadata(type, metadata);
            String metaJson = toJson(sanitized);

            // ON CONFLICT DO NOTHING — unique index (user_id, dedup_key, dedup_bucket)
            // makes this INSERT idempotent within any 10-second window.
            int rows = em.createNativeQuery(
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

            if (rows > 0) {
                log.info("[ActivityService] logged type={} title='{}' user={}", type, resolvedTitle, userId);
            } else {
                log.debug("[ActivityService] skipped duplicate type={} user={}", type, userId);
            }

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

    /**
     * Returns a copy of {@code metadata} containing only the keys declared in
     * {@link #ALLOWED_META_KEYS} for the given type.
     * Returns {@code null} if the type has no allowed keys or nothing survives.
     * Unknown keys are logged at DEBUG so callers can be corrected over time.
     */
    private Map<String, Object> sanitizeMetadata(ActivityType type, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;

        Set<String> allowed = ALLOWED_META_KEYS.getOrDefault(type, Set.of());
        if (allowed.isEmpty()) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                out.put(entry.getKey(), entry.getValue());
            } else {
                log.debug("[ActivityService] stripped unexpected metadata key='{}' for type={}", entry.getKey(), type);
            }
        }
        return out.isEmpty() ? null : out;
    }

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
