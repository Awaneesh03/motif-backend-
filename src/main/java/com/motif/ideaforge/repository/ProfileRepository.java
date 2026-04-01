package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Read-only access to the Supabase `profiles` table.
 * Used exclusively for role-checking on VC/admin-gated endpoints.
 */
@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    // findById(UUID) from JpaRepository is sufficient — returns Optional<Profile>
}
