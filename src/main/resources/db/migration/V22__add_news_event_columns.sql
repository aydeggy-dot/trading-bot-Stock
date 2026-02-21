-- Add event classification columns to news_items
ALTER TABLE news_items ADD COLUMN IF NOT EXISTS event_types TEXT[];
ALTER TABLE news_items ADD COLUMN IF NOT EXISTS impact_score INTEGER;
ALTER TABLE news_items ADD COLUMN IF NOT EXISTS body TEXT;

CREATE INDEX IF NOT EXISTS idx_news_event_types ON news_items USING GIN(event_types);
CREATE INDEX IF NOT EXISTS idx_news_symbols ON news_items USING GIN(symbols);
