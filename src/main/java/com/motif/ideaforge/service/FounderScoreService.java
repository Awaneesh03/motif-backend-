package com.motif.ideaforge.service;

import com.motif.ideaforge.model.dto.response.FounderScoreResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FounderScoreService {

    @PersistenceContext
    private EntityManager em;

    // ── Public API ────────────────────────────────────────────────────────────

    public FounderScoreResponse calculateScore(UUID userId) {
        Object[]       profile    = fetchProfile(userId);
        List<Object[]> activities = fetchActivities(userId);

        int profilePts     = scoreProfile(profile);
        int activityPts    = scoreActivity(activities);
        int consistencyPts = scoreConsistency(activities);
        int engagementPts  = scoreEngagement(activities);

        int total = Math.min(100, profilePts + activityPts + consistencyPts + engagementPts);

        return FounderScoreResponse.builder()
                .score(total)
                .level(computeLevel(total))
                .breakdown(FounderScoreResponse.Breakdown.builder()
                        .profile(profilePts)
                        .activity(activityPts)
                        .consistency(consistencyPts)
                        .engagement(engagementPts)
                        .build())
                .build();
    }

    // ── Data fetching ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object[] fetchProfile(UUID userId) {
        try {
            // Returns [name, about, linkedin, education, location]
            return (Object[]) em.createNativeQuery(
                    "SELECT name, about, linkedin, education, location " +
                    "FROM public.profiles WHERE id = :id")
                    .setParameter("id", userId)
                    .getSingleResult();
        } catch (Exception e) {
            log.warn("[FounderScore] Could not fetch profile for {}: {}", userId, e.getMessage());
            return new Object[5];
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> fetchActivities(UUID userId) {
        try {
            // Returns [type, created_at]
            return em.createNativeQuery(
                    "SELECT type, created_at FROM public.user_activity " +
                    "WHERE user_id = :userId ORDER BY created_at DESC")
                    .setParameter("userId", userId)
                    .getResultList();
        } catch (Exception e) {
            log.warn("[FounderScore] Could not fetch activities for {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ── Scoring: Profile Quality (max 40) ─────────────────────────────────────

    private int scoreProfile(Object[] row) {
        if (row == null) return 0;
        int pts = 0;

        // name → 5
        if (nonEmpty(row, 0)) pts += 5;

        // about: <20→0, 20–80→5, 80–200→10, 200+→15
        String about = str(row, 1);
        if (about.length() >= 200)      pts += 15;
        else if (about.length() >= 80)  pts += 10;
        else if (about.length() >= 20)  pts += 5;

        // linkedin: exists→5, valid URL→+5
        String linkedin = str(row, 2);
        if (!linkedin.isEmpty()) {
            pts += 5;
            if (linkedin.contains("linkedin.com")) pts += 5;
        }

        // education → 5
        if (nonEmpty(row, 3)) pts += 5;

        // location → 5
        if (nonEmpty(row, 4)) pts += 5;

        return Math.min(40, pts);
    }

    // ── Scoring: Activity Signals (max 30) ───────────────────────────────────

    private int scoreActivity(List<Object[]> activities) {
        long ideaCount    = countType(activities, "idea_analyzed");
        long pitchCount   = countType(activities, "pitch_created");
        long fundingCount = countType(activities, "funding_submitted");

        int pts = 0;

        // idea_analyzed: 0→0, 1–3→5, 4–10→10, 10+→15
        if (ideaCount >= 10)     pts += 15;
        else if (ideaCount >= 4) pts += 10;
        else if (ideaCount >= 1) pts += 5;

        // pitch_created: ≥1→5
        if (pitchCount >= 1) pts += 5;

        // funding_submitted: ≥1→10
        if (fundingCount >= 1) pts += 10;

        return Math.min(30, pts);
    }

    // ── Scoring: Consistency (max 20) ────────────────────────────────────────

    private int scoreConsistency(List<Object[]> activities) {
        if (activities.isEmpty()) return 0;

        Instant now         = Instant.now();
        Instant sevenDaysAgo  = now.minus(7,  ChronoUnit.DAYS);
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        boolean activeIn7  = activities.stream().anyMatch(r -> toInstant(r[1]).isAfter(sevenDaysAgo));
        boolean activeIn30 = activities.stream().anyMatch(r -> toInstant(r[1]).isAfter(thirtyDaysAgo));

        int pts = 0;
        if (activeIn7)  pts += 10;
        if (activeIn30) pts += 10;
        return pts;
    }

    // ── Scoring: Engagement Depth (max 10) ───────────────────────────────────

    private int scoreEngagement(List<Object[]> activities) {
        if (activities.isEmpty()) return 0;

        long uniqueTypes = activities.stream()
                .map(r -> r[0] != null ? r[0].toString() : "")
                .distinct()
                .filter(t -> !t.isEmpty())
                .count();

        // Any single type appears ≥ 2 times → repeated usage
        boolean hasRepeatedUsage = activities.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r[0] != null ? r[0].toString() : "",
                        java.util.stream.Collectors.counting()))
                .values().stream()
                .anyMatch(count -> count >= 2);

        int pts = 0;
        if (uniqueTypes >= 3)  pts += 5;
        if (hasRepeatedUsage)  pts += 5;
        return pts;
    }

    // ── Level labels ──────────────────────────────────────────────────────────

    private static String computeLevel(int score) {
        if (score >= 81) return "Investor Ready";
        if (score >= 61) return "Strong";
        if (score >= 31) return "Active";
        return "Beginner";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String str(Object[] row, int i) {
        return (row != null && row.length > i && row[i] != null)
                ? row[i].toString().trim() : "";
    }

    private static boolean nonEmpty(Object[] row, int i) {
        return !str(row, i).isEmpty();
    }

    private static long countType(List<Object[]> activities, String type) {
        return activities.stream()
                .filter(r -> r[0] != null && type.equals(r[0].toString()))
                .count();
    }

    private static Instant toInstant(Object val) {
        if (val == null) return Instant.EPOCH;
        try {
            if (val instanceof java.sql.Timestamp ts) return ts.toInstant();
            if (val instanceof java.time.OffsetDateTime odt) return odt.toInstant();
            return Instant.parse(val.toString());
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }
}
