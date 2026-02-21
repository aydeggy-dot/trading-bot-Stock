CREATE TABLE market_indices (
    id BIGSERIAL PRIMARY KEY,
    index_name VARCHAR(30) NOT NULL,
    trade_date DATE NOT NULL,
    open_value DECIMAL(14,4),
    close_value DECIMAL(14,4),
    high_value DECIMAL(14,4),
    low_value DECIMAL(14,4),
    change_pct DECIMAL(8,4),
    volume BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(index_name, trade_date)
);

CREATE INDEX idx_market_indices_name_date ON market_indices(index_name, trade_date DESC);
