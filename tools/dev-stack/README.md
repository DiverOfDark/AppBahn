# Local dev stack

Postgres + mock-oauth2 + a throwaway K3s cluster in docker-compose. The
platform JAR and operator JAR run from IntelliJ IDEA — both as host
processes, both pointing at the K3s container's apiserver via the
generated kubeconfig.

## Boot it

```sh
cd tools/dev-stack
docker compose up -d --wait
```

`--wait` (compose v2.13+) blocks until every service's healthcheck passes,
so the command only returns once Postgres accepts connections, mock-oauth2
serves its OIDC discovery doc, and the K3s node reports `Ready`. No
follow-up sanity check needed.

If you boot without `--wait` (`docker compose up -d`), `docker compose ps`
shows each service's health state — wait for all three to read `healthy`
before pointing the platform/operator at the cluster.

That gives you:

- Postgres on `localhost:5432`
- mock-oauth2 on `localhost:8081`
- K3s apiserver on `localhost:6443`, kubeconfig dropped at
  `tools/dev-stack/kubeconfig/kubeconfig.yaml`
- (CRDs are installed by the operator at startup — see below.)

## Run the platform + operator from IDEA

For both `AppBahnApplication` (platform) and `OperatorApplication`
(operator), open the run configuration and set:

- **Active profiles:** `dev`
- **Environment variables:** none

That's it. Both apps' `application-dev.yml` carries every override
(Postgres URL, mock-oauth2 issuer, admin-group mapping, operator's
`platform-base-url`, operator's HTTP port `:8082` to dodge the platform's
`:8080`, debug logging). Both apps' `DevKubernetesClientConfig` walks up
from the working directory to find
`tools/dev-stack/kubeconfig/kubeconfig.yaml`, so IDEA's default working
dir of `<repo>/backend` works out of the box. If you ever need to point
at a different cluster, set `$KUBECONFIG` and the auto-resolution falls
back to it.

On startup, the operator's `DevCrdInstaller` reads the AppBahn CRD YAMLs
from the classpath (`META-INF/fabric8/*-v1.yml`, emitted at compile time
by the fabric8 APT in `:shared`) and `serverSideApply`s them. Same source
the helm chart pulls from in production, so dev and prod can't drift.

The operator then generates an Ed25519 keypair, persists it as the
Secret `appbahn-operator-key` in the kubeconfig's current-context
namespace (`default`), and POSTs `/tunnel/register` to the platform. The
platform reads the same Secret via `OperatorPubkeyReader`, sees a match,
and auto-promotes the cluster row from `PENDING` to `APPROVED`. From
there the operator opens its SSE subscription and reconciles every
command the platform enqueues.

## Vite HMR (optional)

```sh
cd web && npm run dev      # localhost:5173
```

Vite proxies `/api`, `/oauth2/*`, and `/login/oauth2/*` through to the
platform on `:8080` with `xfwd: true`, and the platform runs with
`server.forward-headers-strategy=FRAMEWORK` in dev. That means the OIDC
kickoff and callback flow stays on `:5173` end-to-end — open
`http://localhost:5173/`, log in normally, and you're done. No
manual token copy between tabs.

## Logging in

mock-oauth2's interactive form accepts any username; the regex matcher in
`mock-oauth2-config.json` (`"requestParam": "username", "match": ".+"`)
auto-stamps every login as `dev-admin` with `groups: ["admin"]`. You're a
global admin without typing claim JSON.

## What works

✅ Real OIDC, real Postgres + Flyway, real Kubernetes — workspaces,
projects, environments, CRUD on resources, the full operator
reconciliation loop, pod logs (once container runtime fetches them),
status updates, restarts. Same code paths as production.

⚠️ No ingress / loadbalancer / metrics-server in the K3s — passing
`--disable=traefik,servicelb,metrics-server` keeps boot fast. Resources
with `expose: Ingress` or `expose: Tcp` will provision the K8s objects
but won't be reachable from outside the cluster. Use
`KUBECONFIG=… kubectl port-forward …` if you need to hit a pod.

⚠️ No real container registry. Public images (DockerHub, GHCR public)
work; private registries need imagePullSecrets that this stack doesn't
mint.

❌ Build pipelines (Git source) won't work — they need an in-cluster
registry the operator pushes to. Use prebuilt Docker images for dev.

## Linux-only on `network_mode: host`

Postgres + mock-oauth2 use host networking so the OIDC issuer URL
`http://localhost:8081/default` resolves identically from the browser
and from the platform's server-side calls. K3s uses bridge networking
+ a port mapping for 6443 — k3s manages its own iptables and is
happiest in its own netns. Docker Desktop on Mac/Windows runs containers
in a VM where host networking semantics don't apply; switch the
host-networked services to bridge + URL overrides
(`host.docker.internal:8081`, `host.docker.internal:6443`,
`MOCK_OAUTH2_SERVER_HOSTNAME=localhost:8081`) to support those.

## Resetting state

Postgres data and K3s state persist across `docker compose down` /
`docker compose up` cycles via named volumes (`postgres-data`,
`k3s-server`, `k3s-containerd`). To wipe everything and start fresh:

```sh
cd tools/dev-stack
docker compose down -v   # removes the named volumes — Postgres + K3s reset
docker compose up -d --wait
```

The kubeconfig in `./kubeconfig/kubeconfig.yaml` is a host bind-mount
and survives `down -v` — K3s simply overwrites it on next boot, which
keeps the in-cluster CA cert in sync with the freshly-generated server
state.

## Viewing logs

- Platform / Operator: IDEA's Run console.
- Postgres / mock-oauth2 / K3s: `docker compose logs -f <service>`.
