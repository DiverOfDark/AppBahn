---
title: Workspaces, projects, environments
date: 2026-04-08
summary: The four-level hierarchy lands — workspace → project → environment → resource — with slugs, URLs, and RBAC scopes at every tier.
---

AppBahn's domain model, materialised. Every object has a slug (`name-abcdefg`),
a URL that matches the console path, and a tier in the hierarchy that determines
where RBAC and quota decisions are made.

### What shipped

- Workspace CRUD + slug generation.
- Project CRUD scoped under a workspace.
- Environment CRUD scoped under a project.
- Resource slots prepared (deploy path lands with the first Docker deploy).
- Console URL pattern: `/console/{wsSlug}/{projSlug}/{envSlug}/{resSlug}`.
- API path pattern: `/api/v1/workspaces/{slug}`, etc.

### Why this matters

Every subsequent feature — quotas, RBAC, deploy tokens, audit — attaches to one of
these four tiers. Getting the hierarchy shape right up front was the only way to
avoid re-plumbing it later.
