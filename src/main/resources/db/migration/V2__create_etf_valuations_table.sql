CREATE TABLE etf_valuations (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    market_price DECIMAL(12,4) NOT NULL,
    nav DECIMAL(12,4),
    premium_discount_pct DECIMAL(8,4),
    nav_source VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(symbol, trade_date)
);

CREATE INDEX idx_etf_symbol_date ON etf_valuations(symbol, trade_date DESC);
