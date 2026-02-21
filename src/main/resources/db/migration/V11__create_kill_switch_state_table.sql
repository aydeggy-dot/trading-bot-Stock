CREATE TABLE kill_switch_state (
    id BIGSERIAL PRIMARY KEY,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    activated_by VARCHAR(30),
    activation_reason TEXT,
    activated_at TIMESTAMP,
    deactivated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Insert initial state (kill switch OFF)
INSERT INTO kill_switch_state (is_active, activated_by, activation_reason)
VALUES (FALSE, 'SYSTEM', 'Initial system state');
