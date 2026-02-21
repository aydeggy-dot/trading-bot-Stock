package com.ngxbot.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "symbols", columnDefinition = "TEXT[]")
    private String[] symbols;

    @Column(name = "sentiment", length = 20)
    private String sentiment;

    @Column(name = "relevance_score")
    private Integer relevanceScore;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "event_types", columnDefinition = "TEXT[]")
    private String[] eventTypes;

    @Column(name = "impact_score")
    private Integer impactScore;

    @Column(name = "is_processed")
    @Builder.Default
    private Boolean isProcessed = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
