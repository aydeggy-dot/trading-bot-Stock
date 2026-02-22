CREATE TABLE IF NOT EXISTS rebalance_actions (
    id BIGSERIAL PRIMARY KEY,
    trigger_date DATE NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    market VARCHAR(5) NOT NULL,
    action_type VARCHAR(10) NOT NULL,
    current_weight_pct DECIMAL(6,2),
    target_weight_pct DECIMAL(6,2),
    drift_pct DECIMAL(6,2),
    quantity INTEGER,
    estimated_value DECIMAL(14,2),
    status VARCHAR(20) DEFAULT 'PENDING',
    executed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_rebalance_actions_status ON rebalance_actions(status);
CREATE INDEX idx_rebalance_actions_date ON rebalance_actions(trigger_date);
