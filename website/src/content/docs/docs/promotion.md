---
title: Environment Promotion
description: Promote a build from one environment to the next without rebuilding the image.
---

## What promotion does

Environment promotion lets a resource in one environment reuse the exact image
already running in another environment of the same project. No second build,
no second push — same digest, new config.

A typical project has `dev`, `staging`, and `prod` environments. You build
once in `dev`, validate, then promote the resulting image to `staging`, then
to `prod`. Each downstream environment gets its own config (database URL,
replica count, ingress hostname), but the artifact bytes are identical.

## Picking a binding

When you create a resource with **source = Promote from environment**, you
choose how the downstream resource follows the upstream:

### Track latest

The downstream resource auto-promotes whenever the source environment's
resource publishes a new image. `dev` → `staging` is the canonical use case:
every successful `dev` build flows straight into `staging`, no human in the
loop.

- **Pick this when** the downstream environment is non-production and you
  want continuous flow from the upstream.
- The downstream resource has no fixed image digest. It mirrors whatever
  the upstream's `latestArtifact` resolves to.
- Promotion happens within a few seconds of the upstream publishing.

### Pin to digest

The downstream resource locks to a specific image digest you supply. The
upstream can publish a hundred more builds — this resource stays on the
exact digest you pinned.

- **Pick this when** the downstream environment is production, or any
  environment where each promotion should be a deliberate human action.
- Digest format: `sha256:` followed by 64 lower-case hex characters.
- To promote a newer build, edit the resource and update the pinned digest.

## How to promote

1. Open the target environment (the one you're promoting _into_).
2. Click **+ Create Resource**.
3. In the **Source** section, pick **Promote from environment**.
4. Pick the **source environment** (an environment in the same project that
   already has the resource).
5. Pick the **source resource**. Only resources that produce images
   (currently: `deployment`) appear in the list.
6. Choose **track latest** or **pin to digest**. For pin, paste the digest.
7. Fill out the rest of the form (name, runtime, ports, env vars) as usual.
   Promoted resources keep the source's image but get their own config.
8. Click **Create Resource**.

## Cluster boundaries

Promotion works **across clusters**. If the source environment lives on
`cluster-a` and the target lives on `cluster-b`, the platform mirrors the
image through the central registry. The downstream resource pulls from the
same registry path with the upstream's digest.

When source and target share a cluster, the operator reads the upstream's
`ImageSource` CRD directly — no registry round-trip on every reconcile.

## Limitations

- Only `deployment` resources can be promoted today. Cronjobs and other
  kinds will be promotable as they ship.
- The source and target must be in the **same project** (same workspace).
- You cannot promote into the same environment you're promoting from.
