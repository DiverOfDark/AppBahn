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

| Client     | Source                         | Usage               |
|------------|--------------------------------|----------------------|
| TypeScript | Generated from `public-api.yaml` | Web console (Vue.js) |
| Go         | Generated from `public-api.yaml` | CLI tool             |
| Spring     | Generated from both specs       | Backend server stubs |

<!-- TODO: Add links to generated client packages and usage examples -->
