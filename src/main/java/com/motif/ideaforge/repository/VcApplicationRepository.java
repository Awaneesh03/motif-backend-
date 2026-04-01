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

    // ── Paginated queries (used by service layer) ─────────────────────────────

    /**
     * Founder view: applications for one founder, paginated, newest first.
     * LEFT JOIN enriches each row with the idea_title from idea_analyses.
     *
     * Columns: 0=id, 1=vc_id, 2=founder_id, 3=idea_id, 4=status,
     *          5=vc_notes, 6=reviewed_at, 7=created_at, 8=updated_at, 9=idea_title
     */
    @Query(value = """
            SELECT v.id, v.vc_id, v.founder_id, v.idea_id, v.status,
                   v.vc_notes, v.reviewed_at, v.created_at, v.updated_at,
                   ia.idea_title
            FROM   public.vc_applications v
            LEFT JOIN public.idea_analyses ia ON ia.id = v.idea_id
            WHERE  v.founder_id = :founderId
            ORDER  BY v.created_at DESC
            LIMIT  :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findPageByFounderId(
            @Param("founderId") UUID   founderId,
            @Param("size")      int    size,
            @Param("offset")    long   offset);

    @Query(value = """
            SELECT COUNT(*) FROM public.vc_applications
            WHERE  founder_id = :founderId
            """, nativeQuery = true)
    long countByFounderId(@Param("founderId") UUID founderId);

    /**
     * VC/admin pipeline view: all applications, optional status filter, paginated.
     * Sorted by updated_at DESC (most recently touched first) then created_at DESC.
     *
     * Pass null for statusFilter to return all statuses.
     */
    @Query(value = """
            SELECT v.id, v.vc_id, v.founder_id, v.idea_id, v.status,
                   v.vc_notes, v.reviewed_at, v.created_at, v.updated_at,
                   ia.idea_title
            FROM   public.vc_applications v
            LEFT JOIN public.idea_analyses ia ON ia.id = v.idea_id
            WHERE  (:statusFilter IS NULL OR v.status = :statusFilter)
            ORDER  BY v.updated_at DESC, v.created_at DESC
            LIMIT  :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findPageAll(
            @Param("statusFilter") String statusFilter,
            @Param("size")         int    size,
            @Param("offset")       long   offset);

    @Query(value = """
            SELECT COUNT(*) FROM public.vc_applications
            WHERE  (:statusFilter IS NULL OR status = :statusFilter)
            """, nativeQuery = true)
    long countAll(@Param("statusFilter") String statusFilter);

    // ── Legacy non-paginated helpers (kept for backward compat) ──────────────

    @Query(value = """
            SELECT v.id, v.vc_id, v.founder_id, v.idea_id, v.status,
                   v.vc_notes, v.reviewed_at, v.created_at, v.updated_at,
                   ia.idea_title
            FROM   public.vc_applications v
            LEFT JOIN public.idea_analyses ia ON ia.id = v.idea_id
            WHERE  v.founder_id = :founderId
            ORDER  BY v.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findWithIdeaTitleByFounderId(@Param("founderId") UUID founderId);

    @Query(value = """
            SELECT v.id, v.vc_id, v.founder_id, v.idea_id, v.status,
                   v.vc_notes, v.reviewed_at, v.created_at, v.updated_at,
                   ia.idea_title
            FROM   public.vc_applications v
            LEFT JOIN public.idea_analyses ia ON ia.id = v.idea_id
            WHERE  (:statusFilter IS NULL OR v.status = :statusFilter)
            ORDER  BY v.updated_at DESC, v.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findAllWithIdeaTitle(@Param("statusFilter") String statusFilter);
}
