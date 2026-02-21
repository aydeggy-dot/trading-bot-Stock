CREATE TABLE discovered_stocks (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    company_name VARCHAR(100),
    sector VARCHAR(50),
    discovery_source VARCHAR(20) NOT NULL,
    discovery_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'CANDIDATE',
    fundamental_score DECIMAL(5,2),
    observation_start_date DATE,
    promotion_date DATE,
    demotion_date DATE,
    demotion_reason TEXT,
    cooldown_until DATE,
    last_signal_date DATE,
    signal_count INTEGER DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_discovered_status ON discovered_stocks(status);
CREATE INDEX idx_discovered_symbol ON discovered_stocks(symbol);
CREATE INDEX idx_discovered_cooldown ON discovered_stocks(cooldown_until);
