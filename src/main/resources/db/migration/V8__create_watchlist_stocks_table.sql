CREATE TABLE watchlist_stocks (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    company_name VARCHAR(100),
    sector VARCHAR(50),
    sub_sector VARCHAR(50),
    market_segment VARCHAR(20),
    stock_type VARCHAR(20) DEFAULT 'EQUITY',
    is_etf BOOLEAN DEFAULT FALSE,
    is_ngx30 BOOLEAN DEFAULT FALSE,
    is_pension_eligible BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_watchlist_sector ON watchlist_stocks(sector);
CREATE INDEX idx_watchlist_status ON watchlist_stocks(status);
CREATE INDEX idx_watchlist_etf ON watchlist_stocks(is_etf);
