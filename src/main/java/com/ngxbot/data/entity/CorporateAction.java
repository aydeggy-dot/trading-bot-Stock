package com.ngxbot.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "corporate_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorporateAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "announcement_date")
    private LocalDate announcementDate;

    @Column(name = "ex_date")
    private LocalDate exDate;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "value", precision = 12, scale = 4)
    private BigDecimal value;

    @Column(name = "description")
    private String description;

    @Column(name = "data_source", length = 30)
    private String dataSource;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
