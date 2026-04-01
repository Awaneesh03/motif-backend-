package com.motif.ideaforge.model.dto.request;

import com.motif.ideaforge.model.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for PUT /api/vc/applications/{id}/status
 *
 * Jackson deserialises `status` using ApplicationStatus.fromString(),
 * which accepts both "under_review" and "UNDER_REVIEW" and rejects
 * unknown values with a clear 400 message before this even reaches the service.
 */
@Data
public class UpdateApplicationStatusRequest {

    /**
     * Target lifecycle status.
     * Accepted values: submitted | under_review | interested | rejected | funded
     */
    @NotNull(message = "status is required")
    private ApplicationStatus status;

    /** Optional reviewer notes — can be updated alongside or independently of status. */
    private String vcNotes;
}
