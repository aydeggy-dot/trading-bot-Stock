package com.ngxbot.data.repository;

import com.ngxbot.data.entity.CorporateAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CorporateActionRepository extends JpaRepository<CorporateAction, Long> {

    List<CorporateAction> findBySymbolOrderByExDateDesc(String symbol);

    List<CorporateAction> findByExDateBetween(LocalDate from, LocalDate to);

    List<CorporateAction> findByActionType(String actionType);

    List<CorporateAction> findBySymbolAndActionType(String symbol, String actionType);
}
