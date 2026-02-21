CREATE TABLE discovery_events (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20),
    reason TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_discovery_events_symbol ON discovery_events(symbol);
CREATE INDEX idx_discovery_events_type ON discovery_events(event_type);
