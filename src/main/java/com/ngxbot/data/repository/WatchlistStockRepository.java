package com.ngxbot.data.repository;

import com.ngxbot.data.entity.WatchlistStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistStockRepository extends JpaRepository<WatchlistStock, Long> {

    Optional<WatchlistStock> findBySymbol(String symbol);

    List<WatchlistStock> findByStatus(String status);

    List<WatchlistStock> findByIsEtfTrue();

    List<WatchlistStock> findByIsNgx30True();

    List<WatchlistStock> findByIsPensionEligibleTrue();

    List<WatchlistStock> findBySector(String sector);

    List<WatchlistStock> findByStatusAndIsEtf(String status, Boolean isEtf);
}
