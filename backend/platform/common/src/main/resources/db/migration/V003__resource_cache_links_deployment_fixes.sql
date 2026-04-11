ALTER TABLE resource_cache
    ADD COLUMN links      JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE deployment
    ADD CONSTRAINT fk_deployment_resource_slug
        FOREIGN KEY (resource_slug) REFERENCES resource_cache(slug) ON DELETE CASCADE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_deployment_source_deployment ON deployment(source_deployment_id);
