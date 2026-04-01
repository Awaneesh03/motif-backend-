package com.motif.ideaforge.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for the vc_applications table.
 *
 * Dual-use:
 *   • VC intro-request flow  — vc_id set, founder_id derived from the idea
 *   • Founder funding-request flow — founder_id set (vc_id may be null until matched)
 *
 * ddl-auto=none — Hibernate never creates or modifies this table.
 */
@Entity
@Table(name = "vc_applications", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VcApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** VC investor who sent an intro request (null for founder-initiated submissions). */
    @Column(name = "vc_id")
    private UUID vcId;

    /** Founder who submitted the funding request. */
    @Column(name = "founder_id")
    private UUID founderId;

    /** The analysed idea being pitched. */
    @Column(name = "idea_id")
    private UUID ideaId;

    /**
     * Application lifecycle status.
     * Values: submitted | under_review | interested | rejected
     * (Legacy VC intro-request values: pending | accepted | rejected)
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "submitted";

    /** Free-text notes written by the reviewing VC or admin. */
    @Column(name = "vc_notes", columnDefinition = "TEXT")
    private String vcNotes;

    /** When a VC/admin last changed the status. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private Instant updatedAt;
}
