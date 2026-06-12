---
title: Observability
description: How AppBahn surfaces metrics, logs, and Kubernetes events for a resource, and how the metrics and log providers are configured.
---

AppBahn gives every resource three observability signals: **metrics** (CPU, memory, network
time-series), **logs** (container output), and **Kubernetes events** (scheduling, image pulls,
restarts). All three are read on demand through the operator tunnel — the platform never reaches into
your cluster directly. A request travels over the tunnel, the operator runs it against the in-cluster
backend, and the result flows back.

This page is the overview. For the full surface of each signal — endpoints, query parameters, console
behaviour, and CLI flags — follow the per-signal pages:

- [Metrics](/docs/metrics/) — CPU, memory, and network charts, and the Prometheus provider.
- [Logs](/docs/logs/) — container logs, the live stream, and the Victoria Logs provider.

## The signals

### Metrics

Metrics are time-series read from a **metrics provider** (reference: Prometheus, queried with PromQL).
Each query returns one series per pod backing the resource, so a single misbehaving replica is easy to
spot. The console's **Metrics** tab renders CPU, memory, network-in, and network-out charts; the CLI
reads one series at a time with `appbahn resource metrics`. See [Metrics](/docs/metrics/) for endpoints,
ranges, and the pod filter.

### Logs

Logs are container output read from a **log provider** (reference: Victoria Logs, queried with LogsQL).
A snapshot returns the most recent lines newest-first; a live stream tails new lines as they arrive.
The console's **Logs** tab offers a live tail and a per-deployment build-log view; the CLI reads them
with `appbahn resource logs` (add `--follow` to tail). See [Logs](/docs/logs/) for endpoints, the
stream frame types, and CLI flags.

### Kubernetes events

Kubernetes events explain _why_ a resource is in its current state — scheduling decisions, image pulls,
container restarts, and out-of-memory kills. They ride the same live stream as logs: the
`GET /api/v1/resources/{slug}/logs/stream` Server-Sent Events endpoint emits `k8s_event` frames
alongside `log` frames. Surfaced reasons include Scheduled, Pulling/Pulled, Created/Started, Killing,
BackOff, Failed, OOMKilled, Evicted, Unhealthy, FailedScheduling, ScalingReplicaSet, and
SuccessfulRescale. In the console they appear inline in the Logs tab as amber-bordered system messages
with a `Normal`/`Warning` badge, so a `BackOff` or `OOMKilled` stands out from ordinary output. Events
flow even when no log provider is configured, so the event timeline keeps working without Victoria Logs.

## From the CLI

Both signals are available under the `resource` command and respect `-o table|json|yaml`:

```bash
# Recent logs, then a live tail
appbahn resource logs my-app-abc1234
appbahn resource logs my-app-abc1234 --follow

# CPU over the last hour
appbahn resource metrics my-app-abc1234 --metric-type cpu
```

The per-signal pages document every flag: [logs](/docs/logs/#viewing-logs-from-the-cli) and
[metrics](/docs/metrics/#viewing-metrics-from-the-cli).

## Configuring the providers

Metrics and logs are each backed by a pluggable provider. The operator runs the query, so the backend
endpoint is set on the operator; the platform separately records _that_ a provider exists so it can
degrade gracefully.

```yaml
operator:
  providers:
    metrics:
      type: prometheus
      endpoint: http://prometheus.observability.svc:9090
    logs:
      type: victoria-logs
      endpoint: http://victoria-logs.observability.svc:9428

platform:
  providers:
    metrics:
      type: prometheus
    logs:
      type: victoria-logs
```

## Graceful degradation

Observability is optional. A cluster without a metrics or log provider keeps working — only the
corresponding view is empty, never an error:

- **No metrics provider** — metric endpoints return a well-formed response with the resolved time
  window but no series, and a `message` of `Metrics not available — no metrics provider configured`.
  The console shows that message in place of the charts.
- **No log provider** — the logs endpoint returns no lines with a `message` of
  `Logs not available — no log provider configured`, and the live stream emits only `k8s_event`
  frames. The Kubernetes event timeline keeps working regardless.

This means you can adopt AppBahn first and wire up Prometheus or Victoria Logs later; nothing breaks in
the meantime.
