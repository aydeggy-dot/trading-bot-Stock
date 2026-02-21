CREATE TABLE approval_requests (
    id BIGSERIAL PRIMARY KEY,
    trade_order_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    channel VARCHAR(20) NOT NULL,
    message_id VARCHAR(100),
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP,
    response_text VARCHAR(50),
    timeout_minutes INTEGER DEFAULT 5,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_approval_status ON approval_requests(status);
CREATE INDEX idx_approval_order ON approval_requests(trade_order_id);
