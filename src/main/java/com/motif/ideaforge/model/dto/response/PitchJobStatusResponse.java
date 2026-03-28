package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitchJobStatusResponse {
    private String jobId;

    /** PENDING | PROCESSING | COMPLETED | FAILED */
    private String status;

    /** Non-null when status = COMPLETED. */
    private PitchResponse result;

    /** Non-null when status = FAILED. */
    private String errorMessage;

    private Instant createdAt;

    /** Non-null when status = COMPLETED or FAILED. */
    private Instant completedAt;
}
