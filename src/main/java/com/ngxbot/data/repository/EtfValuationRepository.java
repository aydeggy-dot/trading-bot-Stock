package com.ngxbot.data.repository;

import com.ngxbot.data.entity.EtfValuation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EtfValuationRepository extends JpaRepository<EtfValuation, Long> {

    Optional<EtfValuation> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    List<EtfValuation> findBySymbolOrderByTradeDateDesc(String symbol);

    List<EtfValuation> findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(String symbol, LocalDate from, LocalDate to);

    List<EtfValuation> findByTradeDateOrderByPremiumDiscountPctAsc(LocalDate tradeDate);

    boolean existsBySymbolAndTradeDate(String symbol, LocalDate tradeDate);
}
