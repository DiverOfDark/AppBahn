-- Audit log schema evolution: typed diff columns, enum widening, hierarchy,
-- actor token tracking, and admission decision support.

-- Add new columns and widen enum types in a single pass
ALTER TABLE audit_log
    ADD COLUMN changes        JSONB,
    ADD COLUMN details        JSONB,
    ALTER COLUMN actor_source TYPE VARCHAR(32),
    ALTER COLUMN target_type  TYPE VARCHAR(32),
    ADD COLUMN workspace_id   UUID,
    ADD COLUMN project_id     UUID,
    ADD COLUMN environment_id UUID,
    ADD COLUMN actor_token_id UUID,
    ADD COLUMN decision       VARCHAR(20),
    ADD COLUMN denial_reason  TEXT;

-- Migrate data in a single table scan: enum rewrite + context extraction
UPDATE audit_log SET
    action       = UPPER(REPLACE(action, '.', '_')),
    target_type  = UPPER(target_type),
    actor_source = UPPER(actor_source),
    workspace_id = (context ->> 'workspaceId')::uuid;

-- Drop replaced columns
ALTER TABLE audit_log
    DROP COLUMN diff,
    DROP COLUMN context;

CREATE INDEX idx_audit_log_workspace   ON audit_log (workspace_id, timestamp);
CREATE INDEX idx_audit_log_project     ON audit_log (project_id, timestamp);
CREATE INDEX idx_audit_log_environment ON audit_log (environment_id, timestamp);
CREATE INDEX idx_audit_log_denied      ON audit_log (timestamp) WHERE decision = 'DENIED';
