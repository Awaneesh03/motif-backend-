package com.motif.ideaforge.model.job;

import com.motif.ideaforge.model.dto.response.AnalysisResponse;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory representation of an async analysis job.
 *
 * Thread-safety design:
 *  - Immutable fields (jobId, userId, ideaTitle, createdAt) are set once at construction.
 *  - Mutable fields (status, result, errorMessage, completedAt) are volatile so that
 *    writes from the analysis executor thread are immediately visible to HTTP poll threads.
 *  - Status is always written LAST in markCompleted / markFailed so that a reader that
 *    sees COMPLETED/FAILED is guaranteed to also see a non-null result/errorMessage.
 */
@Getter
public class AnalysisJob {

    private final String jobId;
    private final UUID userId;
    private final String ideaTitle;   // used for duplicate detection
    private final Instant createdAt;

    private volatile JobStatus status;
    private volatile AnalysisResponse result;
    private volatile String errorMessage;
    private volatile Instant completedAt;

    public AnalysisJob(String jobId, UUID userId, String ideaTitle) {
        this.jobId = jobId;
        this.userId = userId;
        this.ideaTitle = ideaTitle;
        this.status = JobStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markProcessing() {
        this.status = JobStatus.PROCESSING;
    }

    /** Write result and completedAt BEFORE status so readers see a consistent snapshot. */
    public void markCompleted(AnalysisResponse result) {
        this.result = result;
        this.completedAt = Instant.now();
        this.status = JobStatus.COMPLETED;   // visibility fence — write last
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
        this.status = JobStatus.FAILED;      // visibility fence — write last
    }

    public enum JobStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
