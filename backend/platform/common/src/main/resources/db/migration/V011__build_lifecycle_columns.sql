-- V011: extra columns on `deployment` to record builds driven by ImageSource lifecycle
-- events. Coexists with the existing `status` column — `lifecycle` is populated only for
-- ImageSource-driven rows; legacy resource flow keeps using `status`. PR3 retires `status`.

ALTER TABLE deployment
    ADD COLUMN lifecycle              VARCHAR(20),
    ADD COLUMN image_source_name      VARCHAR(255),
    ADD COLUMN image_source_namespace VARCHAR(63),
    ADD COLUMN error_message          TEXT;

CREATE INDEX idx_deployment_image_source ON deployment (image_source_namespace, image_source_name);

-- ImageSource-driven deployments are minted before any Resource exists; keep resource_slug
-- as a soft pointer (filled from imageSourceName when no Resource) and drop the foreign key
-- to resource_cache. PR3's Resource refactor will retire resource_slug entirely.
ALTER TABLE deployment
    DROP CONSTRAINT IF EXISTS fk_deployment_resource_slug;
