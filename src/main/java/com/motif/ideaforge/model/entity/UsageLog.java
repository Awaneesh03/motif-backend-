package com.motif.ideaforge.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for ai_usage_logs table
 */
@Entity
@Table(name = "ai_usage_logs", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String endpoint;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "tokens_used", nullable = false)
    private Integer tokensUsed;

    @Column(name = "estimated_cost", nullable = false, precision = 10, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;
}
