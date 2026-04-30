-- Drop the legacy DockerSource / deploymentRevision path. Deployment lifecycle is now driven
-- exclusively by the operator's BuildLifecycleEvent stream into deployment.lifecycle.
ALTER TABLE deployment DROP COLUMN IF EXISTS status;

-- Strip legacy `source` key from any cached resource configs that still carry it. The new shape
-- uses Resource.spec.release + a sibling ImageSource CR. Idempotent — rows already in the new
-- shape have no `source` key under `config` and stay untouched.
UPDATE resource_cache
   SET config = config - 'source'
 WHERE config IS NOT NULL
   AND config ? 'source';

-- Strip legacy status fields from any cached resource statuses.
UPDATE resource_cache
   SET status_detail = status_detail
       - 'primaryDeploymentId'
       - 'primaryImage'
       - 'lastDeploymentTime'
       - 'latestDeploymentId'
       - 'latestDeploymentStatus'
 WHERE status_detail IS NOT NULL
   AND ( status_detail ? 'primaryDeploymentId'
      OR status_detail ? 'primaryImage'
      OR status_detail ? 'lastDeploymentTime'
      OR status_detail ? 'latestDeploymentId'
      OR status_detail ? 'latestDeploymentStatus' );
