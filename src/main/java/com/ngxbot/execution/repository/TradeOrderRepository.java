package com.ngxbot.execution.repository;

import com.ngxbot.execution.entity.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    Optional<TradeOrder> findByOrderId(String orderId);

    List<TradeOrder> findByStatus(String status);

    List<TradeOrder> findBySymbol(String symbol);

    List<TradeOrder> findByStrategy(String strategy);

    List<TradeOrder> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    @Query("SELECT t FROM TradeOrder t WHERE t.status IN ('CONFIRMED') AND t.exitPrice IS NULL")
    List<TradeOrder> findOpenTrades();

    List<TradeOrder> findByStatusAndCreatedAtAfter(String status, LocalDateTime after);
}
