# AppBahn

**Self-hosted Platform-as-a-Service on Kubernetes.** Ship like Vercel. Run on your own rails.

AppBahn is a self-hosted PaaS that deploys container images onto any Kubernetes
cluster. Real orchestration — not a Docker Compose wrapper. Your data stays on
your cluster because AppBahn ships as an operator; there is no AppBahn-operated
cloud.

- Website · <https://appbahn.eu>
- Docs · <https://appbahn.eu/docs/getting-started/>
- Changelog · <https://appbahn.eu/changelog/>
- Security · <https://appbahn.eu/security/>
- Licence · [Elastic License 2.0](LICENSE)

## Where it fits

| | Deploy model | Runs where | Your data |
|---|---|---|---|
| Vercel / Railway / Heroku / Render / Fly | SaaS | Vendor cloud | Vendor |
| Coolify / Dokploy | Self-hosted (Docker Compose) | Any VM | You |
| **AppBahn** | **Self-hosted (real Kubernetes)** | **Any cluster** | **You** |

## What works today

See the full honest feature matrix at <https://appbahn.eu/contact/>. Summary:

- Container-image resources on any Kubernetes cluster
- Auto-generated domain + cert-manager TLS
- Workspace → project → environment → resource hierarchy with RBAC at each tier
- OIDC single sign-on (Keycloak, Okta, Microsoft Entra ID, Google Workspace)
- CLI (`appbahn`) with short-lived deploy tokens
- Operator tunnel across any ingress or network

Git-to-deploy pipelines, buildpack autodetection, managed databases, custom
domains with automatic TLS, observability, audit log, and multi-cluster
federation are planned. Track shipped work in the
[changelog](https://appbahn.eu/changelog/).

## Architecture

AppBahn ships as two deployables inside a single Helm chart:

- **Platform API** — Spring Boot application serving the REST API, the Vue
  console, and authentication.
- **Operator** — Kubernetes operator (JOSDK) that reconciles AppBahn CRDs into
  native `Deployment`, `Service`, and `Ingress` resources.

Both are packaged as signed container images. The platform and operator
communicate over a Connect tunnel, so the platform can reach operators behind
NAT or egress-only firewalls.

## Tech stack

| Layer    | Technology                                                        |
|----------|-------------------------------------------------------------------|
| Backend  | Spring Boot 4, Java 25, Gradle multi-module, PostgreSQL, Flyway   |
| Frontend | Vue.js 3, TailwindCSS 4, auto-generated OpenAPI client            |
| Operator | Java Operator SDK (JOSDK), Kubernetes CRDs                        |
| CLI      | Go, Cobra, OpenAPI-generated client                               |
| Website  | Astro (SSG) + Starlight for `/docs/*`                             |

## Project structure

```
api/                   public-api.yaml (OpenAPI) and tunnel-api.proto
                       (Connect tunnel contract)
backend/
  shared/              CRD models, slug utilities, shared types
  tunnel-api/          Connect tunnel proto codegen and wire codec
  platform/
    api-spec/          OpenAPI code generation (Spring server stubs)
    app/               Spring Boot entry point, wires all modules
    workspace/         Workspace, project, environment CRUD, quotas
    resource/          Resources, deployments, links
    git/               Git repo access and build-config detection
    observability/     Metrics and logs providers (pluggable)
    user/              User management, RBAC, OIDC group mappings
    tunnel/            Operator tunnel server (Connect RPCs, JWT auth)
    common/            Auth context, JWT, base persistence, ID generation
  operator/            AppBahn Operator (separate deployable JAR)
cli/                   CLI tool (Go)
crds/                  CRD definitions
helm/                  Helm chart
web/                   Vue.js 3 SPA (served by the platform JAR)
website/               appbahn.eu — Astro marketing pages + Starlight docs
buf.yaml, buf.gen.yaml Proto module metadata
```

## Local development

Prerequisites:

- Java 25+ (SDKMAN works)
- Node.js 22+
- Go 1.24+
- Docker or Podman
- PostgreSQL 16+
- A Kubernetes cluster for operator development

```bash
# Backend
cd backend && ./gradlew :platform:app:bootRun

# Web console
cd web && npm install && npm run dev

# CLI
cd cli && go build -o appbahn . && ./appbahn version

# Website (marketing + docs)
cd website && npm install && npm run dev
```

## API-first

`api/` holds the two interface contracts:

- `public-api.yaml` — OpenAPI spec for the Platform API consumed by the web
  console and CLI
- `tunnel-api.proto` — Connect/gRPC protocol for operator-platform
  communication over the tunnel

Generated clients:

- **Backend** — Gradle generates Spring server stubs from `public-api.yaml`
- **Frontend** — npm generates a TypeScript client from `public-api.yaml`
- **CLI** — `cli/scripts/generate-api.sh` generates a Go client from
  `public-api.yaml`
- **Tunnel** — `buf` generates Java stubs from `tunnel-api.proto` for both
  the platform-side server and the operator-side client

Do not hand-craft API calls — always use the generated clients.

## Install

```bash
helm install appbahn oci://ghcr.io/diverofdark/appbahn/charts/appbahn
```

Full install and configuration guide:
<https://appbahn.eu/docs/getting-started/>.

## Licence

[Elastic License 2.0](LICENSE). Source-available; free to run, modify, and
self-host. See the LICENSE file for the permitted-use carve-outs.
