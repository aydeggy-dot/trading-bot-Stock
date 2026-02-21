CREATE TABLE circuit_breaker_log (
    id BIGSERIAL PRIMARY KEY,
    breaker_type VARCHAR(30) NOT NULL,
    triggered_at TIMESTAMP NOT NULL,
    pnl_value DECIMAL(14,2) NOT NULL,
    pnl_pct DECIMAL(8,4) NOT NULL,
    threshold_pct DECIMAL(8,4) NOT NULL,
    resume_at TIMESTAMP,
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_circuit_breaker_type ON circuit_breaker_log(breaker_type);
CREATE INDEX idx_circuit_breaker_resolved ON circuit_breaker_log(is_resolved);
