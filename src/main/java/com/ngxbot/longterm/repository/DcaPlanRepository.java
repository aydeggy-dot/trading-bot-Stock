package com.ngxbot.longterm.repository;

import com.ngxbot.longterm.entity.DcaPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DcaPlanRepository extends JpaRepository<DcaPlan, Long> {

    List<DcaPlan> findByMarketAndIsActiveTrue(String market);

    List<DcaPlan> findByIsActiveTrue();

    Optional<DcaPlan> findBySymbolAndMarket(String symbol, String market);
}
