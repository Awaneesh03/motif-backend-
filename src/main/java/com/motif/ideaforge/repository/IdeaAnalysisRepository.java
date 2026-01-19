package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.IdeaAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for IdeaAnalysis entity
 */
@Repository
public interface IdeaAnalysisRepository extends JpaRepository<IdeaAnalysis, UUID> {

    List<IdeaAnalysis> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);
}
