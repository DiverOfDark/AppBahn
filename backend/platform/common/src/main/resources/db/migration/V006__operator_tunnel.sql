-- V006: Operator tunnel
--
-- Schema backing the operator ↔ platform tunnel: cluster registration + public key,
-- session ownership, pending-command queue, full-sync chunk buffer. Forward-only;
-- earlier migrations (V001–V005) are deliberately not touched.

-- ---------------------------------------------------------------------------
-- cluster: drop kubeconfig, add tunnel registration + session tracking fields
-- ---------------------------------------------------------------------------

ALTER TABLE cluster DROP COLUMN kubeconfig_secret;

ALTER TABLE cluster
    ADD COLUMN public_key             TEXT,
    ADD COLUMN public_key_fingerprint VARCHAR(64),
    ADD COLUMN status                 VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN operator_version       VARCHAR(64),
    ADD COLUMN operator_instance_id   UUID,
    ADD COLUMN last_heartbeat_at      TIMESTAMPTZ,
    ADD COLUMN connected_replica_id   VARCHAR(64),
    ADD COLUMN approved_at            TIMESTAMPTZ,
    ADD COLUMN approved_by            UUID         REFERENCES users(id) ON DELETE SET NULL,
    -- Bumped by PushEventsHandler whenever the operator reports an admission-cache miss.
    -- The subscribing replica's drain loop watches this column and force-pushes the
    -- current QuotaRbacCachePush snapshot (bypassing the revision dedupe) so the
    -- operator's admission cache reconverges within the next drain tick.
    ADD COLUMN last_admission_miss_at TIMESTAMPTZ;

ALTER TABLE cluster
    ADD CONSTRAINT cluster_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED'));

-- Drop the V001-seeded 'local' placeholder. Auto-approval in
-- ClusterRegistrationService UPSERTs the row on the operator's first register call,
-- so a pre-seeded one would just have to be reconciled with the real public_key
-- afterwards. Existing FKs on environment.target_cluster and
-- resource_type_availability.cluster_name are preserved — callers create the
-- cluster row before any FK reference.
DELETE FROM cluster WHERE name = 'local';

-- ---------------------------------------------------------------------------
-- cluster_session: which platform replica currently holds the SubscribeCommands stream
-- ---------------------------------------------------------------------------
-- Liveness lives on cluster.last_heartbeat_at alone; session existence + session_id
-- equality convey ownership, and a zombie session row owned by a crashed replica is
-- detected via the cluster timestamp going stale (only the session holder writes it).

CREATE TABLE cluster_session (
    cluster_name            VARCHAR(63)  PRIMARY KEY REFERENCES cluster(name) ON DELETE CASCADE,
    subscribing_replica_id  VARCHAR(64)  NOT NULL,
    session_id              UUID         NOT NULL,
    operator_instance_id    UUID         NOT NULL,
    connected_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- pending_command: async queue for platform → operator commands
-- ---------------------------------------------------------------------------

CREATE TABLE pending_command (
    id                  UUID          PRIMARY KEY,
    cluster_name        VARCHAR(63)   NOT NULL REFERENCES cluster(name) ON DELETE CASCADE,
    correlation_id      UUID          NOT NULL UNIQUE,
    command_type        VARCHAR(32)   NOT NULL,
    payload             BYTEA         NOT NULL,
    enqueued_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    claimed_by_replica  VARCHAR(64),
    claimed_at          TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    acked_at            TIMESTAMPTZ,
    response_status     VARCHAR(32),
    response_message    TEXT,
    expires_at          TIMESTAMPTZ   NOT NULL
);

-- Consumer claim query: pick unacked commands for a cluster, oldest first.
CREATE INDEX idx_pending_command_claim
    ON pending_command (cluster_name, enqueued_at)
    WHERE acked_at IS NULL;

-- Sweeper query: expire unacked rows past expires_at.
CREATE INDEX idx_pending_command_expiry
    ON pending_command (expires_at)
    WHERE acked_at IS NULL;

-- ---------------------------------------------------------------------------
-- full_sync_chunk_buffer: staging for FullResourceSyncChunk events
-- ---------------------------------------------------------------------------
-- Chunks from one operator full-sync pass may land on different platform replicas
-- (PushEvents is stateless). Each replica persists its incoming chunks here; the
-- replica that receives the chunk with `complete=true` runs the set-diff commit
-- against resource_cache in one transaction and purges all rows for the session.

CREATE TABLE full_sync_chunk_buffer (
    cluster_name      VARCHAR(63)  NOT NULL REFERENCES cluster(name) ON DELETE CASCADE,
    sync_session_id   UUID         NOT NULL,
    chunk_index       INTEGER      NOT NULL,
    payload           BYTEA        NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (cluster_name, sync_session_id, chunk_index)
);

CREATE INDEX idx_full_sync_chunk_session
    ON full_sync_chunk_buffer (cluster_name, sync_session_id);
