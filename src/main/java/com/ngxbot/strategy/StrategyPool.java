package com.ngxbot.strategy;

/**
 * Classifies strategies into portfolio allocation pools.
 * <p>
 * CORE strategies form the foundation of the portfolio with lower risk and
 * well-understood edge. SATELLITE strategies are opportunistic, higher-risk
 * allocations that complement the core.
 */
public enum StrategyPool {
    CORE,
    SATELLITE
}
