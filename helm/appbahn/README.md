# AppBahn Helm chart

Deploys the two AppBahn services — the **platform** API and the **operator** — plus the CRDs,
RBAC, and supporting objects.

```bash
helm install appbahn ./helm/appbahn \
  --namespace appbahn-system --create-namespace \
  --set platform.baseDomain=appbahn.acme.org \
  --set platform.auth.issuerUrl=https://idp.acme.org/realms/main \
  --set platform.auth.clientId=appbahn \
  --set platform.auth.clientSecret=...
```

## Observability

AppBahn surfaces resource **metrics** (PromQL) and **logs** (LogsQL) in the console. The operator
runs the queries in-cluster, so it alone holds the provider configuration — backend type and
endpoint live on the operator. There is **no platform-side provider config**; an unconfigured
provider (`type: NONE`) degrades gracefully to a "not available" message in the console.

The chart does **not** deploy Prometheus or Victoria Logs — bring your own (or reuse an existing
cluster install) and point the operator at their in-cluster Services:

```yaml
operator:
  providers:
    metrics:
      type: PROMETHEUS
      endpoint: http://prometheus-server.monitoring.svc:80
    logs:
      type: VICTORIA_LOGS
      endpoint: http://victoria-logs.monitoring.svc:9428
```

Both providers default to `type: NONE` (disabled) — the console hides the corresponding view behind
the "not available" message until you set a type and endpoint.

## Operator RBAC

The operator ClusterRole grants `get`/`list`/`watch` on core `v1/Event` objects cluster-wide — the
operator surfaces Kubernetes Events (image pull failures, scheduling problems, probe failures) in
the console alongside resource status.

## Values reference

### Platform

| Key | Default | Description |
| --- | --- | --- |
| `platform.image.repository` | `ghcr.io/diverofdark/appbahn/platform` | Platform image. |
| `platform.replicas` | `1` | Platform replica count. |
| `platform.baseDomain` | `appbahn.example.com` | Base domain for generated ingress hosts. |
| `platform.namespacePrefix` | `abp` | Prefix for per-environment namespaces. |
| `platform.auth.issuerUrl` | `""` | OIDC issuer URL. |
| `platform.auth.clientId` / `clientSecret` | `""` | OIDC client credentials (or use `existingSecret`). |
| `platform.auth.existingSecret` | `""` | Existing Secret with `client-id` / `client-secret`. |
| `platform.auth.platformAdminGroups` | `[]` | OIDC groups granted platform-admin. |
| `platform.database.url` | `jdbc:postgresql://postgres:5432/appbahn` | JDBC URL. |
| `platform.database.credentialRef` | `""` | Secret with `username` / `password` (else inline). |
| `platform.registry.url` | `""` | Image registry for built images. |
| `platform.quota.maxResources` / `maxCpuCores` / `maxMemoryMb` | `50` / `16.0` / `32768` | Default workspace quota. |
| `platform.licenseFile` / `licenseSecretName` | `""` | Enterprise license (inline contents, or existing Secret with `license.jws`). Mutually exclusive. |
| `platform.deployment.retention.*` | `10` / `true` / `0 0 3 * * *` | Deployment audit-row pruner. |
| `platform.branding.*` | `AppBahn` / … | Console branding (instance name, tagline, logo, login button). |

### Operator

| Key | Default | Description |
| --- | --- | --- |
| `operator.image.repository` | `ghcr.io/diverofdark/appbahn/operator` | Operator image. |
| `operator.clusterName` | `local` | Cluster identifier reported over the tunnel. |
| `operator.platformBaseUrl` | `""` | Platform origin; defaults to the in-cluster `<release>-platform` Service. |
| `operator.providers.metrics.type` | `NONE` | Metrics backend type (`NONE` disables; `PROMETHEUS` to enable). |
| `operator.providers.metrics.endpoint` | `""` | Prometheus base URL the operator queries, e.g. `http://prometheus-server.monitoring.svc:80`. |
| `operator.providers.logs.type` | `NONE` | Logs backend type (`NONE` disables; `VICTORIA_LOGS` to enable). |
| `operator.providers.logs.endpoint` | `""` | Victoria Logs base URL the operator queries, e.g. `http://victoria-logs.monitoring.svc:9428`. |
| `operator.build.*` | … | Build infrastructure (registry, builders, deadlines). |
| `operator.extraEnv` | `[]` | Extra env vars for the operator container. |

### Other

| Key | Default | Description |
| --- | --- | --- |
| `serviceMonitor.enabled` | `false` | Render Prometheus-Operator `ServiceMonitor`s for platform + operator. |
| `additionalObjects` | `[]` | Extra raw manifests to render alongside the release. |
| `builtinResourceTypes` | (6 types) | Discoverable managed-resource catalog (PostgreSQL, S3, Valkey, MongoDB, Kafka, NATS). |
