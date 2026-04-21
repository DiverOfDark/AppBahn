---
title: Operator Tunnel Architecture
description: How the AppBahn operator communicates with the platform over a Connect-protocol tunnel.
---

The AppBahn operator talks to the platform over a single **Connect-protocol tunnel** — one service, five RPCs, plain HTTPS on port 443. This page explains why, and what it means for installers.

## At a glance

- **Wire protocol:** Connect over HTTP/1.1 + TLS. Any HTTP/1.1-capable ingress controller (NGINX, Traefik, HAProxy, Cilium Gateway, any cloud load balancer) works without special configuration. No gRPC backend-protocol annotations, no h2c, no dedicated port.
- **Five RPCs on `/appbahn.tunnel.v1.OperatorTunnel/*`:**
  - `RegisterCluster` (unary, **unauthenticated**) — operator advertises its public key on startup; platform UPSERTs the cluster row and runs auto-approval. The only RPC the operator can call before the platform has a key to verify against.
  - `SubscribeCommands` (server-stream) — platform pushes imperative commands (apply/delete CR, trigger full resync, admin-config / quota-RBAC snapshots). The open stream is the operator's liveness signal: the platform refreshes `cluster.last_heartbeat_at` from the drain loop.
  - `PushEvents` (unary) — operator batches status events, sync batches, admission approvals.
  - `AckCommand` (unary) — operator acknowledges each correlation-id.
  - `Goodbye` (unary) — operator announces graceful shutdown so the platform clears session state immediately.
- **Auth:** operator-minted RS256 JWTs in `Authorization: Bearer` for every RPC except `RegisterCluster`. The platform verifies per RPC against the public key it stored during registration.

## Registration and approval

On first start, the operator generates an RSA-2048 keypair and persists it as a Kubernetes Secret (`appbahn-operator-key`) in its own namespace. The Secret holds both `private-key` (operator-only) and `public-key` (platform-readable). The operator then calls `RegisterCluster` on the tunnel.

In single-cluster mode (the platform and operator running in the same cluster), the platform reads the `public-key` field of that same Secret. If it matches the key the operator advertised, the cluster is **auto-approved**. Remote clusters remain `PENDING` until a human admin approves them.

Registration is **idempotent**. The operator sends `RegisterCluster` on every startup — it does not check whether the cluster already exists on the platform. The platform UPSERTs by cluster name and re-runs the auto-approval public-key match each time, so re-registering an already-approved cluster with the same keypair is a no-op (operator restarts, pod replacements, etc. all behave correctly).

## Why Connect (and why not gRPC)

gRPC requires HTTP/2 end-to-end, which punches a hole through ingress controllers, load balancers, and customer environments where HTTP/2 is off by default or simply broken. Connect gives us the same typed-proto programming model but stays HTTP/1.1-compatible — so it rides the platform's existing hostname and port on any ingress controller.

## Installation

The helm chart sets `OPERATOR_TUNNEL_PLATFORMBASEURL` to the in-cluster platform Service by default, so single-cluster installs need zero extra configuration. The operator manages its own RSA keypair as a Kubernetes Secret (`appbahn-operator-key`); the platform stores only the public key registered for each cluster.
