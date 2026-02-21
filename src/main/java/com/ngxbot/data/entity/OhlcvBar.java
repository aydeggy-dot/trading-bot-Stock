package com.ngxbot.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ohlcv_bars", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "trade_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OhlcvBar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", precision = 12, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 12, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 12, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", precision = 12, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "adjusted_close", precision = 12, scale = 4)
    private BigDecimal adjustedClose;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "data_source", length = 20)
    @Builder.Default
    private String dataSource = "EODHD";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
