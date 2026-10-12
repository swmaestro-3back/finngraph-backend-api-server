ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS issue_kind TEXT;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS issue_kind_reason TEXT;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS issue_kind_model TEXT;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS issue_kind_prompt_version TEXT;
ALTER TABLE news_clusters ADD COLUMN IF NOT EXISTS issue_kind_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'chk_news_clusters_issue_kind'
           AND conrelid = 'news_clusters'::regclass
    ) THEN
        ALTER TABLE news_clusters
            ADD CONSTRAINT chk_news_clusters_issue_kind
            CHECK (issue_kind IN ('event', 'market_reaction'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS news_issue_link_votes (
    id                   BIGSERIAL PRIMARY KEY,
    run_id               UUID             NOT NULL,
    cluster_id           BIGINT           NOT NULL REFERENCES news_clusters (id) ON DELETE CASCADE,
    candidate_cluster_id BIGINT           NOT NULL REFERENCES news_clusters (id) ON DELETE CASCADE,
    cosine               DOUBLE PRECISION NOT NULL,
    event_score          DOUBLE PRECISION,
    gap_hours            DOUBLE PRECISION NOT NULL,
    pair_vote            BOOLEAN,
    rank_vote            BOOLEAN,
    judge_vote           BOOLEAN,
    check_vote           BOOLEAN,
    pair_route           SMALLINT,
    judge_by             TEXT,
    pair_label           TEXT,
    rank_label           TEXT,
    judge_label          TEXT,
    judge_a_scope        TEXT,
    judge_b_scope        TEXT,
    check_role           TEXT,
    vote_count           SMALLINT         NOT NULL,
    accepted             BOOLEAN          NOT NULL,
    chosen               BOOLEAN          NOT NULL,
    evidence             JSONB            NOT NULL DEFAULT '{}'::jsonb,
    created_at           TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT uq_news_issue_link_votes UNIQUE (run_id, cluster_id, candidate_cluster_id),
    CONSTRAINT chk_news_issue_link_votes_judge_by CHECK (judge_by IN ('scope_judge', 'plan_reader'))
);

CREATE INDEX IF NOT EXISTS idx_news_issue_link_votes_cluster
    ON news_issue_link_votes (cluster_id, created_at);

CREATE TABLE IF NOT EXISTS news_issue_link_llm_calls (
    cache_key            TEXT        PRIMARY KEY,
    stage                TEXT        NOT NULL,
    model                TEXT        NOT NULL,
    prompt_version       TEXT        NOT NULL,
    sample               SMALLINT    NOT NULL DEFAULT 0,
    cluster_id           BIGINT      NOT NULL REFERENCES news_clusters (id) ON DELETE CASCADE,
    candidate_cluster_id BIGINT      REFERENCES news_clusters (id) ON DELETE CASCADE,
    response             JSONB       NOT NULL,
    input_tokens         INT         NOT NULL DEFAULT 0,
    output_tokens        INT         NOT NULL DEFAULT 0,
    latency_ms           INT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_news_issue_link_llm_calls_cluster
    ON news_issue_link_llm_calls (cluster_id);
CREATE INDEX IF NOT EXISTS idx_news_issue_link_llm_calls_created
    ON news_issue_link_llm_calls (created_at);
