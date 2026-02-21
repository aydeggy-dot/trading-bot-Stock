package com.ngxbot.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analysis")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "news_item_id")
    private Long newsItemId;

    @Column(name = "symbol", length = 20)
    private String symbol;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "sentiment", length = 20)
    private String sentiment;

    @Column(name = "confidence_score")
    private int confidenceScore;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "key_insights", columnDefinition = "TEXT")
    private String keyInsights;

    @Column(name = "predicted_impact", length = 20)
    private String predictedImpact;

    @Column(name = "sector_implications", columnDefinition = "TEXT")
    private String sectorImplications;

    @Column(name = "forward_guidance", columnDefinition = "TEXT")
    private String forwardGuidance;

    @Column(name = "management_tone", length = 50)
    private String managementTone;

    @Column(name = "revenue_quality", length = 50)
    private String revenueQuality;

    @Column(name = "analysis_type", length = 30)
    private String analysisType;

    @Column(name = "input_tokens")
    private int inputTokens;

    @Column(name = "output_tokens")
    private int outputTokens;

    @Column(name = "cost_usd", precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
