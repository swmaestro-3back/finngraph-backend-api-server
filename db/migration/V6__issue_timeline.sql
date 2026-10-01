ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS summary TEXT;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS embedding vector(1024);
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS story_root_id BIGINT REFERENCES news_clusters (id) ON DELETE SET NULL;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS parent_cluster_id BIGINT REFERENCES news_clusters (id) ON DELETE SET NULL;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS link_score NUMERIC;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS linked_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_news_clusters_story_root ON news_clusters (story_root_id);

CREATE TABLE IF NOT EXISTS news_cluster_judgments (
    id              BIGSERIAL PRIMARY KEY,
    run_at          TIMESTAMPTZ NOT NULL,
    link            TEXT NOT NULL,
    title           TEXT NOT NULL,
    description     TEXT,
    published_at    TIMESTAMPTZ,
    cluster_id      BIGINT REFERENCES news_clusters (id) ON DELETE SET NULL,
    is_new_cluster  BOOLEAN NOT NULL,
    kept            BOOLEAN NOT NULL,
    seed_similarity NUMERIC,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_news_cluster_judgments_cluster ON news_cluster_judgments (cluster_id);
CREATE INDEX IF NOT EXISTS idx_news_cluster_judgments_run_at ON news_cluster_judgments (run_at);
