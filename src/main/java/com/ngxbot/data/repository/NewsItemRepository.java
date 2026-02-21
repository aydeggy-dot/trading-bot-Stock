package com.ngxbot.data.repository;

import com.ngxbot.data.entity.NewsItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    List<NewsItem> findBySourceOrderByPublishedAtDesc(String source);

    List<NewsItem> findByPublishedAtAfterOrderByPublishedAtDesc(LocalDateTime after);

    List<NewsItem> findByIsProcessedFalse();

    boolean existsByUrl(String url);
}
