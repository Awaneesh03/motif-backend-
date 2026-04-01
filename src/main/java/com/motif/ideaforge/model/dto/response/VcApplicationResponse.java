package com.motif.ideaforge.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a vc_applications row, enriched with idea title + audit history.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VcApplicationResponse {

    private UUID   id;
    private UUID   vcId;
    private UUID   founderId;
    private UUID   ideaId;

    /** Human-readable title from idea_analyses, null if no idea linked. */
    private String ideaTitle;

    /**
     * Lifecycle status (lowercase snake_case).
     * Values: submitted | under_review | interested | rejected | funded
     * Legacy VC intro-request values: pending | accepted
     */
    private String status;

    /** Notes added by the reviewing VC or admin. */
    private String vcNotes;

    private Instant reviewedAt;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Ordered audit trail for this application (oldest → newest).
     * Populated only when explicitly requested; null otherwise.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<HistoryEntry> history;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HistoryEntry {
        private String  oldStatus;
        private String  newStatus;
        private UUID    changedBy;
        private Instant changedAt;
    }
}
