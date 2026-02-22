package com.ngxbot.backtest.repository;

import com.ngxbot.backtest.entity.EquityCurvePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquityCurveRepository extends JpaRepository<EquityCurvePoint, Long> {
    List<EquityCurvePoint> findByBacktestRunIdOrderByTradeDateAsc(Long backtestRunId);
}
