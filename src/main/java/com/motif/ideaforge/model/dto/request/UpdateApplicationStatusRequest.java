package com.motif.ideaforge.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for PUT /api/vc/applications/{id}/status
 */
@Data
public class UpdateApplicationStatusRequest {

    /**
     * New status value.
     * Allowed: submitted | under_review | interested | rejected
     */
    @NotBlank(message = "status is required")
    @Pattern(
        regexp = "submitted|under_review|interested|rejected",
        message = "status must be one of: submitted, under_review, interested, rejected"
    )
    private String status;

    /** Optional reviewer notes — can be updated independently of status. */
    private String vcNotes;
}
