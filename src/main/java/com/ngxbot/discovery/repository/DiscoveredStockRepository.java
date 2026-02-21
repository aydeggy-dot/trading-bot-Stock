package com.ngxbot.discovery.repository;

import com.ngxbot.discovery.entity.DiscoveredStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiscoveredStockRepository extends JpaRepository<DiscoveredStock, Long> {

    Optional<DiscoveredStock> findBySymbol(String symbol);

    List<DiscoveredStock> findByStatus(String status);

    List<DiscoveredStock> findByStatusIn(List<String> statuses);

    long countByStatus(String status);

    boolean existsBySymbol(String symbol);

    List<DiscoveredStock> findByCooldownUntilBeforeAndStatus(LocalDate date, String status);
}
