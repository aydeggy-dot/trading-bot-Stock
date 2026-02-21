package com.ngxbot.ai.repository;

import com.ngxbot.ai.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    Optional<AiAnalysis> findByNewsItemId(Long newsItemId);

    List<AiAnalysis> findBySymbolAndCreatedAtAfter(String symbol, LocalDateTime after);

    boolean existsByNewsItemId(Long newsItemId);
}
