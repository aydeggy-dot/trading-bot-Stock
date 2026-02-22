package com.ngxbot.dashboard.controller;

import com.ngxbot.execution.service.FxRateReconciler;
import com.ngxbot.execution.service.KillSwitchService;
import com.ngxbot.execution.service.PortfolioReconciler;
import com.ngxbot.execution.service.CashReconciler;
import com.ngxbot.longterm.entity.DividendEvent;
import com.ngxbot.longterm.repository.DividendEventRepository;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.entity.PortfolioSnapshot;
import com.ngxbot.risk.repository.PortfolioSnapshotRepository;
import com.ngxbot.risk.repository.PositionRepository;
import com.ngxbot.risk.service.SettlementCashTracker;
import com.ngxbot.strategy.StrategyMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class DashboardController {

    private final PositionRepository positionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final SettlementCashTracker settlementCashTracker;
    private final KillSwitchService killSwitchService;
    private final FxRateReconciler fxRateReconciler;
    private final PortfolioReconciler portfolioReconciler;
    private final CashReconciler cashReconciler;
    private final DividendEventRepository dividendEventRepository;

    // ---- Portfolio endpoints ----

    @GetMapping("/portfolio")
    public ResponseEntity<Map<String, Object>> getPortfolio() {
        List<Position> openPositions = positionRepository.findAll().stream()
                .filter(p -> p.getIsOpen() != null && p.getIsOpen())
                .toList();

        BigDecimal ngxValue = sumMarketValue(openPositions, "NGX");
        BigDecimal usValue = sumMarketValue(openPositions, "US");
        BigDecimal totalNgn = ngxValue; // US value would need FX conversion

        BigDecimal brokerFxRate = fxRateReconciler.getLastBrokerRate();
        if (brokerFxRate.compareTo(BigDecimal.ZERO) > 0) {
            totalNgn = totalNgn.add(usValue.multiply(brokerFxRate));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalValueNgn", totalNgn);
        result.put("ngxValue", ngxValue);
        result.put("usValueUsd", usValue);
        result.put("fxRate", brokerFxRate);
        result.put("openPositions", openPositions.size());
        result.put("byMarket", Map.of("NGX", ngxValue, "US", usValue));
        result.put("byPool", getPoolBreakdown(openPositions));
        result.put("killSwitchActive", killSwitchService.isActive());

        PortfolioSnapshot latest = portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc().orElse(null);
        if (latest != null) {
            result.put("dailyPnlPct", latest.getDailyPnlPct());
            result.put("snapshotDate", latest.getSnapshotDate());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/portfolio/ngx")
    public ResponseEntity<Map<String, Object>> getNgxPortfolio() {
        return getMarketPortfolio("NGX", "NGN");
    }

    @GetMapping("/portfolio/us")
    public ResponseEntity<Map<String, Object>> getUsPortfolio() {
        return getMarketPortfolio("US", "USD");
    }

    @GetMapping("/portfolio/core")
    public ResponseEntity<List<Position>> getCorePositions() {
        return ResponseEntity.ok(getPositionsByPool("CORE"));
    }

    @GetMapping("/portfolio/satellite")
    public ResponseEntity<List<Position>> getSatellitePositions() {
        return ResponseEntity.ok(getPositionsByPool("SATELLITE"));
    }

    // ---- FX endpoint ----

    @GetMapping("/fx")
    public ResponseEntity<Map<String, Object>> getFxInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brokerRate", fxRateReconciler.getLastBrokerRate());
        result.put("marketRate", fxRateReconciler.getLastMarketRate());

        BigDecimal brokerRate = fxRateReconciler.getLastBrokerRate();
        BigDecimal marketRate = fxRateReconciler.getLastMarketRate();
        BigDecimal spread = BigDecimal.ZERO;
        if (marketRate.compareTo(BigDecimal.ZERO) > 0 && brokerRate.compareTo(BigDecimal.ZERO) > 0) {
            spread = brokerRate.subtract(marketRate).abs()
                    .divide(marketRate, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        result.put("spreadPct", spread);
        return ResponseEntity.ok(result);
    }

    // ---- Settlement endpoint ----

    @GetMapping("/settlement")
    public ResponseEntity<Map<String, Object>> getSettlement() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ngx", settlementCashTracker.getLedgerSnapshot(StrategyMarket.NGX));
        result.put("us", settlementCashTracker.getLedgerSnapshot(StrategyMarket.US));
        return ResponseEntity.ok(result);
    }

    // ---- Dividends endpoint ----

    @GetMapping("/dividends")
    public ResponseEntity<List<DividendEvent>> getDividends() {
        return ResponseEntity.ok(dividendEventRepository.findAll());
    }

    // ---- Reconciliation endpoint ----

    @GetMapping("/reconciliation")
    public ResponseEntity<Map<String, Object>> getReconciliationStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("killSwitchActive", killSwitchService.isActive());
        result.put("killSwitchReason", killSwitchService.getReason());
        result.put("killSwitchActivatedAt", killSwitchService.getActivatedAt());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> triggerReconciliation() {
        log.info("[DASHBOARD] Manual reconciliation triggered");
        boolean portfolioOk = portfolioReconciler.reconcile();
        boolean ngxCashOk = cashReconciler.reconcile(StrategyMarket.NGX);
        boolean usCashOk = cashReconciler.reconcile(StrategyMarket.US);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolioMatch", portfolioOk);
        result.put("ngxCashMatch", ngxCashOk);
        result.put("usCashMatch", usCashOk);
        result.put("allClear", portfolioOk && ngxCashOk && usCashOk);
        return ResponseEntity.ok(result);
    }

    // ---- Kill switch endpoint ----

    @GetMapping("/killswitch")
    public ResponseEntity<Map<String, Object>> getKillSwitchStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", killSwitchService.isActive());
        result.put("reason", killSwitchService.getReason());
        result.put("activatedAt", killSwitchService.getActivatedAt());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/killswitch/activate")
    public ResponseEntity<Map<String, String>> activateKillSwitch(@RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "Manual activation via dashboard");
        killSwitchService.activate(reason);
        return ResponseEntity.ok(Map.of("status", "activated", "reason", reason));
    }

    @PostMapping("/killswitch/deactivate")
    public ResponseEntity<Map<String, String>> deactivateKillSwitch() {
        killSwitchService.deactivate();
        return ResponseEntity.ok(Map.of("status", "deactivated"));
    }

    // ---- Helper methods ----

    private ResponseEntity<Map<String, Object>> getMarketPortfolio(String market, String currency) {
        List<Position> positions = positionRepository.findAll().stream()
                .filter(p -> p.getIsOpen() != null && p.getIsOpen())
                .filter(p -> market.equals(p.getMarket()))
                .toList();

        BigDecimal totalValue = sumMarketValue(positions, market);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("market", market);
        result.put("currency", currency);
        result.put("totalValue", totalValue);
        result.put("positions", positions);
        result.put("positionCount", positions.size());
        result.put("availableCash", settlementCashTracker.getAvailableCash(
                "NGX".equals(market) ? StrategyMarket.NGX : StrategyMarket.US));
        return ResponseEntity.ok(result);
    }

    private BigDecimal sumMarketValue(List<Position> positions, String market) {
        return positions.stream()
                .filter(p -> market.equals(p.getMarket()))
                .map(p -> p.getCurrentPrice() != null && p.getQuantity() != null
                        ? p.getCurrentPrice().multiply(BigDecimal.valueOf(p.getQuantity()))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> getPoolBreakdown(List<Position> positions) {
        Map<String, BigDecimal> byPool = new LinkedHashMap<>();
        byPool.put("CORE", positions.stream()
                .filter(p -> "CORE".equals(p.getPool()))
                .map(p -> p.getCurrentPrice() != null ? p.getCurrentPrice().multiply(BigDecimal.valueOf(p.getQuantity())) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        byPool.put("SATELLITE", positions.stream()
                .filter(p -> "SATELLITE".equals(p.getPool()))
                .map(p -> p.getCurrentPrice() != null ? p.getCurrentPrice().multiply(BigDecimal.valueOf(p.getQuantity())) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return byPool;
    }

    private List<Position> getPositionsByPool(String pool) {
        return positionRepository.findAll().stream()
                .filter(p -> p.getIsOpen() != null && p.getIsOpen())
                .filter(p -> pool.equals(p.getPool()))
                .toList();
    }
}
