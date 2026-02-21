package com.ngxbot.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_indices", uniqueConstraints = @UniqueConstraint(columnNames = {"index_name", "trade_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "index_name", nullable = false, length = 30)
    private String indexName;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_value", precision = 14, scale = 4)
    private BigDecimal openValue;

    @Column(name = "close_value", precision = 14, scale = 4)
    private BigDecimal closeValue;

    @Column(name = "high_value", precision = 14, scale = 4)
    private BigDecimal highValue;

    @Column(name = "low_value", precision = 14, scale = 4)
    private BigDecimal lowValue;

    @Column(name = "change_pct", precision = 8, scale = 4)
    private BigDecimal changePct;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
