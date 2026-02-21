package com.ngxbot.strategy;

/**
 * Indicates which market(s) a strategy operates on.
 * <p>
 * NGX = Nigerian Stock Exchange only.
 * US = US equity markets only.
 * BOTH = strategy applies to both markets.
 */
public enum StrategyMarket {
    NGX,
    US,
    BOTH
}
