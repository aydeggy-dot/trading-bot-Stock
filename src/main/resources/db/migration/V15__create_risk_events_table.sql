CREATE TABLE risk_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    symbol VARCHAR(20),
    trade_order_id VARCHAR(50),
    description TEXT NOT NULL,
    current_value DECIMAL(14,4),
    threshold_value DECIMAL(14,4),
    action_taken VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_risk_events_type ON risk_events(event_type);
CREATE INDEX idx_risk_events_severity ON risk_events(severity);
CREATE INDEX idx_risk_events_created ON risk_events(created_at DESC);
