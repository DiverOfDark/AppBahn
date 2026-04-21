---
title: Teams, RBAC, OIDC group mapping, deploy tokens, quotas
date: 2026-04-09
summary: Four-role RBAC, OIDC group-to-role mapping (Keycloak, Okta, Microsoft Entra ID, Google Workspace), deploy tokens, and per-workspace quotas.
---

The platform-as-a-product layer. Before this release, AppBahn had no opinion on
how an organisation would actually be modelled inside it. After this release, it
does.

### What shipped

- Four built-in roles: `owner`, `deployer`, `developer`, `viewer`, scoped per
  environment.
- OIDC provider plumbing with group-to-role mapping for Keycloak, Okta, Microsoft
  Entra ID, and Google Workspace.
- Short-lived deploy tokens with rotation and revocation endpoints.
- Per-workspace quotas for CPU, memory, resource count.
- Admin flow for invite → accept → role assignment.

### Why this matters

Self-hosted platforms that hand-wave authorisation do not survive contact with
real organisations. The roles here are intentionally boring; the point is that
your existing IdP drives them, and the quota enforcement is real rather than
advisory.
