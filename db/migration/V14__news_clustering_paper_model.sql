ALTER TABLE news ADD COLUMN IF NOT EXISTS term_weights JSONB;
ALTER TABLE news ADD COLUMN IF NOT EXISTS cluster_score NUMERIC(12, 6);
ALTER TABLE news ADD COLUMN IF NOT EXISTS cluster_features JSONB;
ALTER TABLE news DROP COLUMN IF EXISTS cluster_terms;

CREATE INDEX IF NOT EXISTS idx_news_unvectorized ON news (id) WHERE term_weights IS NULL;
CREATE INDEX IF NOT EXISTS idx_news_unclustered ON news (id) WHERE cluster_id IS NULL;

ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS centroids JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS mean_published_at TIMESTAMPTZ;
UPDATE news_clusters SET mean_published_at = first_published_at WHERE mean_published_at IS NULL;
ALTER TABLE news_clusters ALTER COLUMN mean_published_at SET NOT NULL;

ALTER TABLE news_clusters DROP CONSTRAINT IF EXISTS chk_news_clusters_size;
ALTER TABLE news_clusters DROP COLUMN IF EXISTS term_weights;
ALTER TABLE news_clusters DROP COLUMN IF EXISTS original_size;
ALTER TABLE news_clusters DROP COLUMN IF EXISTS cohesion;

CREATE TABLE IF NOT EXISTS news_cluster_idf_terms (
    field TEXT    NOT NULL,
    token TEXT    NOT NULL,
    df    INTEGER NOT NULL,
    PRIMARY KEY (field, token)
);

CREATE TABLE IF NOT EXISTS news_cluster_idf_meta (
    id            SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    article_count INTEGER     NOT NULL,
    corpus_since  TIMESTAMPTZ NOT NULL,
    built_at      TIMESTAMPTZ NOT NULL
);
