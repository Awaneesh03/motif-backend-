package com.motif.ideaforge.model;

/**
 * Canonical activity types shared across all backend services.
 * Each enum constant carries the exact string value persisted in
 * {@code user_activity.type} — keeping DB values stable even if Java names change.
 *
 * These values MUST match the {@code ActivityType} union in the frontend
 * {@code activityService.ts} and the string literals read by
 * {@code FounderScoreService}.
 */
public enum ActivityType {

    IDEA_ANALYZED("idea_analyzed"),
    PITCH_CREATED("pitch_created"),
    FUNDING_SUBMITTED("funding_submitted"),
    CASE_VIEWED("case_viewed"),
    COMMUNITY_ACTION("community_action"),
    PROFILE_UPDATED("profile_updated");

    private final String value;

    ActivityType(String value) {
        this.value = value;
    }

    /** The string persisted in {@code user_activity.type}. */
    public String getValue() {
        return value;
    }
}
