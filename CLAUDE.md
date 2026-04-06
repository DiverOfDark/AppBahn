# AppBahn

Enterprise PaaS on your own Kubernetes infrastructure.

## Tech Stack

| Layer    | Technology                                                            |
|----------|-----------------------------------------------------------------------|
| Backend  | Spring Boot 4, Java 25, Gradle multi-module, PostgreSQL, Flyway       |
| Frontend | Vue.js 3, TailwindCSS 4 (OKLch), auto-generated OpenAPI client        |
| Operator | JOSDK (Java Operator SDK), Kubernetes CRDs                            |
| CLI      | Go, Cobra, OpenAPI-generated client (go), GoReleaser for distribution |
| Website  | Astro (SSG)                                                           |
| Docs     | Starlight or VitePress                                                |

## Project Structure

```
backend/
  shared/              — CRD models, slug utils, shared types (used by both platform + operator)
  platform/
    api-spec/          — OpenAPI code generation (Spring server stubs from api/ specs)
    app/               — Spring Boot entry point, wires all modules
    workspace/         — Workspace/Project/Environment CRUD, quotas
    resource/          — Resources, deployments, links, license enforcement
    git/               — Git repo access, clone, build config detection
    observability/     — Metrics + logs providers (pluggable)
    user/              — User management, RBAC, OIDC group mappings
    common/            — Auth context, JWT, base persistence, ID generation
  operator/            — AppBahn Operator (separate deployable JAR)
web/                   — Vue.js 3 SPA (served by backend)
cli/                   — CLI tool
website/               — Public marketing site (Astro SSG, appbahn.eu)
docs/                  — User documentation
api/                   — OpenAPI specs (public-api.yaml + internal-api.yaml)
helm/                  — Helm chart
crds/                  — CRD definitions
e2e/                   — Playwright E2E tests
spec/                  — Specification (private, never published)
  SPEC.md              — Full specification (~6000 lines, DO NOT read whole)
  ROADMAP.md           — Development roadmap (16 sprints, 32 weeks)
  sprints/             — Sprint-sized spec extractions (READ THESE instead of SPEC.md)
```

## Key Design Decisions

- **API-first**: OpenAPI specs (`api/public-api.yaml` + `api/internal-api.yaml`) are the source of truth → Gradle generates Spring server stubs, npm generates TypeScript client
- **Two deployables**: Platform API + Operator (same Gradle project, separate JARs, communicate over HTTPS)
- **Resources are Kubernetes CRDs** (source of truth in etcd), cached to PostgreSQL `resource_cache` for reads
- **Standard MVC per module**: Controller (generated interface impl) → Service → Repository → PostgreSQL
- **No DDD, no event sourcing** — straightforward controllers, services, repositories
- **Slugs everywhere**: Human-readable, immutable, DNS-valid. Format: `{name truncated to 10}-{7 random alphanum}`
- **UUIDs v7** for PostgreSQL entities (sortable)
- **Same-origin SPA**: Vue.js built assets embedded in the Spring Boot JAR, no CORS needed
- **Provider abstractions**: LogProvider, MetricsProvider, etc. — pluggable backends with graceful degradation

## Sprint-Based Development

Each sprint is a **vertical slice** across all layers (API, operator, web console, CLI, docs, website).

**How to work on a sprint:**
1. Read `spec/sprints/sprint-XX.md` — contains all deliverables, acceptance criteria, tests, and relevant spec excerpts
2. If you need deeper detail on a specific topic, read the corresponding section in `spec/SPEC.md` using line offsets

## Current Sprint

Sprint 2 — see `spec/sprints/sprint-02.md`

## Conventions

- All URLs use **slugs** (not UUIDs) — both console and API
- Console URL pattern: `/console/{wsSlug}/{projSlug}/{envSlug}/{resSlug}`
- API path pattern: `/api/v1/workspaces/{slug}`, `/api/v1/resources/{slug}`
- Namespace pattern: `{prefix}-{environmentSlug}` (default prefix: `abp`)
- Image path: `{registry}/{prefix}/{ws_slug}/{proj_slug}/{env_slug}/{resource_slug}:{commit-hash}`
- Audit logging: every mutation is recorded (append-only)
- Tests ship with features, not after
- ELv2 license — test code is private, source code is public
