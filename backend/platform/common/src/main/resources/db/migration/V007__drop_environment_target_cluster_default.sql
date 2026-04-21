-- The "local" default survived even after V006 deleted the seeded cluster row,
-- so new environments still picked up a FK to a non-existent cluster when the
-- caller didn't specify one. The service now resolves targetCluster explicitly
-- from the set of approved clusters before insert, so the column default is
-- obsolete. Keep NOT NULL and the FK on cluster(name).
ALTER TABLE environment ALTER COLUMN target_cluster DROP DEFAULT;
