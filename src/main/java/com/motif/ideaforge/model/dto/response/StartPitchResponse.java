package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartPitchResponse {
    /** UUID string identifying this job. Pass to GET /api/pitch/status/{jobId}. */
    private String jobId;

    /** "PENDING" = new job started; "EXISTING" = reusing an active job for same user+idea. */
    private String status;

    private String message;
}
