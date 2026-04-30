-- V012: drop NOT NULL on `deployment.status` so ImageSource-driven rows (lifecycle-only) no
-- longer need a bridge value. The legacy resource-driven flow continues to populate `status`
-- until the API/CLI/console migrate off it.

ALTER TABLE deployment ALTER COLUMN status DROP NOT NULL;
