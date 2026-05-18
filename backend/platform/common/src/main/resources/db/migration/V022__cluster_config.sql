-- ============================================================
-- V022: per-cluster configuration (node-pool catalogue, ...)
-- ============================================================
-- The cluster row gains a typed-JSONB `config` column carrying
-- `ClusterConfig` (currently the node-pool catalogue). Pushed to
-- the operator via the admin-config snapshot and surfaced to the
-- SPA so the resource-creation form can populate the node-pool
-- picker.
ALTER TABLE cluster
    ADD COLUMN config JSONB;
