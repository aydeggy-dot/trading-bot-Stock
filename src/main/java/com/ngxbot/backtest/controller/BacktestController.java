package com.ngxbot.backtest.controller;

import com.ngxbot.backtest.entity.BacktestRun;
import com.ngxbot.backtest.entity.BacktestTrade;
import com.ngxbot.backtest.entity.EquityCurvePoint;
import com.ngxbot.backtest.repository.BacktestRunRepository;
import com.ngxbot.backtest.repository.BacktestTradeRepository;
import com.ngxbot.backtest.repository.EquityCurveRepository;
import com.ngxbot.backtest.service.BacktestRunner;
import com.ngxbot.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/backtest")
@Slf4j
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestRunner backtestRunner;
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final EquityCurveRepository equityCurveRepository;
    private final List<Strategy> strategies;

    @PostMapping("/run")
    public ResponseEntity<?> runBacktest(@RequestBody BacktestRequest request) {
        Strategy strategy = strategies.stream()
                .filter(s -> s.getName().equals(request.strategyName()))
                .findFirst()
                .orElse(null);

        if (strategy == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Strategy not found: " + request.strategyName()));
        }

        // Run async
        CompletableFuture.runAsync(() ->
            backtestRunner.runBacktest(strategy, request.startDate(), request.endDate(),
                    request.initialCapital(), request.market())
        );

        return ResponseEntity.accepted()
                .body(Map.of("message", "Backtest started for " + request.strategyName(),
                        "market", request.market()));
    }

    @GetMapping("/runs")
    public ResponseEntity<List<BacktestRun>> getAllRuns(
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) String market) {
        List<BacktestRun> runs;
        if (strategy != null && market != null) {
            runs = backtestRunRepository.findByStrategyNameAndMarketOrderByCreatedAtDesc(strategy, market);
        } else if (strategy != null) {
            runs = backtestRunRepository.findByStrategyNameOrderByCreatedAtDesc(strategy);
        } else if (market != null) {
            runs = backtestRunRepository.findByMarketOrderByCreatedAtDesc(market);
        } else {
            runs = backtestRunRepository.findAll();
        }
        return ResponseEntity.ok(runs);
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<BacktestRun> getRunById(@PathVariable Long id) {
        return backtestRunRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/runs/{id}/trades")
    public ResponseEntity<List<BacktestTrade>> getTradesForRun(@PathVariable Long id) {
        return ResponseEntity.ok(backtestTradeRepository.findByBacktestRunIdOrderByEntryDateAsc(id));
    }

    @GetMapping("/runs/{id}/equity-curve")
    public ResponseEntity<List<EquityCurvePoint>> getEquityCurveForRun(@PathVariable Long id) {
        return ResponseEntity.ok(equityCurveRepository.findByBacktestRunIdOrderByTradeDateAsc(id));
    }

    @GetMapping("/strategies")
    public ResponseEntity<List<Map<String, String>>> listStrategies() {
        List<Map<String, String>> strategyList = strategies.stream()
                .map(s -> Map.of(
                        "name", s.getName(),
                        "market", s.getMarket().name(),
                        "pool", s.getPool().name(),
                        "enabled", String.valueOf(s.isEnabled())
                ))
                .toList();
        return ResponseEntity.ok(strategyList);
    }

    public record BacktestRequest(
            String strategyName,
            String market,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal initialCapital
    ) {}
}
