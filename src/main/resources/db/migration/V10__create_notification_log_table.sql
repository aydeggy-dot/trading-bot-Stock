CREATE TABLE notification_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(50),
    message_text TEXT,
    has_image BOOLEAN DEFAULT FALSE,
    image_path VARCHAR(255),
    sent_successfully BOOLEAN DEFAULT FALSE,
    error_message TEXT,
    related_order_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_notifications_type ON notification_log(event_type);
CREATE INDEX idx_notifications_created ON notification_log(created_at DESC);
CREATE INDEX idx_notifications_order ON notification_log(related_order_id);
