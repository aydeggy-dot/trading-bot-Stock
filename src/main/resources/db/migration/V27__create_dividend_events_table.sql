CREATE TABLE IF NOT EXISTS dividend_events (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    market VARCHAR(5) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    ex_date DATE,
    payment_date DATE,
    dividend_per_share DECIMAL(10,4),
    shares_held_at_ex_date INTEGER,
    gross_amount DECIMAL(14,2),
    withholding_tax_pct DECIMAL(6,2) DEFAULT 0,
    net_amount_received DECIMAL(14,2),
    reinvested BOOLEAN DEFAULT FALSE,
    reinvest_order_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_dividend_events_symbol ON dividend_events(symbol);
CREATE INDEX idx_dividend_events_ex_date ON dividend_events(ex_date);
