package com.ngxbot.backtest.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "equity_curve_points")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquityCurvePoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "backtest_run_id", nullable = false)
    private Long backtestRunId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "portfolio_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal portfolioValue;

    @Column(name = "cash_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "positions_value", precision = 14, scale = 2)
    private BigDecimal positionsValue;

    @Column(name = "drawdown_pct", precision = 10, scale = 4)
    private BigDecimal drawdownPct;

    @Column(name = "daily_return_pct", precision = 10, scale = 4)
    private BigDecimal dailyReturnPct;
}
