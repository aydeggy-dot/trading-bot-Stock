package com.ngxbot.longterm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividend_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "market", nullable = false, length = 5)
    private String market;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "ex_date")
    private LocalDate exDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "dividend_per_share", precision = 10, scale = 4)
    private BigDecimal dividendPerShare;

    @Column(name = "shares_held_at_ex_date")
    private Integer sharesHeldAtExDate;

    @Column(name = "gross_amount", precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "withholding_tax_pct", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal withholdingTaxPct = BigDecimal.ZERO;

    @Column(name = "net_amount_received", precision = 14, scale = 2)
    private BigDecimal netAmountReceived;

    @Column(name = "reinvested")
    @Builder.Default
    private Boolean reinvested = false;

    @Column(name = "reinvest_order_id", length = 50)
    private String reinvestOrderId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
