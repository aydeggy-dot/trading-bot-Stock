package com.ngxbot.data.repository;

import com.ngxbot.data.entity.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketIndexRepository extends JpaRepository<MarketIndex, Long> {

    Optional<MarketIndex> findByIndexNameAndTradeDate(String indexName, LocalDate tradeDate);

    List<MarketIndex> findByIndexNameOrderByTradeDateDesc(String indexName);

    List<MarketIndex> findByIndexNameAndTradeDateBetweenOrderByTradeDateAsc(String indexName, LocalDate from, LocalDate to);

    boolean existsByIndexNameAndTradeDate(String indexName, LocalDate tradeDate);
}
