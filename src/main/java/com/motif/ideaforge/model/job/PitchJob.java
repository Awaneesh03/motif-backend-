package com.motif.ideaforge.model.job;

import com.motif.ideaforge.model.dto.response.PitchResponse;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory representation of an async pitch-generation job.
 *
 * Thread-safety: identical pattern to AnalysisJob.
 *  - Immutable fields set once at construction.
 *  - Mutable fields are volatile; status written LAST so any thread
 *    that observes COMPLETED/FAILED is guaranteed to see a non-null result.
 */
@Getter
public class PitchJob {

    private final String jobId;
    private final UUID userId;
    private final String ideaName;   // used for duplicate detection
    private final Instant createdAt;

    private volatile JobStatus status;
    private volatile PitchResponse result;
    private volatile String errorMessage;
    private volatile Instant completedAt;

    public PitchJob(String jobId, UUID userId, String ideaName) {
        this.jobId = jobId;
        this.userId = userId;
        this.ideaName = ideaName;
        this.status = JobStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markProcessing() {
        this.status = JobStatus.PROCESSING;
    }

    public void markCompleted(PitchResponse result) {
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
