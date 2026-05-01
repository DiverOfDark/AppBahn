-- V016: enforce one in-flight deployment audit row per (image source, source commit). Defense
-- in depth against operator-side reconcile races and platform-side concurrent-handler races
-- that would otherwise insert duplicate rows for the same build.
--
-- Terminal states (FAILED, SUPERSEDED, CANCELED, ACTIVE) are intentionally excluded from the
-- predicate. ACTIVE is the long-lived "current rollout" row; FAILED/SUPERSEDED/CANCELED are
-- historical. A new build for the same commit after one of those is allowed to mint a fresh
-- row — the constraint targets duplicates of in-flight builds, not legitimate retries.

-- Dedup any pre-existing duplicate in-flight rows: keep the row with the most advanced
-- lifecycle (BUILT > BUILDING > QUEUED) and oldest createdAt as the canonical row; mark the
-- losers as SUPERSEDED so the partial unique index can be created. This is idempotent — on a
-- clean DB the WITH clause picks no rows.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY image_source_namespace, image_source_name, source_ref
               ORDER BY CASE lifecycle
                   WHEN 'ACTIVATING' THEN 0
                   WHEN 'BUILT'    THEN 1
                   WHEN 'BUILDING' THEN 2
                   WHEN 'QUEUED'   THEN 3
                   ELSE 99
               END,
               created_at ASC
           ) AS rn
    FROM deployment
    WHERE image_source_name IS NOT NULL
      AND source_ref IS NOT NULL
      AND lifecycle IN ('QUEUED', 'BUILDING', 'BUILT', 'ACTIVATING')
)
UPDATE deployment SET lifecycle = 'SUPERSEDED'
 WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_deployment_inflight_per_build
    ON deployment (image_source_namespace, image_source_name, source_ref)
    WHERE image_source_name IS NOT NULL
      AND source_ref IS NOT NULL
      AND lifecycle IN ('QUEUED', 'BUILDING', 'BUILT', 'ACTIVATING');
