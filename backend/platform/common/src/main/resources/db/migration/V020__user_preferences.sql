CREATE TABLE user_preferences (
    user_id     UUID        NOT NULL PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    default_workspace_slug VARCHAR(64),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
