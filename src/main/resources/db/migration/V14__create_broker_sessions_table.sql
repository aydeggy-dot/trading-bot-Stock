CREATE TABLE broker_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    login_at TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP,
    expires_at TIMESTAMP,
    logout_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_broker_sessions_status ON broker_sessions(status);
