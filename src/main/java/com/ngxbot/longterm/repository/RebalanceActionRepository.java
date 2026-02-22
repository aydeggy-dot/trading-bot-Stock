package com.ngxbot.longterm.repository;

import com.ngxbot.longterm.entity.RebalanceAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RebalanceActionRepository extends JpaRepository<RebalanceAction, Long> {

    List<RebalanceAction> findByStatus(String status);

    List<RebalanceAction> findByTriggerDate(LocalDate triggerDate);

    List<RebalanceAction> findByTriggerDateAndStatus(LocalDate triggerDate, String status);
}
