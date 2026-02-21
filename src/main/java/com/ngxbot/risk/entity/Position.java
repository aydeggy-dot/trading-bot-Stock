package com.ngxbot.risk.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "avg_entry_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal avgEntryPrice;

    @Column(name = "current_price", precision = 12, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "stop_loss", precision = 12, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "target_price", precision = 12, scale = 4)
    private BigDecimal targetPrice;

    @Column(name = "strategy", length = 50)
    private String strategy;

    @Column(name = "sector", length = 50)
    private String sector;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "entry_order_id", length = 50)
    private String entryOrderId;

    @Column(name = "unrealized_pnl", precision = 14, scale = 2)
    private BigDecimal unrealizedPnl;

    @Column(name = "unrealized_pnl_pct", precision = 8, scale = 4)
    private BigDecimal unrealizedPnlPct;

    @Column(name = "is_open")
    @Builder.Default
    private Boolean isOpen = true;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
