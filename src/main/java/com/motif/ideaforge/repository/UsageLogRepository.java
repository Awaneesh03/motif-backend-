package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for UsageLog entity
 */
@Repository
public interface UsageLogRepository extends JpaRepository<UsageLog, UUID> {

    @Query("SELECT SUM(u.tokensUsed) FROM UsageLog u WHERE u.userId = :userId AND u.createdAt >= :since")
    Long sumTokensUsedByUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("SELECT COUNT(u) FROM UsageLog u WHERE u.userId = :userId AND u.createdAt >= :since")
    Long countByUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
