-- Add missing updated_at column to tables whose entities extend BaseEntity
ALTER TABLE environment_token ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE notification_webhook ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE oidc_group_mapping ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE oidc_group_mapping ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
