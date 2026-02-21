package com.ngxbot.signal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_signals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "signal_date", nullable = false)
    private LocalDate signalDate;

    @Column(name = "side", nullable = false, length = 4)
    private String side;

    @Column(name = "strength", nullable = false, length = 20)
    private String strength;

    @Column(name = "strategy", nullable = false, length = 50)
    private String strategy;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "suggested_entry_price", precision = 12, scale = 4)
    private BigDecimal suggestedEntryPrice;

    @Column(name = "suggested_stop_loss", precision = 12, scale = 4)
    private BigDecimal suggestedStopLoss;

    @Column(name = "suggested_target", precision = 12, scale = 4)
    private BigDecimal suggestedTarget;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "indicator_snapshot", columnDefinition = "JSONB")
    private String indicatorSnapshot;

    @Column(name = "is_acted_upon")
    @Builder.Default
    private Boolean isActedUpon = false;

    @Column(name = "trade_order_id", length = 50)
    private String tradeOrderId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
