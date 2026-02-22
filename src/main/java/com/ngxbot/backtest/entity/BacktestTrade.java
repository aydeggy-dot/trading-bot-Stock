package com.ngxbot.backtest.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "backtest_trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestTrade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "backtest_run_id", nullable = false)
    private Long backtestRunId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "side", nullable = false, length = 4)
    private String side;  // BUY or SELL

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "entry_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 12, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "commission", precision = 10, scale = 4)
    private BigDecimal commission;

    @Column(name = "slippage", precision = 10, scale = 4)
    private BigDecimal slippage;

    @Column(name = "gross_pnl", precision = 14, scale = 2)
    private BigDecimal grossPnl;

    @Column(name = "net_pnl", precision = 14, scale = 2)
    private BigDecimal netPnl;

    @Column(name = "net_pnl_pct", precision = 10, scale = 4)
    private BigDecimal netPnlPct;

    @Column(name = "holding_days")
    private Integer holdingDays;

    @Column(name = "signal_strength", length = 20)
    private String signalStrength;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "exit_reason", length = 50)
    private String exitReason;  // STOP_LOSS, TARGET_HIT, SIGNAL_EXIT, END_OF_BACKTEST

    @Column(name = "is_open")
    @Builder.Default
    private Boolean isOpen = true;
}
