CREATE TABLE favorite_folders (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(20)  NOT NULL,
    position    INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_favorite_folders_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_favorite_folders_user_position ON favorite_folders (user_id, position);

CREATE TABLE favorite_folder_items (
    folder_id    BIGINT       NOT NULL REFERENCES favorite_folders (id) ON DELETE CASCADE,
    favorite_id  BIGINT       NOT NULL REFERENCES favorites (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (folder_id, favorite_id)
);

CREATE INDEX idx_favorite_folder_items_favorite ON favorite_folder_items (favorite_id);
