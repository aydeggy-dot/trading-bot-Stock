package com.ngxbot.backtest.repository;

import com.ngxbot.backtest.entity.BacktestTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacktestTradeRepository extends JpaRepository<BacktestTrade, Long> {
    List<BacktestTrade> findByBacktestRunIdOrderByEntryDateAsc(Long backtestRunId);
    List<BacktestTrade> findByBacktestRunIdAndIsOpenTrue(Long backtestRunId);

    @Query("SELECT COUNT(t) FROM BacktestTrade t WHERE t.backtestRunId = :runId AND t.netPnl > 0")
    int countWinningTrades(Long runId);

    @Query("SELECT COUNT(t) FROM BacktestTrade t WHERE t.backtestRunId = :runId AND t.netPnl <= 0")
    int countLosingTrades(Long runId);
}
