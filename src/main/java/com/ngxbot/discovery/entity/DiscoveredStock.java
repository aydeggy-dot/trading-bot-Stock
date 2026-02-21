package com.ngxbot.discovery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "discovered_stocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveredStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20, unique = true)
    private String symbol;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "sector", length = 50)
    private String sector;

    @Column(name = "discovery_source", length = 20)
    private String discoverySource;

    @Column(name = "discovery_date")
    private LocalDate discoveryDate;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "CANDIDATE";

    @Column(name = "fundamental_score", precision = 10, scale = 2)
    private BigDecimal fundamentalScore;

    @Column(name = "observation_start_date")
    private LocalDate observationStartDate;

    @Column(name = "promotion_date")
    private LocalDate promotionDate;

    @Column(name = "demotion_date")
    private LocalDate demotionDate;

    @Column(name = "demotion_reason")
    private String demotionReason;

    @Column(name = "cooldown_until")
    private LocalDate cooldownUntil;

    @Column(name = "last_signal_date")
    private LocalDate lastSignalDate;

    @Column(name = "signal_count")
    @Builder.Default
    private int signalCount = 0;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
