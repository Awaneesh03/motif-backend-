package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.IdeaAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for IdeaAnalysis entity
 */
@Repository
public interface IdeaAnalysisRepository extends JpaRepository<IdeaAnalysis, UUID> {

    List<IdeaAnalysis> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Paginated version — prefer this for any endpoint returning user idea lists
     * to avoid loading all rows when a user has many analyses.
     * Example: findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20))
     */
    Page<IdeaAnalysis> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Returns the most recent analysis for the user — used by MentorChatService. */
    Optional<IdeaAnalysis> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Upsert check — find an existing analysis for this user + title so we can
     * UPDATE it instead of inserting a duplicate row.
     */
    Optional<IdeaAnalysis> findByUserIdAndIdeaTitle(UUID userId, String ideaTitle);

    /**
     * Ownership-safe lookup used by submit-for-review.
     * Returns empty if the idea doesn't exist OR belongs to a different user —
     * both cases surface as 404 to avoid leaking existence of other users' ideas.
     */
    Optional<IdeaAnalysis> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
