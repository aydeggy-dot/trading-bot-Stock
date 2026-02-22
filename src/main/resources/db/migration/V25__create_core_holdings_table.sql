CREATE TABLE IF NOT EXISTS core_holdings (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    market VARCHAR(5) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    target_weight_pct DECIMAL(6,2),
    current_weight_pct DECIMAL(6,2),
    market_value DECIMAL(14,2),
    shares_held INTEGER,
    avg_cost_basis DECIMAL(12,4),
    last_rebalance_date DATE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(symbol, market)
);
CREATE INDEX idx_core_holdings_market ON core_holdings(market);
