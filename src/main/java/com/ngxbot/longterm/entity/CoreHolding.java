package com.ngxbot.longterm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "core_holdings", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "market"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "market", nullable = false, length = 5)
    private String market;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "target_weight_pct", precision = 6, scale = 2)
    private BigDecimal targetWeightPct;

    @Column(name = "current_weight_pct", precision = 6, scale = 2)
    private BigDecimal currentWeightPct;

    @Column(name = "market_value", precision = 14, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "shares_held")
    private Integer sharesHeld;

    @Column(name = "avg_cost_basis", precision = 12, scale = 4)
    private BigDecimal avgCostBasis;

    @Column(name = "last_rebalance_date")
    private LocalDate lastRebalanceDate;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
