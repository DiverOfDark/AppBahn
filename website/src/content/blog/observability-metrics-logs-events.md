---
title: 'Observability: metrics, logs, and events, read from your cluster'
summary: AppBahn now surfaces per-pod CPU and memory charts, a live log stream, and the Kubernetes event feed for every resource — sourced from your own cluster, with no data leaving it.
date: 2026-06-07
author: AppBahn
tags: [observability, kubernetes, platform]
---

A PaaS that deploys your code but hides what happens next is half a tool. Once a
resource is running, the questions are immediate: is it using the memory you gave
it, why did the last roll fail, what did the container print at 03:14. AppBahn now
answers all three from inside the console, and it reads everything from your own
cluster.

### Three signals, one resource view

Each resource gets two new tabs alongside its deployment history.

- **Metrics.** CPU and memory, charted per pod, with a time-range selector
  (`5m`, `1h`, `6h`, `24h`). The charts read from Prometheus via the operator
  tunnel, so the numbers are the same ones `kubectl top` would show — `0.24 vCPU`,
  `412 MB` — not an approximation. Per-pod series make a noisy-neighbour pod
  obvious at a glance, and the threshold lines mark the requests and limits you
  set on the resource.
- **Logs.** A live stream over Server-Sent Events. Application stdout/stderr and
  the Kubernetes event feed for the resource arrive in the same pane, interleaved
  by timestamp, so a `CrashLoopBackOff` event sits next to the stack trace that
  caused it. Build logs use the same viewer, so a failed image build reads the
  same way a failed runtime does.
- **Events.** The raw Kubernetes event stream for the resource — scheduling
  decisions, image pulls, probe failures — surfaced without a `kubectl describe`.

### It runs on your infrastructure

The data path matters as much as the data. Metrics come from Prometheus; logs
come from Victoria Logs. Both are part of an **opt-in** observability stack that
installs on your cluster through the Helm chart, and both stay there. AppBahn
queries them over the operator tunnel — the same authenticated channel the
operator already uses — and renders the result. No log line and no metric sample
is copied off your cluster to reach the console.

If you do not enable the stack, the tabs degrade rather than break: the providers
report unavailable and the console says so, in plain text, instead of showing a
spinner that never resolves. That graceful-degradation contract is the same one
the rest of the platform follows — a provider that is not configured is a stated
fact, not an error.

### From the CLI too

The same signals are available without the browser:

```
appbahn resource logs api --follow
appbahn resource metrics api --range 1h
```

`logs` streams the SSE feed to your terminal; `metrics` prints the per-pod CPU and
memory series. Both speak to the same endpoints the console uses, so the console
and the CLI never disagree about what a resource is doing.

### Where to read more

The [observability overview](/docs/observability/) walks through enabling the
stack and reading each tab; the [metrics](/docs/metrics/) and [logs](/docs/logs/)
pages cover the query model and retention in detail. Everything described here is
in the OSS edition — there is no separate tier for seeing your own telemetry.
