---
title: API Reference
description: AppBahn REST API documentation.
---

The AppBahn API documentation is **auto-generated from the OpenAPI specification** and is always up to date with the running instance.

## Interactive API Docs

Every running AppBahn instance exposes interactive API documentation powered by [Scalar](https://scalar.com/) at:

```
https://<your-instance>/docs/api
```

Use the Scalar UI to explore endpoints, view request/response schemas, and try out API calls directly from your browser.

## OpenAPI Specifications

AppBahn defines two OpenAPI specs:

- **Public API** (`api/public-api.yaml`) — the primary API for workspaces, projects, environments, resources, and user management.
- **Internal API** (`api/internal-api.yaml`) — used for operator-to-platform communication.

These specs are the **source of truth** for all generated clients (TypeScript, Go) and server stubs (Spring Boot).

## Generated Clients

| Client     | Source                           | Usage                |
| ---------- | -------------------------------- | -------------------- |
| TypeScript | Generated from `public-api.yaml` | Web console (Vue.js) |
| Go         | Generated from `public-api.yaml` | CLI tool             |
| Spring     | Generated from both specs        | Backend server stubs |

## Idempotent retries

Every mutating endpoint (`POST`, `PUT`, `PATCH`, `DELETE`) accepts an optional `Idempotency-Key` request header. Pass any short, client-generated token (a UUID is a good choice). Within 24 hours, a retry from the same caller with the same key replays the original status, headers, and body — the controller is not re-invoked, so no duplicate work happens.

- **Same key + same body** → cached response replays. The reply carries `Idempotency-Replayed: true`.
- **Same key + different body** → `422 idempotency_key_reused`. Use a fresh key for a different request.
- **No key** → handled normally, no caching.

Keys are scoped per caller: a key from one user cannot be replayed by another. Records expire after 24 hours.

<!-- TODO: Add links to generated client packages and usage examples -->
