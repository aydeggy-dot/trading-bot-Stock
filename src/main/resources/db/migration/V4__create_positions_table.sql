CREATE TABLE positions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    avg_entry_price DECIMAL(12,4) NOT NULL,
    current_price DECIMAL(12,4),
    stop_loss DECIMAL(12,4),
    target_price DECIMAL(12,4),
    strategy VARCHAR(50),
    sector VARCHAR(50),
    entry_date DATE NOT NULL,
    entry_order_id VARCHAR(50) REFERENCES trade_orders(order_id),
    unrealized_pnl DECIMAL(14,2),
    unrealized_pnl_pct DECIMAL(8,4),
    is_open BOOLEAN DEFAULT TRUE,
    closed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_positions_symbol ON positions(symbol);
CREATE INDEX idx_positions_open ON positions(is_open);
CREATE INDEX idx_positions_entry_order ON positions(entry_order_id);
