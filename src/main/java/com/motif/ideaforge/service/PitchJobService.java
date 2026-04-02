package com.motif.ideaforge.service;

import com.motif.ideaforge.exception.ResourceNotFoundException;
import com.motif.ideaforge.exception.UnauthorizedException;
import com.motif.ideaforge.model.ActivityType;
import com.motif.ideaforge.model.dto.request.GeneratePitchRequest;
import com.motif.ideaforge.model.dto.response.PitchJobStatusResponse;
import com.motif.ideaforge.model.dto.response.PitchResponse;
import com.motif.ideaforge.model.dto.response.StartPitchResponse;
import com.motif.ideaforge.model.job.PitchJob;
import com.motif.ideaforge.model.job.PitchJob.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages async pitch-generation jobs fully decoupled from HTTP request threads.
 *
 * Architecture mirrors AnalysisJobService:
 *  - jobs        → ConcurrentHashMap<jobId, PitchJob>      (in-memory job store)
 *  - dedupeIndex → ConcurrentHashMap<userId:ideaName, jobId> (prevents duplicate jobs)
 *  - pitchExecutor → fixed thread pool, daemon=false (survives client disconnect)
 *
 * Thread safety:
 *  PitchJob uses volatile fields; status is written last in mark* methods so any
 *  thread that observes COMPLETED/FAILED is guaranteed to see the result/errorMessage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PitchJobService {

    private final PitchGeneratorService pitchGeneratorService;
    private final ActivityService activityService;

    // ── In-memory stores ──────────────────────────────────────────────────────

    private final ConcurrentHashMap<String, PitchJob> jobs = new ConcurrentHashMap<>();

    /**
     * Dedupe index: "userId:ideaName" → jobId.
     * Only contains entries for PENDING or PROCESSING jobs.
     * Removed in the finally block of runPitch().
     */
    private final ConcurrentHashMap<String, String> dedupeIndex = new ConcurrentHashMap<>();

    // ── Dedicated thread pool — independent of HTTP threads ───────────────────

    private final ExecutorService pitchExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "pitch-worker-" + count.incrementAndGet());
            t.setDaemon(false);  // survive client disconnects
            return t;
        }
    });

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates and starts an async pitch job. Returns immediately.
     * Duplicate detection: if the same user already has an active job for the same
     * idea name, the existing jobId is returned instead of starting a new one.
     */
    public StartPitchResponse startJob(UUID userId, GeneratePitchRequest request) {
        String dedupeKey = userId + ":" + request.getIdeaName();

        String existingJobId = dedupeIndex.get(dedupeKey);
        if (existingJobId != null) {
            PitchJob existing = jobs.get(existingJobId);
            if (existing != null &&
                    (existing.getStatus() == JobStatus.PENDING || existing.getStatus() == JobStatus.PROCESSING)) {
                log.info("Reusing existing pitch job {} for user {} (idea: {})",
                        existingJobId, userId, request.getIdeaName());
                return StartPitchResponse.builder()
                        .jobId(existingJobId)
                        .status("EXISTING")
                        .message("Pitch generation already in progress — poll /api/pitch/status/" + existingJobId)
                        .build();
            }
        }

        String jobId = UUID.randomUUID().toString();
        PitchJob job = new PitchJob(jobId, userId, request.getIdeaName());
        jobs.put(jobId, job);
        dedupeIndex.put(dedupeKey, jobId);

        // Submit to dedicated executor — returns immediately
        pitchExecutor.submit(() -> runPitch(jobId, userId, request));

        log.info("Started pitch job {} for user {} (idea: {})", jobId, userId, request.getIdeaName());
        return StartPitchResponse.builder()
                .jobId(jobId)
                .status("PENDING")
                .message("Pitch generation started — poll /api/pitch/status/" + jobId)
                .build();
    }

    /**
     * Returns current status of a pitch job.
     * Only the user who created the job may poll it (prevents data leakage).
     */
    public PitchJobStatusResponse getJobStatus(String jobId, UUID userId) {
        PitchJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("Pitch job not found: " + jobId);
        }
        if (!job.getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorised to access pitch job: " + jobId);
        }

        return PitchJobStatusResponse.builder()
                .jobId(jobId)
                .status(job.getStatus().name())
                .result(job.getResult())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Runs on the dedicated pitch thread pool — completely independent of any
     * HTTP request. Client can disconnect; this method continues to completion.
     */
    private void runPitch(String jobId, UUID userId, GeneratePitchRequest request) {
        PitchJob job = jobs.get(jobId);
        if (job == null) {
            log.warn("Pitch job {} was removed before generation started — skipping", jobId);
            return;
        }

        job.markProcessing();
        log.info("Pitch job {} PROCESSING on thread {}", jobId, Thread.currentThread().getName());

        try {
            PitchResponse result = pitchGeneratorService.generatePitch(request);
            job.markCompleted(result);
            log.info("Pitch job {} COMPLETED — {} slides", jobId,
                    result.getSlides() != null ? result.getSlides().size() : 0);
            activityService.log(userId, ActivityType.PITCH_CREATED, request.getIdeaName());

        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "Pitch generation failed";
            job.markFailed(message);
            log.error("Pitch job {} FAILED: {}", jobId, message, e);

        } finally {
            // CAS remove: only removes if the value still matches this jobId
            String dedupeKey = userId + ":" + request.getIdeaName();
            dedupeIndex.remove(dedupeKey, jobId);
        }
    }

    /**
     * Removes jobs older than 2 hours to prevent unbounded memory growth.
     * Runs every 30 minutes.
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void cleanupOldJobs() {
        Instant cutoff = Instant.now().minus(2, ChronoUnit.HOURS);
        int[] removed = {0};

        jobs.entrySet().removeIf(entry -> {
            PitchJob job = entry.getValue();
            if (job.getCreatedAt().isBefore(cutoff)) {
                String dedupeKey = job.getUserId() + ":" + job.getIdeaName();
                dedupeIndex.remove(dedupeKey, entry.getKey());
                removed[0]++;
                return true;
            }
            return false;
        });

        if (removed[0] > 0) {
            log.info("Cleaned up {} expired pitch jobs", removed[0]);
        }
    }
}
