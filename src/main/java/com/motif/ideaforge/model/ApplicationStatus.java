package com.motif.ideaforge.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Set;

/**
 * Controlled lifecycle states for a VC funding application.
 *
 * Valid transitions:
 *   SUBMITTED   → UNDER_REVIEW
 *   UNDER_REVIEW → INTERESTED | REJECTED
 *   INTERESTED  → FUNDED | REJECTED
 *   REJECTED    → (terminal)
 *   FUNDED      → (terminal)
 *
 * All serialisation uses lowercase snake_case to match existing DB values.
 */
public enum ApplicationStatus {
    SUBMITTED,
    UNDER_REVIEW,
    INTERESTED,
    REJECTED,
    FUNDED;

    // ── Transition rules ──────────────────────────────────────────────────────

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS = Map.of(
            SUBMITTED,    Set.of(UNDER_REVIEW),
            UNDER_REVIEW, Set.of(INTERESTED, REJECTED),
            INTERESTED,   Set.of(FUNDED, REJECTED),
            REJECTED,     Set.of(),
            FUNDED,       Set.of()
    );

    /**
     * Returns true when transitioning from {@code current} to {@code next} is permitted.
     * Also returns true when {@code current} is null (new record, any status allowed).
     */
    public static boolean isValidTransition(ApplicationStatus current, ApplicationStatus next) {
        if (current == null) return true;
        Set<ApplicationStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        return allowed.contains(next);
    }

    /** Human-readable label for error messages. */
    public static String allowedTransitions(ApplicationStatus from) {
        if (from == null) return "any";
        Set<ApplicationStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Set.of());
        if (allowed.isEmpty()) return "none (terminal state)";
        return allowed.stream()
                .map(s -> s.toJson())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    // ── JSON serialisation ────────────────────────────────────────────────────

    /** Serialised as lowercase (e.g. "under_review") — matches DB values. */
    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    /**
     * Accepts both lowercase ("under_review") and uppercase ("UNDER_REVIEW").
     * Throws {@link IllegalArgumentException} on unknown values → mapped to HTTP 400
     * by GlobalExceptionHandler.
     */
    @JsonCreator
    public static ApplicationStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        String normalised = value.trim().toUpperCase();
        try {
            return ApplicationStatus.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid status '" + value + "'. Allowed values: submitted, under_review, interested, rejected, funded"
            );
        }
    }

    /** Convenience: parse a DB string value (nullable → null). */
    public static ApplicationStatus fromDb(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) return null;
        return fromString(dbValue);
    }
}
