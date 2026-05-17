ALTER TABLE pending_invitation
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS invite_code (
    id           UUID         PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    code         VARCHAR(16)  NOT NULL UNIQUE,
    role         VARCHAR(20)  NOT NULL,
    created_by   UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ,
    redeemed_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    redeemed_at  TIMESTAMPTZ,
    max_uses     INT          NOT NULL DEFAULT 1,
    use_count    INT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_invite_code_workspace ON invite_code(workspace_id);
CREATE INDEX IF NOT EXISTS idx_invite_code_code ON invite_code(code);
