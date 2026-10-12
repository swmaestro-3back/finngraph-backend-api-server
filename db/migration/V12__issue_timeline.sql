ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS embedding vector(1024);
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS story_root_id BIGINT REFERENCES news_clusters (id) ON DELETE SET NULL;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS parent_cluster_id BIGINT REFERENCES news_clusters (id) ON DELETE SET NULL;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS link_score NUMERIC;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS link_relation TEXT;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS linked_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'chk_news_clusters_link_relation'
           AND conrelid = 'news_clusters'::regclass
    ) THEN
        ALTER TABLE news_clusters
            ADD CONSTRAINT chk_news_clusters_link_relation
            CHECK (link_relation IN ('follow_up', 'same_event'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_news_clusters_story_root ON news_clusters (story_root_id);
CREATE INDEX IF NOT EXISTS idx_news_clusters_parent ON news_clusters (parent_cluster_id);
