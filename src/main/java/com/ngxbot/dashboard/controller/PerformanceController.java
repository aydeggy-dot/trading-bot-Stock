package com.ngxbot.dashboard.controller;

import com.ngxbot.risk.entity.PortfolioSnapshot;
import com.ngxbot.risk.entity.Position;
import com.ngxbot.risk.repository.PortfolioSnapshotRepository;
import com.ngxbot.risk.repository.PositionRepository;
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
@RequestMapping("/api/performance")
@Slf4j
@RequiredArgsConstructor
public class PerformanceController {

    private final PositionRepository positionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPerformance() {
        PortfolioSnapshot latest = portfolioSnapshotRepository.findFirstByOrderBySnapshotDateDesc().orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();

        if (latest != null) {
            result.put("snapshotDate", latest.getSnapshotDate());
            result.put("totalValue", latest.getTotalValue());
            result.put("dailyPnl", latest.getDailyPnl());
            result.put("dailyPnlPct", latest.getDailyPnlPct());
        }

        // Per-market breakdown from open positions
        List<Position> openPositions = positionRepository.findAll().stream()
                .filter(p -> p.getIsOpen() != null && p.getIsOpen())
                .toList();

        Map<String, BigDecimal> unrealizedByMarket = new LinkedHashMap<>();
        unrealizedByMarket.put("NGX", sumUnrealizedPnl(openPositions, "NGX"));
        unrealizedByMarket.put("US", sumUnrealizedPnl(openPositions, "US"));
        result.put("unrealizedPnlByMarket", unrealizedByMarket);

        Map<String, BigDecimal> unrealizedByPool = new LinkedHashMap<>();
        unrealizedByPool.put("CORE", sumUnrealizedPnlByPool(openPositions, "CORE"));
        unrealizedByPool.put("SATELLITE", sumUnrealizedPnlByPool(openPositions, "SATELLITE"));
        result.put("unrealizedPnlByPool", unrealizedByPool);

        result.put("openPositionCount", openPositions.size());

        return ResponseEntity.ok(result);
    }

    private BigDecimal sumUnrealizedPnl(List<Position> positions, String market) {
        return positions.stream()
                .filter(p -> market.equals(p.getMarket()))
                .map(p -> p.getUnrealizedPnl() != null ? p.getUnrealizedPnl() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumUnrealizedPnlByPool(List<Position> positions, String pool) {
        return positions.stream()
                .filter(p -> pool.equals(p.getPool()))
                .map(p -> p.getUnrealizedPnl() != null ? p.getUnrealizedPnl() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
