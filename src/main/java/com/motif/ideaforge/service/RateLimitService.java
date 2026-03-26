package com.motif.ideaforge.service;

import com.motif.ideaforge.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter.
 *
 * Each (userId + endpoint-key) pair gets its own timestamp deque.
 * On each call we drop timestamps older than the window (1 hour), then
 * check whether the remaining count is below the configured limit.
 *
 * A scheduled task cleans up stale keys every 30 minutes to prevent
 * unbounded memory growth.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private static final long WINDOW_MS = 60 * 60 * 1000L; // 1 hour

    private final RateLimitProperties props;

    // key: "userId::endpointKey" → deque of request timestamps (ms)
    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    /**
     * Returns true if the request is allowed; false if the limit is exceeded.
     *
     * @param userId      the authenticated user's UUID string
     * @param endpointKey a short string identifying the endpoint category
     *                    (e.g. "ai-analyze", "ai-chat", "ai-generate")
     */
    public boolean isAllowed(String userId, String endpointKey) {
        if (!props.isEnabled()) return true;

        int limit = resolveLimit(endpointKey);
        if (limit <= 0) return true;  // 0 means unlimited

        String key = userId + "::" + endpointKey;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - WINDOW_MS;

        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Evict timestamps outside the sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= limit) {
                log.warn("Rate limit exceeded for user={} endpoint={} count={} limit={}",
                        userId, endpointKey, timestamps.size(), limit);
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    /** Returns how many requests remain in the current window (for headers). */
    public int remaining(String userId, String endpointKey) {
        int limit = resolveLimit(endpointKey);
        if (!props.isEnabled() || limit <= 0) return Integer.MAX_VALUE;

        String key = userId + "::" + endpointKey;
        long windowStart = Instant.now().toEpochMilli() - WINDOW_MS;
        Deque<Long> timestamps = requestLog.get(key);
        if (timestamps == null) return limit;

        synchronized (timestamps) {
            long used = timestamps.stream().filter(t -> t >= windowStart).count();
            return (int) Math.max(0, limit - used);
        }
    }

    private int resolveLimit(String endpointKey) {
        return switch (endpointKey) {
            case "ai-analyze"  -> props.getAiAnalyze();
            case "ai-generate" -> props.getAiGenerate();
            case "ai-chat"     -> props.getAiChat();
            case "idea-create" -> props.getIdeaCreate();
            case "idea-update" -> props.getIdeaUpdate();
            default            -> 0; // unknown key = unlimited
        };
    }

    /** Evict entries that have had no activity for longer than the window. */
    @Scheduled(fixedDelay = 30 * 60 * 1000L) // every 30 minutes
    public void evictStaleEntries() {
        long windowStart = Instant.now().toEpochMilli() - WINDOW_MS;
        int removed = 0;
        for (Map.Entry<String, Deque<Long>> entry : requestLog.entrySet()) {
            Deque<Long> ts = entry.getValue();
            synchronized (ts) {
                while (!ts.isEmpty() && ts.peekFirst() < windowStart) {
                    ts.pollFirst();
                }
                if (ts.isEmpty()) {
                    requestLog.remove(entry.getKey());
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.debug("Rate limiter evicted {} stale entries", removed);
        }
    }
}
