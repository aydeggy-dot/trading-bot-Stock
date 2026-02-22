CREATE TABLE IF NOT EXISTS dca_plans (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    market VARCHAR(5) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    monthly_budget DECIMAL(12,2),
    execution_day INTEGER,
    weight_pct DECIMAL(6,2),
    last_execution_date DATE,
    total_invested DECIMAL(14,2) DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_dca_plans_market_active ON dca_plans(market, is_active);
