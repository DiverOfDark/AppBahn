-- Add an optimistic-lock version column so ResourceService.create / ResourceSyncService.syncResource
-- can run their upsert with retry semantics instead of silently clobbering each other when the
-- platform create races the operator's sync callback on the same slug.
ALTER TABLE resource_cache
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
