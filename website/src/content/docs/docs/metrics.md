---
title: Metrics
description: Query CPU, memory, and network time-series for a resource, and configure the Prometheus-backed metrics provider.
---

AppBahn reads time-series metrics for a resource through a **metrics provider**. The reference
provider is Prometheus, queried with PromQL. The platform itself never talks to the cluster
directly: a metrics request travels over the operator tunnel, the operator runs the PromQL
against the in-cluster Prometheus, and the result flows back as one series per pod.

Metrics are read from a Prometheus that follows the standard
[kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack)
conventions — the cAdvisor series scraped by that stack, labelled by `pod`:

| Metric      | Series                                   |
| ----------- | ---------------------------------------- |
| CPU         | `container_cpu_usage_seconds_total`      |
| Memory      | `container_memory_working_set_bytes`     |
| Network in  | `container_network_receive_bytes_total`  |
| Network out | `container_network_transmit_bytes_total` |

Any Prometheus that exposes these series with the standard names and a `pod` label works.

## Endpoints

Each endpoint returns one series per pod backing the resource.

| Endpoint                                                | Unit               |
| ------------------------------------------------------- | ------------------ |
| `GET /api/v1/resources/{slug}/metrics/cpu`              | Cores (fractional) |
| `GET /api/v1/resources/{slug}/metrics/ram`              | Bytes              |
| `GET /api/v1/resources/{slug}/metrics/network/inbound`  | Bytes per second   |
| `GET /api/v1/resources/{slug}/metrics/network/outbound` | Bytes per second   |

### Query parameters

| Parameter | Default  | Meaning                                                                                                                  |
| --------- | -------- | ------------------------------------------------------------------------------------------------------------------------ |
| `start`   | `-1h`    | Range start. ISO 8601 (`2026-01-01T00:00:00Z`) or relative (`-1h`, `-24h`, `-7d`).                                       |
| `end`     | `now`    | Range end. ISO 8601 or `now`.                                                                                            |
| `step`    | auto     | Resolution in seconds. Auto-calculated as `max(range / 200, 60)` — at most 200 data points, never finer than 60 seconds. |
| `pod`     | all pods | Restrict the query to a single pod.                                                                                      |

The response echoes the resolved `start`, `end`, and `step` alongside the per-pod `series`.

## Viewing metrics in the console

Open a resource and select the **Metrics** tab. The tab renders four line charts — CPU, memory,
network in, and network out — with one line per pod so you can spot a single misbehaving replica.

- **Time range.** A selector across the top switches the window between 15 minutes, 1 hour, 6 hours,
  and 24 hours. Each range picks a step that keeps the chart readable, and the charts re-query when
  you change it.
- **Pod filter.** When more than one pod backs the resource, a pod selector narrows every chart to a
  single pod.
- **Limit thresholds.** Where the resource declares a CPU or memory limit, a dashed amber line marks
  that limit on the corresponding chart, so it is obvious when usage approaches the ceiling.

The charts refresh on their own every 30 seconds while the tab is open.

## Configuring the provider

The operator runs the PromQL, so the Prometheus endpoint is set on the operator. Point it at
your in-cluster Prometheus:

```yaml
operator:
  providers:
    metrics:
      type: prometheus
      endpoint: http://prometheus.observability.svc:9090
```

That is the only place the provider is configured. The platform holds no metrics configuration —
it always forwards the query over the tunnel, and the operator reports back whether a provider is
available.

## When no provider is configured

If no metrics provider is configured, the operator reports it as unavailable and the endpoints
still return a well-formed response with the resolved time window — there is no series data, and
the `message` field reads `Metrics not available — no metrics provider configured`. The console
shows that message instead of an error, so a cluster without Prometheus keeps working; only the
charts are empty.
