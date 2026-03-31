package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.FundingQualification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FundingQualificationRepository extends JpaRepository<FundingQualification, UUID> {

    Optional<FundingQualification> findByUserId(UUID userId);

    /**
     * Native upsert — inserts a new row or updates the existing one for this user.
     * ON CONFLICT (user_id) ensures exactly one row per user at all times.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO public.funding_qualifications
                (id, user_id, full_name, email, experience_level, linkedin_url, previous_startups, created_at, updated_at)
            VALUES
                (gen_random_uuid(), :userId, :fullName, :email, :experienceLevel, :linkedinUrl, :previousStartups, NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                full_name         = EXCLUDED.full_name,
                email             = EXCLUDED.email,
                experience_level  = EXCLUDED.experience_level,
                linkedin_url      = EXCLUDED.linkedin_url,
                previous_startups = EXCLUDED.previous_startups,
                updated_at        = NOW()
            """, nativeQuery = true)
    void upsert(
            @Param("userId")            UUID   userId,
            @Param("fullName")          String fullName,
            @Param("email")             String email,
            @Param("experienceLevel")   String experienceLevel,
            @Param("linkedinUrl")       String linkedinUrl,
            @Param("previousStartups")  String previousStartups
    );
}
