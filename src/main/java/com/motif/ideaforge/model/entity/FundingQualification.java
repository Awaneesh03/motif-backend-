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
 * JPA Entity for funding_qualifications table.
 * One row per user — uniqueness is enforced by the UNIQUE constraint on user_id.
 * Persists founder profile data so returning users see a pre-filled qualification form.
 */
@Entity
@Table(name = "funding_qualifications", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundingQualification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Owner of this qualification profile — unique per user. */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "full_name", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String fullName = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String email = "";

    /**
     * Founder experience level.
     * Expected values: "first_time" | "1_2_startups" | "3_plus" | "serial"
     */
    @Column(name = "experience_level", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String experienceLevel = "";

    @Column(name = "linkedin_url", columnDefinition = "TEXT")
    @Builder.Default
    private String linkedinUrl = "";

    /** Free-text description of previous startup experience. */
    @Column(name = "previous_startups", columnDefinition = "TEXT")
    @Builder.Default
    private String previousStartups = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private Instant updatedAt;
}
