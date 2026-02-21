package com.ngxbot.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist_stocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20, unique = true)
    private String symbol;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "sector", length = 50)
    private String sector;

    @Column(name = "sub_sector", length = 50)
    private String subSector;

    @Column(name = "market_segment", length = 20)
    private String marketSegment;

    @Column(name = "stock_type", length = 20)
    @Builder.Default
    private String stockType = "EQUITY";

    @Column(name = "is_etf")
    @Builder.Default
    private Boolean isEtf = false;

    @Column(name = "is_ngx30")
    @Builder.Default
    private Boolean isNgx30 = false;

    @Column(name = "is_pension_eligible")
    @Builder.Default
    private Boolean isPensionEligible = false;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
