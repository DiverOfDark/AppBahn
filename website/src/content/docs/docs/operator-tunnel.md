---
title: Operator Tunnel Architecture
description: How the AppBahn operator communicates with the platform over HTTPS — unary REST plus a single Server-Sent Events stream.
---

The AppBahn operator talks to the platform over a single **HTTPS tunnel** — a handful of REST endpoints plus one long-lived Server-Sent Events stream, all on port 443. This page explains why, and what it means for installers.

## At a glance

- **Wire protocol:** plain HTTP/1.1 + TLS. Unary calls are JSON `POST`s; platform → operator commands flow over a single `GET` that keeps its response body open as a Server-Sent Events stream. Any HTTP/1.1-capable ingress controller (NGINX, Traefik, HAProxy, Cilium Gateway, any cloud load balancer) works without special configuration. No gRPC backend-protocol annotations, no h2c, no dedicated port.
- **Endpoints on `/api/tunnel/v1/`:**
  - `POST /register` (**unauthenticated**) — operator advertises its public key on startup; platform UPSERTs the cluster row and runs auto-approval. The only call the operator can make before the platform has a key to verify against.
  - `GET /commands` (**SSE stream**) — platform pushes imperative commands (apply/delete CR, trigger full resync, admin-config / quota-RBAC snapshots) as `event:`/`data:` frames. The open stream is the operator's liveness signal: the platform refreshes `cluster.last_heartbeat_at` from the drain loop.
  - `POST /events` — operator batches status events, sync batches, admission approvals.
  - `POST /commands/{correlationId}/ack` — operator acknowledges each correlation-id.
  - `POST /goodbye` — operator announces graceful shutdown so the platform clears session state immediately.
- **Auth:** operator-minted RS256 JWTs in `Authorization: Bearer` for every endpoint except `POST /register`. The platform verifies per request against the public key it stored during registration.

## Registration and approval

On first start, the operator generates an RSA-2048 keypair and persists it as a Kubernetes Secret (`appbahn-operator-key`) in its own namespace. The Secret holds both `private-key` (operator-only) and `public-key` (platform-readable). The operator then calls `POST /api/tunnel/v1/register`.

In single-cluster mode (the platform and operator running in the same cluster), the platform reads the `public-key` field of that same Secret. If it matches the key the operator advertised, the cluster is **auto-approved**. Remote clusters remain `PENDING` until a human admin approves them.

Registration is **idempotent**. The operator registers on every startup — it does not check whether the cluster already exists on the platform. The platform UPSERTs by cluster name and re-runs the auto-approval public-key match each time, so re-registering an already-approved cluster with the same keypair is a no-op (operator restarts, pod replacements, etc. all behave correctly).

## Why REST + SSE (and why not gRPC)

gRPC requires HTTP/2 end-to-end, which punches a hole through ingress controllers, load balancers, and customer environments where HTTP/2 is off by default or simply broken. Plain REST for unary calls plus SSE for the server-push stream stays HTTP/1.1-compatible — so the tunnel rides the platform's existing hostname and port on any ingress controller.

## Installation

The helm chart sets `OPERATOR_TUNNEL_PLATFORMBASEURL` to the in-cluster platform Service by default, so single-cluster installs need zero extra configuration. The operator manages its own RSA keypair as a Kubernetes Secret (`appbahn-operator-key`); the platform stores only the public key registered for each cluster.
