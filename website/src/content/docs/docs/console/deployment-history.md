---
title: Deployment history
description: Cancel, retry, and inspect deployments from the AppBahn console.
---

Every resource keeps an append-only history of its deployments. Open a resource in the console and pick the **Deploys** tab to see one row per deployment, newest first. The current primary release is highlighted; replaced rows are dimmed.

## Environment overview

The environment page summarises deployment activity across all of its resources:

- The **Pipeline** panel shows the most recent deployment in the environment — its status, the resource it targeted, the image reference, and the timestamp. It refreshes automatically.
- The resources table carries a **Last Deploy** column, so you can see at a glance when each workload last shipped.

## Status pills

Each row carries one of these badges:

- **Current** — this row is the primary release receiving traffic.
- **Replaced** — completed successfully, then superseded by a newer deployment.
- **Queued** / **Building** — the platform is fetching source or building an image.
- **Built** / **Activating** — image is ready; the operator is rolling it out.
- **Failed** / **Canceled** — terminal failure or operator-cancelled.

## Cancelling a deployment

While a deployment is **Queued** or **Building**, its row exposes a **Cancel** button. Clicking it aborts the build job, marks the row `Canceled`, and writes an audit entry. Once a deployment reaches **Built**, the rollout owns it — the **Cancel** button is shown disabled with a tooltip explaining that you should use **Rollback** instead to revert to a previous release.

From the CLI:

```bash
appbahn deploy cancel <resource-slug> <deployment-id>
```

## Retrying a deployment

Failed, canceled, or superseded rows expose a **Retry** button. Clicking it mints a new deployment row that re-uses the original source (the same commit hash for git-backed resources, the same image digest for registry resources). The new row appears at the top of the list and goes through the standard build → rollout pipeline. Retry is the right tool when a build failed on a transient error (registry blip, flaky network, etc.); the source is unchanged.

From the CLI:

```bash
appbahn deploy retry <resource-slug> <deployment-id>
```

If you need to revert to an earlier good release instead of re-running the same one, use rollback (see [Rollback](/docs/concepts/#rollback)).
