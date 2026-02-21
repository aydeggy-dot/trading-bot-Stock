package com.ngxbot.signal.repository;

import com.ngxbot.signal.entity.TradeSignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TradeSignalRepository extends JpaRepository<TradeSignalEntity, Long> {

    List<TradeSignalEntity> findBySignalDateOrderByConfidenceScoreDesc(LocalDate date);

    List<TradeSignalEntity> findBySymbolAndSignalDate(String symbol, LocalDate date);

    List<TradeSignalEntity> findByIsActedUponFalseAndSignalDate(LocalDate date);

    List<TradeSignalEntity> findByStrategyAndSignalDate(String strategy, LocalDate date);
}
