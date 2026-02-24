package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response returned by PATCH /api/analysis/{id}/submit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitReviewResponse {

    /** The UUID of the submitted idea. */
    private String ideaId;

    /** New status — always "pending_review" on success. */
    private String status;

    /** Server-side timestamp of when the submission was recorded. */
    private Instant submittedAt;
}
