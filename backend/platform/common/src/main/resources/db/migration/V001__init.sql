-- V001: Initial schema — all tables for AppBahn platform

-- ============================================================
-- Independent tables (no foreign keys)
-- ============================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    oidc_subject_id VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cluster (
    name              VARCHAR(63)  PRIMARY KEY,
    description       TEXT,
    kubeconfig_secret VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE platform_config (
    id     INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    config JSONB   NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE resource_type_definition (
    type           VARCHAR(63) PRIMARY KEY,
    definition     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    admin_config   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    last_synced_at TIMESTAMPTZ
);

-- ============================================================
-- Level 1: depends on users, cluster
-- ============================================================

CREATE TABLE workspace (
    id                 UUID         PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    slug               VARCHAR(18)  NOT NULL UNIQUE,
    quota              JSONB,
    runtime_class_name VARCHAR(255),
    registry           JSONB,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workspace_member (
    workspace_id UUID        NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role         VARCHAR(20) NOT NULL,
    PRIMARY KEY (workspace_id, user_id)
);

CREATE TABLE oidc_group_mapping (
    id           UUID        PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    oidc_group   VARCHAR(255) NOT NULL,
    role         VARCHAR(20) NOT NULL
);

CREATE TABLE notification_webhook (
    id           UUID         PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    url          TEXT         NOT NULL,
    events       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    secret       TEXT,
    created_by   UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE network_policy (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    workspace_id UUID         REFERENCES workspace(id) ON DELETE CASCADE,
    policy       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- Level 2: depends on workspace
-- ============================================================

CREATE TABLE project (
    id           UUID         PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    slug         VARCHAR(18)  NOT NULL UNIQUE,
    quota        JSONB,
    registry     JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE project_member_override (
    project_id UUID        NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL,
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE pending_invitation (
    id           UUID         PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    invited_by   UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (email, workspace_id)
);

-- ============================================================
-- Level 3: depends on project, cluster
-- ============================================================

CREATE TABLE environment (
    id             UUID         PRIMARY KEY,
    project_id     UUID         NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    slug           VARCHAR(18)  NOT NULL UNIQUE,
    description    TEXT,
    target_cluster VARCHAR(63)  NOT NULL DEFAULT 'local' REFERENCES cluster(name),
    quota          JSONB,
    registry       JSONB,
    approval_gates JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE environment_member_override (
    environment_id UUID        NOT NULL REFERENCES environment(id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role           VARCHAR(20) NOT NULL,
    PRIMARY KEY (environment_id, user_id)
);

CREATE TABLE environment_token (
    id             UUID         PRIMARY KEY,
    environment_id UUID         NOT NULL REFERENCES environment(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    token_hash     VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    expires_at     TIMESTAMPTZ,
    last_used_at   TIMESTAMPTZ,
    created_by     UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE resource_type_availability (
    type           VARCHAR(63) NOT NULL REFERENCES resource_type_definition(type) ON DELETE CASCADE,
    cluster_name   VARCHAR(63) NOT NULL REFERENCES cluster(name) ON DELETE CASCADE,
    available      BOOLEAN     NOT NULL DEFAULT false,
    last_synced_at TIMESTAMPTZ,
    PRIMARY KEY (type, cluster_name)
);

-- ============================================================
-- Level 4: depends on environment
-- ============================================================

CREATE TABLE resource_cache (
    slug           VARCHAR(18)  PRIMARY KEY,
    environment_id UUID         NOT NULL REFERENCES environment(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    type           VARCHAR(63)  NOT NULL,
    config         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status         VARCHAR(20),
    status_detail  JSONB,
    last_synced_at TIMESTAMPTZ
);

CREATE TABLE deployment (
    id                     UUID         PRIMARY KEY,
    resource_slug          VARCHAR(18)  NOT NULL,
    environment_id         UUID         NOT NULL REFERENCES environment(id) ON DELETE CASCADE,
    source_ref             VARCHAR(255),
    image_ref              TEXT,
    triggered_by           VARCHAR(20)  NOT NULL,
    status                 VARCHAR(30)  NOT NULL,
    is_primary             BOOLEAN      NOT NULL DEFAULT false,
    source_deployment_id   UUID         REFERENCES deployment(id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_deployment_primary
    ON deployment (resource_slug) WHERE is_primary = true;

CREATE TABLE deployment_approval (
    id            UUID        PRIMARY KEY,
    deployment_id UUID        NOT NULL REFERENCES deployment(id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    decision      VARCHAR(20) NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (deployment_id, user_id)
);

CREATE TABLE resource_exposure (
    id             UUID        PRIMARY KEY,
    resource_slug  VARCHAR(18) NOT NULL,
    environment_id UUID        NOT NULL REFERENCES environment(id) ON DELETE CASCADE,
    port           INTEGER     NOT NULL,
    external_port  INTEGER     NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_by     UUID        REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE (resource_slug, environment_id, port)
);

-- ============================================================
-- Auxiliary tables
-- ============================================================

CREATE TABLE webhook_delivery (
    id            UUID        PRIMARY KEY,
    webhook_id    UUID        NOT NULL REFERENCES notification_webhook(id) ON DELETE CASCADE,
    event         VARCHAR(63) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    response_code INTEGER,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE build_detection_job (
    id         UUID         PRIMARY KEY,
    status     VARCHAR(20)  NOT NULL,
    step       VARCHAR(30),
    result     JSONB,
    error      TEXT,
    url        TEXT         NOT NULL,
    branch     VARCHAR(255) NOT NULL,
    created_by UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX idx_build_detection_running
    ON build_detection_job (url, branch, created_by) WHERE status = 'RUNNING';

-- Audit log: partitioned by month
CREATE TABLE audit_log (
    id           UUID         NOT NULL,
    timestamp    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_id     UUID,
    actor_email  VARCHAR(255),
    actor_source VARCHAR(20)  NOT NULL,
    action       VARCHAR(63)  NOT NULL,
    target_type  VARCHAR(63)  NOT NULL,
    target_id    VARCHAR(255) NOT NULL,
    context      JSONB,
    diff         JSONB,
    request_id   VARCHAR(255),
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Create initial partitions (current month + next month)
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;

-- ============================================================
-- Indexes
-- ============================================================

CREATE INDEX idx_workspace_member_user ON workspace_member(user_id);
CREATE INDEX idx_project_workspace ON project(workspace_id);
CREATE INDEX idx_environment_project ON environment(project_id);
CREATE INDEX idx_resource_cache_environment ON resource_cache(environment_id);
CREATE INDEX idx_deployment_resource ON deployment(resource_slug);
CREATE INDEX idx_deployment_environment ON deployment(environment_id);
CREATE INDEX idx_audit_log_action ON audit_log(action, timestamp);
CREATE INDEX idx_audit_log_target ON audit_log(target_type, target_id, timestamp);
CREATE INDEX idx_environment_token_hash ON environment_token(token_hash);
CREATE INDEX idx_pending_invitation_email ON pending_invitation(email);

-- ============================================================
-- Seed data
-- ============================================================

INSERT INTO cluster (name, description, kubeconfig_secret)
VALUES ('local', 'In-cluster (default)', NULL);

INSERT INTO platform_config (id, config)
VALUES (1, '{}'::jsonb);
