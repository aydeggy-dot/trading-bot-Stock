package com.ngxbot.risk.repository;

import com.ngxbot.risk.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    Optional<PortfolioSnapshot> findBySnapshotDate(LocalDate snapshotDate);

    List<PortfolioSnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(LocalDate from, LocalDate to);

    Optional<PortfolioSnapshot> findFirstByOrderBySnapshotDateDesc();

    boolean existsBySnapshotDate(LocalDate snapshotDate);
}
