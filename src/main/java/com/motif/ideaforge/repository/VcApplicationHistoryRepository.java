package com.motif.ideaforge.repository;

import com.motif.ideaforge.model.entity.VcApplicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VcApplicationHistoryRepository extends JpaRepository<VcApplicationHistory, UUID> {

    /** Full audit trail for one application, oldest first. */
    List<VcApplicationHistory> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
}
