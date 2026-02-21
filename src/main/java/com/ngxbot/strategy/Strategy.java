package com.ngxbot.strategy;

import com.ngxbot.signal.model.TradeSignal;

import java.time.LocalDate;
import java.util.List;

/**
 * Strategy interface for NGX trading strategies.
 * Each strategy evaluates symbols and produces trade signals.
 */
public interface Strategy {

    /**
     * @return unique name of this strategy
     */
    String getName();

    /**
     * @return true if this strategy is enabled in configuration
     */
    boolean isEnabled();

    /**
     * Evaluate a single symbol and produce trade signals (if any).
     *
     * @param symbol stock symbol to evaluate
     * @param date evaluation date
     * @return list of trade signals (may be empty)
     */
    List<TradeSignal> evaluate(String symbol, LocalDate date);

    /**
     * Get the list of symbols this strategy is interested in.
     *
     * @return list of symbols to evaluate
     */
    List<String> getTargetSymbols();
}
