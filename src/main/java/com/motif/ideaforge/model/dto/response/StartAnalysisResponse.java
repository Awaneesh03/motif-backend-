package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartAnalysisResponse {
    /** UUID string identifying this job. Pass to GET /api/analysis/status/{jobId}. */
    private String jobId;

    /** "PENDING" = new job started; "EXISTING" = reusing an active job for same user+title. */
    private String status;

    private String message;
}
