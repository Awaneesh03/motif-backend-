package com.motif.ideaforge.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a vc_applications row, enriched with the idea title.
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
     * Lifecycle status.
     * Founder-submitted:  submitted | under_review | interested | rejected
     * Legacy VC requests: pending   | accepted     | rejected
     */
    private String status;

    /** Notes added by the reviewing VC or admin. */
    private String vcNotes;

    private Instant reviewedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
