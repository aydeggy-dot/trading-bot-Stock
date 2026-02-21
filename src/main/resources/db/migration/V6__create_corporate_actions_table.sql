CREATE TABLE corporate_actions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    announcement_date DATE,
    ex_date DATE,
    record_date DATE,
    payment_date DATE,
    value DECIMAL(12,4),
    description TEXT,
    data_source VARCHAR(30),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_corp_actions_symbol ON corporate_actions(symbol);
CREATE INDEX idx_corp_actions_ex_date ON corporate_actions(ex_date);
CREATE INDEX idx_corp_actions_type ON corporate_actions(action_type);
