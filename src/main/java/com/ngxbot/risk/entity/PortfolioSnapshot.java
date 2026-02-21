package com.ngxbot.risk.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_snapshots", uniqueConstraints = @UniqueConstraint(columnNames = {"snapshot_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "cash_balance", precision = 14, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "equity_value", precision = 14, scale = 2)
    private BigDecimal equityValue;

    @Column(name = "daily_pnl", precision = 14, scale = 2)
    private BigDecimal dailyPnl;

    @Column(name = "daily_pnl_pct", precision = 8, scale = 4)
    private BigDecimal dailyPnlPct;

    @Column(name = "weekly_pnl", precision = 14, scale = 2)
    private BigDecimal weeklyPnl;

    @Column(name = "open_positions_count")
    private Integer openPositionsCount;

    @Column(name = "win_rate", precision = 6, scale = 2)
    private BigDecimal winRate;

    @Column(name = "profit_factor", precision = 8, scale = 4)
    private BigDecimal profitFactor;

    @Column(name = "max_drawdown_pct", precision = 8, scale = 4)
    private BigDecimal maxDrawdownPct;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
