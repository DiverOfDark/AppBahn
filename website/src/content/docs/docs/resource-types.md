---
title: Resource Types
description: Available resource types in AppBahn
---

## Overview

A **resource** is any workload or service running in an AppBahn environment. Each resource has a **type** that determines how AppBahn provisions and manages it.

## Deployment

The `deployment` type runs a container image as a Kubernetes Deployment with a Service and optional Ingress.

### Configuration

| Field                       | Type    | Default      | Description                                                                                                                                                            |
| --------------------------- | ------- | ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `source.type`               | string  | —            | Source type: `docker` or `git`                                                                                                                                         |
| `source.image`              | string  | —            | Docker image name (e.g., `nginx`, `registry.example.com/app`)                                                                                                          |
| `source.tag`                | string  | `latest`     | Image tag                                                                                                                                                              |
| `networking.ports[].port`   | integer | `80`         | Container port to expose                                                                                                                                               |
| `hosting.cpu`               | string  | `"250m"`     | CPU request in Kubernetes quantity format (e.g., "250m", "1")                                                                                                          |
| `hosting.memory`            | string  | `"256Mi"`    | Memory request in Kubernetes quantity format (e.g., "256Mi", "1Gi")                                                                                                    |
| `hosting.replicas`          | integer | `1`          | Number of replicas                                                                                                                                                     |
| `networking.ports[].expose` | string  | `none`       | Exposure mode: `ingress` (public HTTP via Ingress + in-cluster ClusterIP service), `tcp` (raw-TCP LoadBalancer), or `none` (in-cluster ClusterIP service, no Ingress). |
| `networking.ports[].domain` | string  | auto         | Per-port domain. Defaults to `{slug}.{baseDomain}` for the first ingress port and `{slug}-{port}.{baseDomain}` for additional ones.                                    |
| `runMode`                   | string  | `continuous` | Run mode: `continuous`                                                                                                                                                 |
| `healthCheck.readiness`     | object  | —            | Readiness probe (see Health Checks below)                                                                                                                              |
| `healthCheck.liveness`      | object  | —            | Liveness probe (see Health Checks below)                                                                                                                               |
| `healthCheck.startup`       | object  | —            | Startup probe (see Health Checks below)                                                                                                                                |
| `env`                       | object  | `{}`         | Environment variables as key-value pairs                                                                                                                               |

### Example

```json
{
  "source": {
    "type": "docker",
    "image": "nginx",
    "tag": "1.27"
  },
  "hosting": {
    "cpu": "250m",
    "memory": "256Mi",
    "replicas": 2
  },
  "networking": {
    "ports": [
      {
        "port": 80,
        "expose": "ingress"
      }
    ]
  },
  "runMode": "continuous",
  "env": {
    "APP_ENV": "production"
  }
}
```

### Resource Status

Resources go through these status phases:

| Status       | Description                                                                                                                                         |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PENDING`    | Resource created, waiting for pods                                                                                                                  |
| `READY`      | All replicas are available                                                                                                                          |
| `DEGRADED`   | Some replicas unavailable                                                                                                                           |
| `RESTARTING` | Rolling restart in progress                                                                                                                         |
| `ERROR`      | No replicas available — image pull failed, rollout deadline exceeded, or a container is `CrashLoopBackOff`-ing. `statusDetail.lastError` shows why. |
| `STOPPED`    | Manually stopped                                                                                                                                    |

When a container crash-loops or its previous instance terminated with a non-zero exit code,
the latest error is surfaced under `statusDetail.lastError` (for example
`container 'app' crash-looped: exited 137 (OOMKilled)`).

### Health Checks

Each probe (`readiness`, `liveness`, `startup`) supports one action type and optional timing parameters:

| Field                 | Type    | Description                                   |
| --------------------- | ------- | --------------------------------------------- |
| `httpGet.path`        | string  | HTTP path to probe (e.g., `/healthz`)         |
| `httpGet.port`        | integer | Port for the HTTP probe                       |
| `tcpSocket.port`      | integer | Port for a TCP socket check                   |
| `exec.command`        | array   | Command to execute inside the container       |
| `initialDelaySeconds` | integer | Seconds to wait before the first probe        |
| `periodSeconds`       | integer | Interval between probes                       |
| `failureThreshold`    | integer | Consecutive failures before marking unhealthy |

Example with health checks:

```json
{
  "healthCheck": {
    "readiness": {
      "httpGet": { "path": "/ready", "port": 8080 },
      "initialDelaySeconds": 5,
      "periodSeconds": 10
    },
    "liveness": {
      "httpGet": { "path": "/healthz", "port": 8080 },
      "periodSeconds": 30,
      "failureThreshold": 3
    }
  }
}
```

#### Live probe status

The console's resource overview shows the latest outcome for each configured probe — a green
dot for success, red for failure, and the most recently measured latency in milliseconds. The
data lives under `statusDetail.probeStatus` and is refreshed continuously by the operator:

| Field           | Source                                                                                                                                                                       |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ok`            | Failures are picked up from kubelet `Unhealthy` events the moment they fire; successes are observed by an operator-side probe that runs against the pod IP every 60 seconds. |
| `lastLatencyMs` | Measured by the operator's own probe run. Kubelet failure events do not carry latency, so this field can be `null` for a probe that has only ever failed.                    |
| `lastCheckedAt` | Wall-clock time the most recent observation was recorded.                                                                                                                    |

Exec probes (`exec.command`) record only `lastCheckedAt`; their latency is not measured. HTTP
and TCP probes record latency in full.

### Error Responses

Resource creation and updates may return these additional error codes:

| Status | Meaning                                                                         |
| ------ | ------------------------------------------------------------------------------- |
| `402`  | **License limit reached** — the current license does not allow more resources   |
| `422`  | **Quota exceeded** — workspace, project, or environment quota would be exceeded |

These responses include a JSON body with `error` and `message` fields describing the specific limit or quota that was exceeded.

## Database Types

Database resource types (PostgreSQL, MongoDB, Valkey, etc.) are available when the corresponding operator is installed on the cluster. Use the `/resource-types` API endpoint or the console to see available types for your cluster.

## API Reference

- `GET /api/v1/resource-types?cluster={name}` — List available types
- `POST /api/v1/resources` — Create a resource
- `GET /api/v1/resources/{slug}` — Get resource details
- `PATCH /api/v1/resources/{slug}` — Update a resource
- `DELETE /api/v1/resources/{slug}` — Delete a resource
