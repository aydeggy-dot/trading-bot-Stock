package com.ngxbot.discovery.repository;

import com.ngxbot.discovery.entity.DiscoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscoveryEventRepository extends JpaRepository<DiscoveryEvent, Long> {

    List<DiscoveryEvent> findBySymbolOrderByCreatedAtDesc(String symbol);
}
