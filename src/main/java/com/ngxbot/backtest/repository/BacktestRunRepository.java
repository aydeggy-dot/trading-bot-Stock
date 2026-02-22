package com.ngxbot.backtest.repository;

import com.ngxbot.backtest.entity.BacktestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {
    List<BacktestRun> findByStrategyNameOrderByCreatedAtDesc(String strategyName);
    List<BacktestRun> findByMarketOrderByCreatedAtDesc(String market);
    List<BacktestRun> findByStatusOrderByCreatedAtDesc(String status);
    List<BacktestRun> findByStrategyNameAndMarketOrderByCreatedAtDesc(String strategyName, String market);
}
