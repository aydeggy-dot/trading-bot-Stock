package com.ngxbot.longterm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dca_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcaPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "market", nullable = false, length = 5)
    private String market;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "monthly_budget", precision = 12, scale = 2)
    private BigDecimal monthlyBudget;

    @Column(name = "execution_day")
    private Integer executionDay;

    @Column(name = "weight_pct", precision = 6, scale = 2)
    private BigDecimal weightPct;

    @Column(name = "last_execution_date")
    private LocalDate lastExecutionDate;

    @Column(name = "total_invested", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalInvested = BigDecimal.ZERO;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
