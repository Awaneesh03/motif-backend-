package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.IdeaAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
     * @deprecated Superseded by {@link #findByUserIdAndNormalizedIdea} — kept for safety.
     */
    @Deprecated
    Optional<IdeaAnalysis> findByUserIdAndIdeaTitle(UUID userId, String ideaTitle);

    /**
     * Post-upsert fetch: retrieve the row by the normalized key to get its id + createdAt.
     */
    Optional<IdeaAnalysis> findByUserIdAndNormalizedIdea(UUID userId, String normalizedIdea);

    /**
     * Ownership-safe lookup used by submit-for-review.
     * Returns empty if the idea doesn't exist OR belongs to a different user —
     * both cases surface as 404 to avoid leaking existence of other users' ideas.
     */
    Optional<IdeaAnalysis> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    /**
     * Returns the 10 most recently updated analyses for a user.
     * Used by GET /api/ideas/recent.
     * Rows where updated_at IS NULL (legacy rows never re-analyzed) sort last
     * because Postgres places NULLs last in DESC order by default.
     */
    List<IdeaAnalysis> findTop10ByUserIdOrderByUpdatedAtDesc(UUID userId);

    /**
     * Atomic upsert using PostgreSQL ON CONFLICT DO UPDATE.
     * <p>
     * INSERT path  — fires when no row exists for (user_id, normalized_idea).
     *   id and created_at are set once and never change.
     * <p>
     * UPDATE path  — fires when the same (user_id, normalized_idea) already exists.
     *   Only analysis fields are updated; status, created_at, and mentor_context
     *   are intentionally excluded so they are never overwritten by a re-analysis.
     * <p>
     * JSONB columns must be passed as serialized JSON strings; they are cast inside
     * the query with CAST(:param AS jsonb).  Null-safe: CAST(NULL AS jsonb) = NULL.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO public.idea_analyses (
            id,
            user_id,
            idea_title,
            normalized_idea,
            idea_description,
            target_market,
            score,
            strengths,
            weaknesses,
            recommendations,
            market_size,
            competition,
            viability,
            idea_summary,
            heuristic_scores,
            investor_analysis,
            confidence_score,
            competitive_advantage,
            status,
            created_at,
            updated_at
        ) VALUES (
            gen_random_uuid(),
            CAST(:userId         AS uuid),
            :ideaTitle,
            :normalizedIdea,
            :ideaDescription,
            :targetMarket,
            :score,
            CAST(:strengths      AS jsonb),
            CAST(:weaknesses     AS jsonb),
            CAST(:recommendations AS jsonb),
            :marketSize,
            :competition,
            :viability,
            :ideaSummary,
            CAST(:heuristicScores  AS jsonb),
            CAST(:investorAnalysis AS jsonb),
            :confidenceScore,
            :competitiveAdvantage,
            'draft',
            NOW(),
            NOW()
        )
        ON CONFLICT (user_id, normalized_idea) DO UPDATE SET
            idea_title            = EXCLUDED.idea_title,
            idea_description      = EXCLUDED.idea_description,
            target_market         = EXCLUDED.target_market,
            score                 = EXCLUDED.score,
            strengths             = EXCLUDED.strengths,
            weaknesses            = EXCLUDED.weaknesses,
            recommendations       = EXCLUDED.recommendations,
            market_size           = EXCLUDED.market_size,
            competition           = EXCLUDED.competition,
            viability             = EXCLUDED.viability,
            idea_summary          = EXCLUDED.idea_summary,
            heuristic_scores      = EXCLUDED.heuristic_scores,
            investor_analysis     = EXCLUDED.investor_analysis,
            confidence_score      = EXCLUDED.confidence_score,
            competitive_advantage = EXCLUDED.competitive_advantage,
            updated_at            = NOW()
        """)
    void upsertAnalysis(
        @Param("userId")               UUID   userId,
        @Param("ideaTitle")            String ideaTitle,
        @Param("normalizedIdea")       String normalizedIdea,
        @Param("ideaDescription")      String ideaDescription,
        @Param("targetMarket")         String targetMarket,
        @Param("score")                Integer score,
        @Param("strengths")            String strengthsJson,
        @Param("weaknesses")           String weaknessesJson,
        @Param("recommendations")      String recommendationsJson,
        @Param("marketSize")           String marketSize,
        @Param("competition")          String competition,
        @Param("viability")            String viability,
        @Param("ideaSummary")          String ideaSummary,
        @Param("heuristicScores")      String heuristicScoresJson,
        @Param("investorAnalysis")     String investorAnalysisJson,
        @Param("confidenceScore")      Integer confidenceScore,
        @Param("competitiveAdvantage") String competitiveAdvantage
    );
}
