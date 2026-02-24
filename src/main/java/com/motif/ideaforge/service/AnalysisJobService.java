package com.motif.ideaforge.service;

import com.motif.ideaforge.exception.ResourceNotFoundException;
import com.motif.ideaforge.exception.UnauthorizedException;
import com.motif.ideaforge.model.dto.request.AnalyzeIdeaRequest;
import com.motif.ideaforge.model.dto.response.AnalysisResponse;
import com.motif.ideaforge.model.dto.response.JobStatusResponse;
import com.motif.ideaforge.model.dto.response.StartAnalysisResponse;
import com.motif.ideaforge.model.job.AnalysisJob;
import com.motif.ideaforge.model.job.AnalysisJob.JobStatus;
import com.motif.ideaforge.service.ai.IdeaAnalyzerService;
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
 * Manages async idea-analysis jobs that are fully decoupled from HTTP request threads.
 *
 * Architecture:
 *  - jobs          → ConcurrentHashMap<jobId, AnalysisJob>  (in-memory job store)
 *  - dedupeIndex   → ConcurrentHashMap<userId:title, jobId> (prevents duplicate jobs)
 *  - analysisExecutor → fixed thread pool of 4 daemon=false threads, completely
 *                        independent of Tomcat's request thread pool.
 *
 * Why daemon=false?
 *  The JVM will NOT exit while a non-daemon thread is alive. This ensures that
 *  an analysis task that is already running will finish even if the web server
 *  starts shutting down (gives it a short grace period).
 *
 * Thread safety:
 *  AnalysisJob uses volatile fields; status is always written last in mark* methods
 *  so any thread that observes COMPLETED/FAILED is guaranteed to see the result.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobService {

    private final IdeaAnalyzerService ideaAnalyzerService;

    // ── In-memory stores ──────────────────────────────────────────────────────

    private final ConcurrentHashMap<String, AnalysisJob> jobs = new ConcurrentHashMap<>();

    /**
     * Dedupe index: "userId:ideaTitle" → jobId.
     * Only contains entries for jobs that are PENDING or PROCESSING.
     * Entry is removed in the finally block of runAnalysis().
     */
    private final ConcurrentHashMap<String, String> dedupeIndex = new ConcurrentHashMap<>();

    // ── Dedicated thread pool — independent of HTTP threads ───────────────────

    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "analysis-worker-" + count.incrementAndGet());
            t.setDaemon(false);  // survive client disconnects; JVM keeps running until task completes
            return t;
        }
    });

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates and starts an async analysis job for the given user.
     * Returns immediately — the actual OpenAI call runs on a background thread.
     *
     * Duplicate detection: if the same user already has a PENDING/PROCESSING job
     * for the same idea title, the existing jobId is returned instead.
     */
    public StartAnalysisResponse startJob(UUID userId, AnalyzeIdeaRequest request) {
        String ideaTitle = request.getEffectiveTitle();
        String dedupeKey = userId + ":" + ideaTitle;

        // Return existing active job if one already exists for this user + title
        String existingJobId = dedupeIndex.get(dedupeKey);
        if (existingJobId != null) {
            AnalysisJob existing = jobs.get(existingJobId);
            if (existing != null &&
                    (existing.getStatus() == JobStatus.PENDING || existing.getStatus() == JobStatus.PROCESSING)) {
                log.info("Reusing existing job {} for user {} (title: {})", existingJobId, userId, ideaTitle);
                return StartAnalysisResponse.builder()
                        .jobId(existingJobId)
                        .status("EXISTING")
                        .message("Analysis already in progress — poll /api/analysis/status/" + existingJobId)
                        .build();
            }
        }

        // Create a new job
        String jobId = UUID.randomUUID().toString();
        AnalysisJob job = new AnalysisJob(jobId, userId, ideaTitle);
        jobs.put(jobId, job);
        dedupeIndex.put(dedupeKey, jobId);

        // Submit to the dedicated executor — this call returns immediately
        analysisExecutor.submit(() -> runAnalysis(jobId, userId, request));

        log.info("Started analysis job {} for user {} (title: {})", jobId, userId, ideaTitle);
        return StartAnalysisResponse.builder()
                .jobId(jobId)
                .status("PENDING")
                .message("Analysis started — poll /api/analysis/status/" + jobId)
                .build();
    }

    /**
     * Returns the current status (and result when completed) of a job.
     * Only the user who created the job may poll it.
     */
    public JobStatusResponse getJobStatus(String jobId, UUID userId) {
        AnalysisJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("Analysis job not found: " + jobId);
        }
        if (!job.getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorised to access job: " + jobId);
        }

        return JobStatusResponse.builder()
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
     * Runs on the dedicated analysis thread pool — completely independent of any
     * HTTP request. The client can disconnect; this method will continue to run.
     */
    private void runAnalysis(String jobId, UUID userId, AnalyzeIdeaRequest request) {
        AnalysisJob job = jobs.get(jobId);
        if (job == null) {
            log.warn("Job {} was removed before analysis started — skipping", jobId);
            return;
        }

        job.markProcessing();
        log.info("Job {} PROCESSING on thread {}", jobId, Thread.currentThread().getName());

        try {
            AnalysisResponse result = ideaAnalyzerService.analyzeIdea(userId, request);
            job.markCompleted(result);
            log.info("Job {} COMPLETED — score: {}", jobId, result.getScore());

        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "Unknown error during analysis";
            job.markFailed(message);
            log.error("Job {} FAILED: {}", jobId, message, e);

        } finally {
            // Remove from dedupe index so the user can start a fresh job later
            String dedupeKey = userId + ":" + request.getEffectiveTitle();
            dedupeIndex.remove(dedupeKey, jobId); // CAS: only removes if value still matches this jobId
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
            AnalysisJob job = entry.getValue();
            if (job.getCreatedAt().isBefore(cutoff)) {
                // Best-effort cleanup of any orphaned dedupeIndex entry
                String dedupeKey = job.getUserId() + ":" + job.getIdeaTitle();
                dedupeIndex.remove(dedupeKey, entry.getKey());
                removed[0]++;
                return true;
            }
            return false;
        });

        if (removed[0] > 0) {
            log.info("Cleaned up {} expired analysis jobs", removed[0]);
        }
    }
}
