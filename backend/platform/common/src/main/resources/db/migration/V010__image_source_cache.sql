-- ImageSource CRD cache. Mirrors the resource_cache pattern: one row per ImageSource CR,
-- written by the operator's tunnel sync, read by the platform API.
CREATE TABLE image_source_cache (
    slug             VARCHAR(63)  PRIMARY KEY,
    environment_id   UUID         REFERENCES environment(id) ON DELETE CASCADE,
    namespace        VARCHAR(253),
    spec             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status           JSONB,
    observed_commit  VARCHAR(64),
    image_ref        TEXT,
    last_polled_at   TIMESTAMPTZ,
    last_synced_at   TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_image_source_cache_environment ON image_source_cache(environment_id);
