-- V021: query-style command responses on the operator tunnel
--
-- Read commands (list-pods, query-cluster-capacity) carry their result body on the ack —
-- store the JSON-encoded payload alongside the existing status/message so the
-- platform-side awaiter can hand a typed result back to the REST caller.
--
-- Action commands (apply-resource-bundle, delete-resource, ...) leave this column null.

ALTER TABLE pending_command
    ADD COLUMN response_payload TEXT;
