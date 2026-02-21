package com.ngxbot.risk.repository;

import com.ngxbot.risk.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByIsOpenTrue();

    List<Position> findBySymbolAndIsOpenTrue(String symbol);

    Optional<Position> findByEntryOrderId(String entryOrderId);

    List<Position> findBySectorAndIsOpenTrue(String sector);

    @Query("SELECT COUNT(p) FROM Position p WHERE p.isOpen = true")
    long countOpenPositions();

    List<Position> findByIsOpenTrueOrderByEntryDateAsc();
}
