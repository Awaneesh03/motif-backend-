package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.IdeaAnalysis;
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

    /** Returns the most recent analysis for the user — used by MentorChatService. */
    Optional<IdeaAnalysis> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Upsert check — find an existing analysis for this user + title so we can
     * UPDATE it instead of inserting a duplicate row.
     */
    Optional<IdeaAnalysis> findByUserIdAndIdeaTitle(UUID userId, String ideaTitle);

    long countByUserId(UUID userId);
}
