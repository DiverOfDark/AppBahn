-- Drop the obsolete runCommand field from JSONB columns. The image's own ENTRYPOINT/CMD is
-- authoritative; Resource.spec.commandOverride is the explicit-override path.
-- Idempotent: `col - 'runCommand'` is a no-op when the key is absent.

UPDATE resource_cache
SET pinned_release = pinned_release - 'runCommand'
WHERE pinned_release ? 'runCommand';

UPDATE resource_cache
SET status_detail = jsonb_set(
        status_detail,
        '{activeRelease}',
        (status_detail -> 'activeRelease') - 'runCommand'
    )
WHERE status_detail -> 'activeRelease' ? 'runCommand';

UPDATE image_source_cache
SET status = jsonb_set(
        status,
        '{latestArtifact}',
        (status -> 'latestArtifact') - 'runCommand'
    )
WHERE status -> 'latestArtifact' ? 'runCommand';

UPDATE image_source_cache
SET spec = jsonb_set(
        spec,
        '{image}',
        (spec -> 'image') - 'runCommand'
    )
WHERE spec -> 'image' ? 'runCommand';

-- Resource-level container entrypoint/args override.
ALTER TABLE resource_cache
    ADD COLUMN command_override JSONB;
