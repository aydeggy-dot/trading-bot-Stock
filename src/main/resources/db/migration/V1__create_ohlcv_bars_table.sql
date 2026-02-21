CREATE TABLE ohlcv_bars (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    open_price DECIMAL(12,4),
    high_price DECIMAL(12,4),
    low_price DECIMAL(12,4),
    close_price DECIMAL(12,4),
    adjusted_close DECIMAL(12,4),
    volume BIGINT,
    data_source VARCHAR(20) DEFAULT 'EODHD',
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(symbol, trade_date)
);

CREATE INDEX idx_ohlcv_symbol_date ON ohlcv_bars(symbol, trade_date DESC);
CREATE INDEX idx_ohlcv_symbol ON ohlcv_bars(symbol);
