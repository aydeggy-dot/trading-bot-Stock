package com.ngxbot.longterm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rebalance_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RebalanceAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trigger_date", nullable = false)
    private LocalDate triggerDate;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "market", nullable = false, length = 5)
    private String market;

    @Column(name = "action_type", nullable = false, length = 10)
    private String actionType;

    @Column(name = "current_weight_pct", precision = 6, scale = 2)
    private BigDecimal currentWeightPct;

    @Column(name = "target_weight_pct", precision = 6, scale = 2)
    private BigDecimal targetWeightPct;

    @Column(name = "drift_pct", precision = 6, scale = 2)
    private BigDecimal driftPct;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "estimated_value", precision = 14, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
