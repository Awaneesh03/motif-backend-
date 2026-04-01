package com.motif.ideaforge.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/**
 * Lightweight read-only mapping of the Supabase `profiles` table.
 * Only `id` and `role` are mapped — the rest of the columns are
 * managed exclusively by Supabase and are not needed here.
 *
 * ddl-auto=none — Hibernate never creates or modifies this table.
 */
@Entity
@Table(name = "profiles", schema = "public")
@Immutable
@Data
@NoArgsConstructor
public class Profile {

    @Id
    private UUID id;

    /**
     * Role string stored in Supabase profiles.
     * Values: founder | vc | vc_pending | admin | super_admin
     */
    @Column(name = "role")
    private String role;
}
