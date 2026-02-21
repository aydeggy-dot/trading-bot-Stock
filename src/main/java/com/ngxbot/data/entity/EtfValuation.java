package com.ngxbot.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "etf_valuations", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "trade_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtfValuation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "market_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal marketPrice;

    @Column(name = "nav", precision = 12, scale = 4)
    private BigDecimal nav;

    @Column(name = "premium_discount_pct", precision = 8, scale = 4)
    private BigDecimal premiumDiscountPct;

    @Column(name = "nav_source", length = 50)
    private String navSource;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
