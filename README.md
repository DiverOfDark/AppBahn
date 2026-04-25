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
communicate over an HTTPS tunnel (REST + a single Server-Sent Events stream),
so the platform can reach operators behind NAT or egress-only firewalls.

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
api/                   public-api.yaml + tunnel-api.yaml (both springdoc-emitted
                       from the platform's @RestControllers)
backend/
  shared/              CRD models, slug utilities, OperatorJwt + sync payloads
  platform/
    api-spec/          Hand-written REST DTOs + API interfaces (code-first)
    app/               Spring Boot entry point, wires all modules
    workspace/         Workspace, project, environment CRUD, quotas
    resource/          Resources, deployments, links
    user/              User management, RBAC, OIDC group mappings
    tunnel/            Operator tunnel server (REST + SSE, JWT auth)
    common/            Auth context, JWT, base persistence, ID generation
  operator/            AppBahn Operator (separate deployable JAR)
cli/                   CLI tool (Go)
crds/                  CRD definitions
helm/                  Helm chart
web/                   Vue.js 3 SPA (served by the platform JAR)
website/               appbahn.eu — Astro marketing pages + Starlight docs
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

## Code-first API

`api/` holds two OpenAPI specs, both emitted by springdoc from the platform's
`@RestController`s at build time:

- `public-api.yaml` — user-facing Platform API, consumed by the web console
  and the CLI
- `tunnel-api.yaml` — operator ↔ platform transport (REST + a single SSE
  stream); internal, but specified for clarity

`./gradlew :platform:app:syncOpenApi` regenerates both; CI gates freshness via
`:platform:app:verifyOpenApi`.

Generated clients:

- **Frontend** — npm generates a TypeScript client from `public-api.yaml`
- **CLI** — `cli/scripts/generate-api.sh` generates a Go client from
  `public-api.yaml`

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
