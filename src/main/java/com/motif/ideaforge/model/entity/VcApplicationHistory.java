package com.motif.ideaforge.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record for every status change on a vc_application.
 * Rows are INSERT-only — never updated or deleted.
 *
 * ddl-auto=none — Hibernate never creates or modifies this table.
 */
@Entity
@Table(name = "vc_application_history", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VcApplicationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    /** Previous status (null when the first history entry is created for a legacy row). */
    @Column(name = "old_status", updatable = false)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, updatable = false)
    private String newStatus;

    /** user_id of the VC / admin who triggered this change. */
    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant changedAt = Instant.now();
}
