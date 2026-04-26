-- Idempotency replay cache: stores the captured response for a successful mutating
-- request keyed by (Idempotency-Key, actor). A retry within the TTL replays the same
-- status/headers/body without re-invoking the controller. Scoped per actor so one
-- caller's key cannot be replayed by another.

CREATE TABLE idempotency_record (
    idempotency_key  VARCHAR(255)  NOT NULL,
    actor_id         UUID          NOT NULL,
    request_method   VARCHAR(10)   NOT NULL,
    request_path     VARCHAR(2048) NOT NULL,
    request_hash     BYTEA         NOT NULL,
    response_status  SMALLINT      NOT NULL,
    response_headers JSONB         NOT NULL,
    response_body    BYTEA,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (idempotency_key, actor_id)
);

CREATE INDEX idx_idempotency_created_at ON idempotency_record (created_at);
