package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.VcApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VcApplicationRepository extends JpaRepository<VcApplication, UUID> {

    /** All applications submitted by a specific founder, newest first. */
    List<VcApplication> findByFounderIdOrderByCreatedAtDesc(UUID founderId);

    /** All applications across all founders, newest first (VC/admin view). */
    List<VcApplication> findAllByOrderByCreatedAtDesc();

    /**
     * Fetch applications with their associated idea title in a single query.
     * Uses a LEFT JOIN so applications without an idea (idea_id = null) are still returned.
     */
    @Query(value = """
            SELECT v.id,
                   v.vc_id,
                   v.founder_id,
                   v.idea_id,
                   v.status,
                   v.vc_notes,
                   v.reviewed_at,
                   v.created_at,
                   v.updated_at,
                   ia.idea_title
            FROM   public.vc_applications v
            LEFT JOIN public.idea_analyses ia ON ia.id = v.idea_id
            WHERE  v.founder_id = :founderId
            ORDER  BY v.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findWithIdeaTitleByFounderId(@Param("founderId") UUID founderId);

    /**
     * Same join for the VC/admin pipeline — all records, optional status filter.
     * Pass null for statusFilter to return all statuses.
     */
    @Query(value = """
            SELECT v.id,
                   v.vc_id,
                   v.founder_id,
                   v.idea_id,
                   v.status,
                   v.vc_notes,
                   v.reviewed_at,
                   v.created_at,
                   v.updated_at,
                   ia.idea_title
            FROM   public.vc_applications v
            LEFT JOIN public.idea_analyses ia ON ia.id = v.idea_id
            WHERE  (:statusFilter IS NULL OR v.status = :statusFilter)
            ORDER  BY v.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findAllWithIdeaTitle(@Param("statusFilter") String statusFilter);
}
