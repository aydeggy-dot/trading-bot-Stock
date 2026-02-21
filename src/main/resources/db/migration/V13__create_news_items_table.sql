CREATE TABLE news_items (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    source VARCHAR(50) NOT NULL,
    url VARCHAR(1000),
    published_at TIMESTAMP,
    symbols TEXT[],
    sentiment VARCHAR(20),
    relevance_score INTEGER,
    summary TEXT,
    is_processed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_news_published ON news_items(published_at DESC);
CREATE INDEX idx_news_source ON news_items(source);
CREATE INDEX idx_news_processed ON news_items(is_processed);
