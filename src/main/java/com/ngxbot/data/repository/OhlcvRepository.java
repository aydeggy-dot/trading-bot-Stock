package com.ngxbot.data.repository;

import com.ngxbot.data.entity.OhlcvBar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OhlcvRepository extends JpaRepository<OhlcvBar, Long> {

    List<OhlcvBar> findBySymbolOrderByTradeDateDesc(String symbol);

    List<OhlcvBar> findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(String symbol, LocalDate from, LocalDate to);

    Optional<OhlcvBar> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    @Query("SELECT o FROM OhlcvBar o WHERE o.symbol = :symbol ORDER BY o.tradeDate DESC LIMIT :limit")
    List<OhlcvBar> findLatestBySymbol(String symbol, int limit);

    List<OhlcvBar> findByTradeDateOrderBySymbolAsc(LocalDate tradeDate);

    boolean existsBySymbolAndTradeDate(String symbol, LocalDate tradeDate);
}
