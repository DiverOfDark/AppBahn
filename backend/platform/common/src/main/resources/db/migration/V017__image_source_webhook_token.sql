-- Per-ImageSource webhook capability token. The token is the entire authentication signal —
-- a row's existence is the authority. Plaintext storage is intentional: the token is an
-- opaque random capability (32 bytes base64url), not a password — there's no reusable secret
-- to protect. Knowing the token equals knowing the URL, so leaking the row leaks nothing the
-- URL doesn't already leak. Decoupled from image_source_cache because the cache is operator-
-- owned (every sync UPSERT overwrites every column); this table is platform-owned, written
-- when a user mints/rotates the token and read on every inbound webhook.
CREATE TABLE image_source_webhook_token (
    token             VARCHAR(64)  PRIMARY KEY,
    image_source_slug VARCHAR(63)  NOT NULL UNIQUE
                                        REFERENCES image_source_cache(slug) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
