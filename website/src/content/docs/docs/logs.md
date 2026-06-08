---
title: Logs
description: Query container logs for a resource, and configure the Victoria Logs-backed log provider.
---

AppBahn reads container logs for a resource through a **log provider**. The reference provider is
Victoria Logs, queried with LogsQL. The platform itself never talks to the cluster directly: a log
request travels over the operator tunnel, the operator runs the LogsQL against the in-cluster
Victoria Logs, and the matched lines flow back.

## Endpoint

```
GET /api/v1/resources/{slug}/logs
```

Returns the most recent log lines for the pods backing the resource, newest first.

### Query parameters

| Parameter      | Default         | Meaning                                              |
| -------------- | --------------- | ---------------------------------------------------- |
| `container`    | all containers  | Restrict the query to a single container.            |
| `pod`          | all pods        | Restrict the query to a single pod.                  |
| `deploymentId` | all deployments | Restrict to the pods of a specific deployment.       |
| `lines`        | `200`           | Maximum number of lines to return (capped at 5000).  |
| `since`        | unbounded       | Lower time bound. ISO 8601 (`2026-01-01T00:00:00Z`). |

Each returned line carries its `timestamp`, `message`, and the `pod` and `container` it came from.

## Live stream

For a live tail of logs together with Kubernetes events, open the Server-Sent Events stream:

```
GET /api/v1/resources/{slug}/logs/stream
```

The connection stays open and pushes two kinds of frames:

- `log` — a single container log line, tailed from the log provider as new lines arrive.
- `k8s_event` — a Kubernetes event for an object owned by the resource (its pods and ReplicaSets).
  Surfaced reasons: Scheduled, Pulling/Pulled, Created/Started, Killing, BackOff, Failed,
  OOMKilled, Evicted, Unhealthy, FailedScheduling, ScalingReplicaSet, SuccessfulRescale.

A periodic `keepalive` frame keeps the connection from idling out.

### Query parameters

| Parameter   | Default         | Meaning                                                  |
| ----------- | --------------- | -------------------------------------------------------- |
| `container` | all containers  | Restrict the `log` channel to a single container.        |
| `pod`       | all pods        | Restrict the `log` channel to a single pod.              |
| `since`     | now             | Lower time bound for the initial log backfill. ISO 8601. |
| `types`     | `log,k8s_event` | Comma-separated subset of frame types to receive.        |

When no log provider is configured the `log` channel is silently inactive and only `k8s_event`
frames flow — the stream still connects, so the console's event timeline keeps working without
Victoria Logs.

## Configuring the provider

Logs are read from a Victoria Logs endpoint configured on the operator only — the operator runs the
LogsQL in-cluster. Point it at your in-cluster Victoria Logs:

```yaml
operator:
  providers:
    logs:
      type: victoria-logs
      endpoint: http://victoria-logs.observability.svc:9428
```

Leave `type` as `NONE` (the default) to disable the log view. The platform needs no log
configuration: it always issues the query over the tunnel and degrades gracefully when the operator
reports no provider.

## When no provider is configured

If no log provider is configured, the endpoint still returns a well-formed response — there are no
lines, and the `message` field reads `Logs not available — no log provider configured`. The console
shows that message instead of an error, so a cluster without Victoria Logs keeps working; only the
log view is empty.
