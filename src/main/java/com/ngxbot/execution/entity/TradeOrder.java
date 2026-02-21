package com.ngxbot.execution.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 50)
    private String orderId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "side", nullable = false, length = 4)
    private String side;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "intended_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal intendedPrice;

    @Column(name = "executed_price", precision = 12, scale = 4)
    private BigDecimal executedPrice;

    @Column(name = "stop_loss", precision = 12, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "target_price_1", precision = 12, scale = 4)
    private BigDecimal targetPrice1;

    @Column(name = "target_price_2", precision = 12, scale = 4)
    private BigDecimal targetPrice2;

    @Column(name = "strategy", nullable = false, length = 50)
    private String strategy;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    // Signal snapshot
    @Column(name = "rsi_14", precision = 6, scale = 2)
    private BigDecimal rsi14;

    @Column(name = "macd_histogram", precision = 10, scale = 4)
    private BigDecimal macdHistogram;

    @Column(name = "volume_ratio", precision = 6, scale = 2)
    private BigDecimal volumeRatio;

    @Column(name = "nav_premium_discount_pct", precision = 8, scale = 4)
    private BigDecimal navPremiumDiscountPct;

    @Column(name = "sma_20", precision = 12, scale = 4)
    private BigDecimal sma20;

    // Risk validation
    @Column(name = "position_pct_of_portfolio", precision = 6, scale = 2)
    private BigDecimal positionPctOfPortfolio;

    @Column(name = "risk_pct_of_portfolio", precision = 6, scale = 2)
    private BigDecimal riskPctOfPortfolio;

    @Column(name = "cash_remaining_pct", precision = 6, scale = 2)
    private BigDecimal cashRemainingPct;

    @Column(name = "sector_exposure_pct", precision = 6, scale = 2)
    private BigDecimal sectorExposurePct;

    @Column(name = "all_risk_checks_passed")
    private Boolean allRiskChecksPassed;

    // Execution details
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "execution_method", length = 20)
    private String executionMethod;

    @Column(name = "approval_method", length = 20)
    private String approvalMethod;

    @Column(name = "approval_response_seconds")
    private Integer approvalResponseSeconds;

    @Column(name = "screenshots", columnDefinition = "TEXT[]")
    private String[] screenshots;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Outcome
    @Column(name = "exit_price", precision = 12, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "exit_date")
    private LocalDateTime exitDate;

    @Column(name = "pnl_naira", precision = 14, scale = 2)
    private BigDecimal pnlNaira;

    @Column(name = "pnl_pct", precision = 8, scale = 4)
    private BigDecimal pnlPct;

    @Column(name = "holding_days")
    private Integer holdingDays;

    @Column(name = "exit_reason", length = 30)
    private String exitReason;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
