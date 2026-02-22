package com.ngxbot.dashboard.controller;

import com.ngxbot.discovery.entity.DiscoveredStock;
import com.ngxbot.discovery.entity.DiscoveryEvent;
import com.ngxbot.discovery.repository.DiscoveredStockRepository;
import com.ngxbot.discovery.repository.DiscoveryEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the stock discovery pipeline dashboard.
 * Exposes endpoints to view discovered stocks by lifecycle stage
 * and the promotion/demotion audit trail.
 */
@RestController
@RequestMapping("/api/discovery")
@Slf4j
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveredStockRepository discoveredStockRepository;
    private final DiscoveryEventRepository discoveryEventRepository;

    /**
     * Returns all stocks currently in the active watchlist (SEED or PROMOTED status).
     * These are the symbols the bot is actively monitoring for trade signals.
     */
    @GetMapping("/active")
    public ResponseEntity<List<DiscoveredStock>> getActiveStocks() {
        List<DiscoveredStock> active = discoveredStockRepository
                .findByStatusIn(List.of("SEED", "PROMOTED"));
        log.debug("[DISCOVERY] Returning {} active stocks (SEED + PROMOTED)", active.size());
        return ResponseEntity.ok(active);
    }

    /**
     * Returns all stocks in the evaluation pipeline (CANDIDATE or OBSERVATION status).
     * These stocks have been discovered but not yet promoted to the active watchlist.
     */
    @GetMapping("/candidates")
    public ResponseEntity<List<DiscoveredStock>> getCandidateStocks() {
        List<DiscoveredStock> candidates = discoveredStockRepository
                .findByStatusIn(List.of("CANDIDATE", "OBSERVATION"));
        log.debug("[DISCOVERY] Returning {} candidate stocks (CANDIDATE + OBSERVATION)", candidates.size());
        return ResponseEntity.ok(candidates);
    }

    /**
     * Returns the full promotion/demotion audit trail ordered by most recent first.
     * Each event records a status transition with the reason for the change.
     */
    @GetMapping("/history")
    public ResponseEntity<List<DiscoveryEvent>> getDiscoveryHistory() {
        List<DiscoveryEvent> events = discoveryEventRepository
                .findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        log.debug("[DISCOVERY] Returning {} discovery events", events.size());
        return ResponseEntity.ok(events);
    }
}
