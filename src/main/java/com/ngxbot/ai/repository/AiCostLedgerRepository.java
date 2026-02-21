package com.ngxbot.ai.repository;

import com.ngxbot.ai.entity.AiCostLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AiCostLedgerRepository extends JpaRepository<AiCostLedger, Long> {

    List<AiCostLedger> findByCallDate(LocalDate date);

    @Query("SELECT COALESCE(SUM(c.costUsd), 0) FROM AiCostLedger c WHERE c.callDate = :date")
    BigDecimal sumCostByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(c.costUsd), 0) FROM AiCostLedger c WHERE c.callDate BETWEEN :startDate AND :endDate")
    BigDecimal sumCostByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
