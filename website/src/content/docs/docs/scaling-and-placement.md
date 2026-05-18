---
title: Scaling and Placement
description: Control where pods land, how rollouts happen, and how AppBahn protects them during disruption.
---

`HostingConfig` carries three knobs that shape _where_ a resource's pods run, _how_ they
roll out on every new revision, and _how many_ of them are kept available during voluntary
disruptions.

## Node pool

Pin a resource to a named node pool declared on the target cluster:

```yaml
hosting:
  cpu: 500m
  memory: 512Mi
  minReplicas: 2
  nodePool: gpu-l4
```

A node pool is a cluster-side catalogue entry that captures a `nodeSelector` and an optional
list of `tolerations`. When you pin a resource to `gpu-l4`, the operator stamps both onto
the pod template, so the pods only schedule onto nodes that match the selector and can
tolerate the pool's taints.

Available pools are declared per-cluster (see `ClusterConfig.nodePools`) and surfaced in
the console's resource-creation form as a segmented picker. Leaving `nodePool` unset means
"no placement constraint" — pods land on any schedulable node.

The platform rejects a resource pinned to a pool that the target cluster doesn't expose,
with the list of available pool names in the error message.

## Deploy strategy

Pick how Kubernetes rolls a new revision:

| Strategy   | Behavior                                                                     |
| ---------- | ---------------------------------------------------------------------------- |
| `Rolling`  | Default. Replaces pods gradually, keeping the service available.             |
| `Recreate` | Tear all old pods down before starting any new ones. Required when two       |
|            | versions of the workload can't coexist (exclusive locks, single-writer DBs). |

```yaml
hosting:
  deployStrategy: Recreate
```

## Pod disruption budget

When `pdb.minAvailable` is set, the operator reconciles a `policy/v1 PodDisruptionBudget`
alongside the Deployment. Kubernetes will refuse voluntary disruptions (node drains, pod
evictions) that would take the ready count below this floor.

```yaml
hosting:
  minReplicas: 3
  pdb:
    minAvailable: 2
```

Clearing the `pdb` field (or its `minAvailable`) deletes the previously-reconciled PDB.

## Updating an existing resource

These fields are part of `HostingConfig`, so they can be patched via the same
`PATCH /resources/{slug}` endpoint as `cpu`, `memory`, and replica counts. Operator picks
up the change on the next reconcile and applies it without redeploying the image.
