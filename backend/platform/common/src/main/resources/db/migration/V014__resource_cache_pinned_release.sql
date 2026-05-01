-- Resource.spec.pinnedRelease snapshot. Set by POST /resources/{slug}/rollback to pin the
-- Resource onto a previous deployment's artifact without rebuilding (Vercel/Railway/Heroku-style
-- fast rollback). NULL means the Resource follows the bound ImageSource's latestArtifact.
ALTER TABLE resource_cache
    ADD COLUMN pinned_release JSONB;
