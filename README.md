# AppBahn

Enterprise PaaS on your own Kubernetes infrastructure. Deploy and manage applications with git-push workflows, managed databases, auto-TLS, observability, and multi-environment promotion — all running on clusters you control.

## Architecture

AppBahn ships as two components:

- **Platform API** — Spring Boot application serving the REST API, web console, and authentication
- **Operator** — Kubernetes operator (JOSDK) that reconciles AppBahn CRDs into native Kubernetes resources

Both are packaged as container images and deployed via a single Helm chart.

## Tech Stack

| Layer    | Technology                                            |
|----------|-------------------------------------------------------|
| Backend  | Spring Boot 4, Java 25, Gradle, PostgreSQL, Flyway    |
| Frontend | Vue.js 3, TailwindCSS 4                               |
| Operator | Java Operator SDK (JOSDK), Kubernetes CRDs             |
| CLI      | Go, Cobra, OpenAPI-generated client                    |
| Website  | Astro                                                  |
| Docs     | Starlight                                              |

## Project Structure

```
backend/
  shared/              Shared CRD models, slug utilities
  platform/
    api-spec/          OpenAPI code generation (Spring server stubs)
    app/               Spring Boot entry point
    workspace/         Workspace/Project/Environment CRUD
    resource/          Resources, deployments, links
    git/               Git repo access, build config detection
    observability/     Metrics + logs providers
    user/              User management, RBAC
    common/            Auth context, JWT, base persistence
  operator/            Kubernetes operator
web/                   Vue.js 3 SPA
cli/                   CLI tool (Go)
api/                   OpenAPI specs (public + internal)
helm/                  Helm chart
crds/                  CRD definitions
website/               Marketing site (Astro)
docs/                  User documentation (Starlight)
e2e/                   Playwright E2E tests
```

## Getting Started

### Prerequisites

- Java 25+ (via SDKMAN or similar)
- Node.js 22+
- Go 1.24+
- Docker or Podman
- PostgreSQL 16+
- A Kubernetes cluster (for operator development)

### Backend

```bash
cd backend
./gradlew :platform:app:bootRun
```

### Web Console

```bash
cd web
npm install
npm run dev
```

### CLI

```bash
cd cli
go build -o appbahn .
./appbahn version
```

### Docs

```bash
cd docs
npm install
npm run dev
```

### Website

```bash
cd website
npm install
npm run dev
```

## API-First Development

OpenAPI specs in `api/` are the source of truth:

- `public-api.yaml` — Platform API consumed by web console and CLI
- `internal-api.yaml` — Internal API for operator-platform communication

Code generation:
- **Backend**: Gradle generates Spring server stubs from the specs
- **Frontend**: npm generates a TypeScript client from `public-api.yaml`
- **CLI**: `cli/scripts/generate-api.sh` generates a Go client from `public-api.yaml`

## Deployment

```bash
helm install appbahn oci://ghcr.io/diverofdark/appbahn/charts/appbahn
```

See the [documentation](https://appbahn.eu/docs) for full installation and configuration guides.

## License

[Elastic License 2.0 (ELv2)](LICENSE)
