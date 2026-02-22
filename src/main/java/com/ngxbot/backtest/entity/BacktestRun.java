package com.ngxbot.backtest.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "backtest_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_name", nullable = false, length = 50)
    private String strategyName;

    @Column(name = "market", nullable = false, length = 5)
    private String market;  // NGX or US

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "initial_capital", nullable = false, precision = 14, scale = 2)
    private BigDecimal initialCapital;

    @Column(name = "final_capital", precision = 14, scale = 2)
    private BigDecimal finalCapital;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;  // NGN or USD

    // Performance metrics
    @Column(name = "total_return_pct", precision = 10, scale = 4)
    private BigDecimal totalReturnPct;

    @Column(name = "annualized_return_pct", precision = 10, scale = 4)
    private BigDecimal annualizedReturnPct;

    @Column(name = "sharpe_ratio", precision = 8, scale = 4)
    private BigDecimal sharpeRatio;

    @Column(name = "max_drawdown_pct", precision = 10, scale = 4)
    private BigDecimal maxDrawdownPct;

    @Column(name = "win_rate_pct", precision = 8, scale = 4)
    private BigDecimal winRatePct;

    @Column(name = "profit_factor", precision = 8, scale = 4)
    private BigDecimal profitFactor;

    @Column(name = "total_trades")
    private Integer totalTrades;

    @Column(name = "winning_trades")
    private Integer winningTrades;

    @Column(name = "losing_trades")
    private Integer losingTrades;

    @Column(name = "avg_holding_period_days", precision = 8, scale = 2)
    private BigDecimal avgHoldingPeriodDays;

    @Column(name = "max_consecutive_losses")
    private Integer maxConsecutiveLosses;

    @Column(name = "gross_profit", precision = 14, scale = 2)
    private BigDecimal grossProfit;

    @Column(name = "gross_loss", precision = 14, scale = 2)
    private BigDecimal grossLoss;

    @Column(name = "total_commissions", precision = 14, scale = 2)
    private BigDecimal totalCommissions;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "RUNNING";  // RUNNING, COMPLETED, FAILED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
