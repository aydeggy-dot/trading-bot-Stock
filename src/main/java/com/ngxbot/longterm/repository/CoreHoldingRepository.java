package com.ngxbot.longterm.repository;

import com.ngxbot.longterm.entity.CoreHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoreHoldingRepository extends JpaRepository<CoreHolding, Long> {

    List<CoreHolding> findByMarket(String market);

    Optional<CoreHolding> findBySymbolAndMarket(String symbol, String market);

    List<CoreHolding> findAllByOrderByMarketAscSymbolAsc();
}
