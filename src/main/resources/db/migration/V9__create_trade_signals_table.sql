CREATE TABLE trade_signals (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    signal_date DATE NOT NULL,
    side VARCHAR(4) NOT NULL,
    strength VARCHAR(20) NOT NULL,
    strategy VARCHAR(50) NOT NULL,
    confidence_score INTEGER,
    suggested_entry_price DECIMAL(12,4),
    suggested_stop_loss DECIMAL(12,4),
    suggested_target DECIMAL(12,4),
    reasoning TEXT,
    indicator_snapshot JSONB,
    is_acted_upon BOOLEAN DEFAULT FALSE,
    trade_order_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_signals_symbol_date ON trade_signals(symbol, signal_date DESC);
CREATE INDEX idx_signals_date ON trade_signals(signal_date DESC);
CREATE INDEX idx_signals_strength ON trade_signals(strength);
