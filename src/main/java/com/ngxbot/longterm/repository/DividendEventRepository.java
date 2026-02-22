package com.ngxbot.longterm.repository;

import com.ngxbot.longterm.entity.DividendEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DividendEventRepository extends JpaRepository<DividendEvent, Long> {

    List<DividendEvent> findBySymbol(String symbol);

    List<DividendEvent> findByExDateBetween(LocalDate from, LocalDate to);

    List<DividendEvent> findByReinvestedFalse();

    Optional<DividendEvent> findBySymbolAndExDate(String symbol, LocalDate exDate);
}
