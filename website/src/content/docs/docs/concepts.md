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

### Run command

The image's own `ENTRYPOINT`/`CMD` is what the resource runs by default. Built images (Dockerfile, Buildpack, Nixpacks, Railpack, Peelbox) bake the start command into the image — AppBahn does not store a separate run command on the resource side.

To run a different command, set a Resource-level **command override**: `appbahn resource update <slug> --command /bin/sh,-c --args "alternate process"`. The override maps directly to the K8s container's `command` and `args`. Either may be set independently. Clear the override with `appbahn resource update <slug> --clear-command-override`; the container goes back to running the image's defaults on the next reconcile.

### Git webhook triggers

Resources backed by a git repository poll the upstream branch every 60 seconds by default. To skip the poll wait when you push, mint a webhook URL and paste it into your git provider (or any tool that can POST):

```
POST /api/v1/image-sources/{slug}/webhook/rotate
```

The response carries the full webhook URL once — store it now, it isn't readable again. The URL pattern is:

```
POST https://<your-appbahn-host>/api/v1/webhooks/<token>
```

The path token is the entire authentication signal — no headers, no signature, no body. Any tool that can issue a POST works (GitHub, GitLab, Bitbucket, Gitea, raw `curl` from a CI script). The operator re-pulls HEAD itself on every nudge, so there's no payload format to match.

Once a webhook arrives, the polling cadence relaxes to `intervalSecondsAfterWebhook` (1 hour by default) for as long as `webhookFreshnessSeconds` (1 day by default) — connected repos drop to near-zero polling traffic in steady state. If the webhook stops arriving, the operator automatically falls back to the fast cadence. Re-issuing `rotate` invalidates the previous token immediately.

### Rollback

Every resource keeps a deployment history. To roll back to a previous deployment without rebuilding, use `appbahn resource rollback <slug>` (or `--to <deployment-id>` for a specific row). The resource is pinned to the older artifact and re-rolls immediately. To clear the pin and resume tracking new builds, run `appbahn resource unpin <slug>`. Rollback works for every resource type, including those built from a git repo — there's no need to revert your source commit.

The deployment history is bounded by a retention policy. By default, AppBahn keeps the most recent 10 terminal-state deployment rows per resource (those in `superseded`, `failed`, or `canceled` lifecycles) and prunes older rows daily at 03:00. Rows that are referenced by an active `pinnedRelease` are always preserved regardless of age, so rollback never loses its target. In-flight and current rollouts are also never pruned. Retention is configurable via `platform.deployment.retention.maxBuildsPerResource` (default: 10), `platform.deployment.retention.enabled` (default: true), and `platform.deployment.retention.scheduleCron` (default: `0 0 3 * * *`). If you request rollback to a deployment id that has been pruned, the API returns a 404.

### Cancelling and retrying a deployment

A deployment in flight can be aborted from the **Deploys** tab on a resource: each row carries a **Cancel** button while it is still queued or building. Once the build finishes and the rollout starts, the row owns the rollout and cancel is no longer offered — use **Rollback** instead to revert. Failed or superseded deployment rows expose a **Retry** button that re-runs the same source (commit or image digest) as a new deployment. See [Deployment history](/docs/console/deployment-history/) for the full UI walk-through.

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
