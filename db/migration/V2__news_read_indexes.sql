CREATE INDEX IF NOT EXISTS idx_news_visible
    ON news (published_at DESC NULLS LAST, id DESC)
    WHERE is_material AND relation_extracted;
