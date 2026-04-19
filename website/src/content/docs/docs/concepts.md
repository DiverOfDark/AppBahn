---
title: Concepts
description: Key concepts in the AppBahn platform.
---

AppBahn organizes your applications into a four-level hierarchy. Understanding these concepts is essential for working with the platform.

## Workspaces

A **Workspace** is the top-level organizational unit and the multi-tenancy boundary in AppBahn. It represents a team, department, or tenant. Each workspace has its own set of projects, members, and resource quotas.

Every workspace has members with one of four roles:

- **Owner** — full control over the workspace, including deletion and member management.
- **Admin** — can manage projects, environments, resources, and members below the Admin level.
- **Editor** — can manage resources within existing projects and environments.
- **Viewer** — read-only access to everything in the workspace.

### Adding members

Members can be added by email. If the user already exists in the system, they are added immediately (status: **active**). If the user hasn't logged in yet, a **pending invitation** is created. Pending invitations are automatically converted to active memberships when the user first logs in via OIDC with a matching email.

Pending invitations expire and are cleaned up after 90 days.

### OIDC group mappings

OIDC group mappings auto-assign roles when users log in through your identity provider. Mappings are resolved **per request** from the JWT `groups` claim. If a user has both a direct membership and group mapping, the **highest** role wins.

### Role overrides

Workspace roles apply everywhere by default. You can **elevate** (never restrict) a member's role at the project or environment level. The effective role at any level is: `max(workspace_role, project_override, environment_override)`.

Deleting a workspace is blocked (HTTP 409) if it still contains projects. Remove all projects first.

## Projects

A **Project** groups related applications and services within a workspace. Projects contain environments that represent the stages of your delivery pipeline. Creating a project requires the **Admin** role or higher.

Deleting a project is blocked (HTTP 409) if it still contains environments. Remove all environments first.

## Environments

An **Environment** maps to a Kubernetes namespace and represents a deployment stage (e.g. `dev`, `staging`, `production`). Each environment has its own set of deployed resources and configuration. Creating an environment requires the **Admin** role or higher.

Namespace pattern: `abp-{environmentSlug}`

Deleting an environment **cascades** — the corresponding Kubernetes namespace and all resources inside it are removed.

## Resources

A **Resource** is a deployable unit — a container, database, or other service — that runs inside an environment. Resources are defined as Kubernetes Custom Resources (CRDs) and managed by the AppBahn Operator. Full CRUD on resources requires the **Editor** role or higher.

## Environment tokens

**Environment tokens** provide API access scoped to a single environment, intended for CI/CD pipelines. Tokens have the format `abp_` followed by 40 random alphanumeric characters.

- Tokens are scoped to one environment with a role of **Editor** or **Viewer**.
- A maximum lifetime is enforced (configurable via `platform.tokens.max-lifetime-days`, default: 365 days). Non-expiring tokens are not supported.
- The raw token value is only shown **once** at creation time — store it securely.
- Tokens are validated before OIDC JWTs (the `abp_` prefix triggers token-based auth).

## Audit log

Every mutation in AppBahn is recorded in an append-only audit log. Entries include the actor, action, target, and a diff of changes. The audit log can be queried per workspace or platform-wide (admin only).

Retention is configurable via `platform.audit.retention-days` (default: 365 days). A scheduled cleanup job removes entries older than the retention period.

## Slugs

Every workspace, project, environment, and resource receives a **slug** — a human-readable, immutable, DNS-valid identifier used in URLs, API paths, and Kubernetes object names.

Format: `{name-truncated-to-10-chars}-{7-random-alphanum}`

Examples: `acme-corp-a1b2c3d`, `backend-x9y8z7w`, `dev-env-m4n5o6p`

Slugs are assigned at creation time and never change, even if you rename the object.

## Roles and permissions

| Role   | Workspaces   | Projects           | Environments       | Resources | Members                    |
| ------ | ------------ | ------------------ | ------------------ | --------- | -------------------------- |
| Owner  | Edit, delete | Create/edit/delete | Create/edit/delete | Full CRUD | Manage all                 |
| Admin  | Edit         | Create/edit/delete | Create/edit/delete | Full CRUD | Manage Editors and Viewers |
| Editor | View         | View               | View               | Full CRUD | View                       |
| Viewer | View         | View               | View               | View      | View                       |

## Deletion rules

| Entity      | Behavior                                       |
| ----------- | ---------------------------------------------- |
| Workspace   | Blocked (409) if projects exist                |
| Project     | Blocked (409) if environments exist            |
| Environment | Cascades — deletes namespace and all resources |
