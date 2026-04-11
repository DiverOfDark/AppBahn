---
title: Getting Started
description: Set up AppBahn on your Kubernetes cluster.
---

This guide will walk you through setting up AppBahn on your Kubernetes cluster.

## Prerequisites

- A running Kubernetes cluster (v1.28+)
- `kubectl` configured to access your cluster
- Helm 3 installed
- A PostgreSQL database (or use the bundled one)
- An OIDC provider (e.g. Keycloak, Authentik, Azure AD, Google Workspace)

## OIDC Provider Setup

AppBahn requires an OpenID Connect (OIDC) provider for authentication. Any provider that supports `.well-known/openid-configuration` discovery works — Keycloak, Authentik, Azure AD, Google Workspace, Okta, etc.

### What you need to create

**1. Platform client** (Authorization Code + PKCE flow, for user login):

- **Client type**: Confidential (with client secret)
- **Grant type**: Authorization Code
- **Redirect URI**: `https://<your-appbahn-domain>/login/oauth2/code/appbahn`
- **Scopes**: `openid`, `profile`, `email`
- **Audience**: Configure the token to include `aud: appbahn` (the platform rejects tokens without this audience)
- **Token claims**: Ensure the ID token includes `email` and optionally `groups` (for OIDC group mapping)

**2. Operator client** (Client Credentials flow, for machine-to-machine):

- **Client type**: Confidential (with client secret)
- **Grant type**: Client Credentials
- **Scopes**: `openid`, `internal` (the `internal` scope grants access to the platform's internal sync API)
- **Audience**: Must match the platform's audience (default: `appbahn`)
- No redirect URI needed

> **Security note**: The audience claim (`aud`) prevents token reuse across applications. A JWT issued for a different application on the same OIDC provider will be rejected by AppBahn because it won't contain `aud: appbahn`.

### Platform admin groups

To grant users platform-wide admin access, configure `platform.auth.platformAdminGroups` with the OIDC group names. Any user whose `groups` JWT claim intersects with this list gets full Owner-level access on all workspaces.

### Example: Keycloak

1. Create a realm (e.g. `appbahn`)
2. Create a client `appbahn` with:
   - Client authentication: On
   - Authentication flow: Standard flow (Authorization Code)
   - Valid redirect URIs: `https://appbahn.example.com/login/oauth2/code/appbahn`
3. Create a client `appbahn-operator` with:
   - Client authentication: On
   - Authentication flow: Service accounts roles (Client Credentials)
4. Create a client scope `internal` (Client Scopes → Create):
   - Name: `internal`
   - Type: Optional
   - Include in token scope: On
   - Assign this scope to the `appbahn-operator` client (Client → Client Scopes → Add client scope)
5. For **both** clients, add an audience mapper (Mappers → Add mapper → Audience):
   - Included Custom Audience: `appbahn`
   - Add to ID token: On
   - Add to access token: On
6. Create a group `appbahn-admins` and add your admin users
7. Add a group mapper to the `appbahn` client scope to include `groups` in the token

Your issuer URL will be: `https://keycloak.example.com/realms/appbahn`

## Installation

### 1. Add the Helm repository

```bash
helm install appbahn oci://ghcr.io/diverofdark/appbahn/charts/appbahn \
  --namespace appbahn --create-namespace \
  --values values.yaml
```

### 2. Configure Helm values

Create a `values.yaml` with your configuration:

#### Platform

| Value                               | Required | Default                                   | Description                                                                      |
| ----------------------------------- | -------- | ----------------------------------------- | -------------------------------------------------------------------------------- |
| `platform.database.url`             | Yes      | `jdbc:postgresql://postgres:5432/appbahn` | PostgreSQL JDBC connection string                                                |
| `platform.database.username`        | Yes      | `appbahn`                                 | Database user                                                                    |
| `platform.database.password`        | Yes      | `appbahn`                                 | Database password                                                                |
| `platform.auth.issuerUrl`           | Yes      |                                           | OIDC provider issuer URL (supports `.well-known/openid-configuration` discovery) |
| `platform.auth.clientId`            | Yes\*    |                                           | OAuth2 client ID for the platform (ignored when `existingSecret` is set)         |
| `platform.auth.clientSecret`        | Yes\*    |                                           | OAuth2 client secret (ignored when `existingSecret` is set)                      |
| `platform.auth.existingSecret`      | No       |                                           | Name of an existing Secret containing `client-id` and `client-secret` keys       |
| `platform.auth.audience`            | No       | `appbahn`                                 | Expected JWT audience claim — tokens without this `aud` are rejected             |
| `platform.auth.platformAdminGroups` | No       | `[]`                                      | OIDC group names that grant platform admin access                                |
| `platform.namespacePrefix`          | No       | `abp`                                     | Prefix for Kubernetes namespaces (`{prefix}-{envSlug}`)                          |
| `platform.domain.base`              | No       | `appbahn.example.com`                     | Base domain for auto-generated resource URLs (`{slug}.{baseDomain}`)             |
| `platform.branding.instanceName`    | No       | `AppBahn`                                 | Instance name shown in the console UI                                            |
| `platform.branding.tagline`         | No       | `Deploy and manage your applications`     | Tagline shown on the login page                                                  |
| `platform.branding.logoUrl`         | No       |                                           | URL to a custom logo image                                                       |
| `platform.branding.loginButtonText` | No       | `Log in with SSO`                         | Text on the login button                                                         |

#### Operator

| Value                              | Required | Default            | Description                                                                     |
| ---------------------------------- | -------- | ------------------ | ------------------------------------------------------------------------------- |
| `operator.platformApi.endpoint`    | No       | auto-detected      | URL of the platform API (internal service)                                      |
| `operator.auth.clientId`           | Yes\*    | `appbahn-operator` | OAuth2 client ID (ignored when `existingSecret` is set)                         |
| `operator.auth.clientSecret`       | Yes\*    |                    | OAuth2 client secret (ignored when `existingSecret` is set)                     |
| `operator.auth.existingSecret`     | No       |                    | Name of an existing Secret containing `client-id` and `client-secret` keys      |
| `operator.auth.tokenEndpoint`      | Yes      |                    | OIDC token endpoint URL                                                         |
| `operator.clusterName`             | No       | `local`            | Cluster name reported by the operator (set for multi-cluster deployments)       |
| `operator.ingressClassName`        | No       |                    | Ingress class for operator-created Ingresses (required if cluster has multiple) |
| `operator.clusterIssuer`           | No       |                    | cert-manager ClusterIssuer for TLS certificates                                 |
| `operator.resourceRequestFraction` | No       | `0.25`             | Fraction of resource limits set as requests (e.g. 0.25 = 25%)                   |

### 3. Example `values.yaml`

```yaml
platform:
  database:
    url: jdbc:postgresql://postgres:5432/appbahn
    username: appbahn
    password: secret
  auth:
    issuerUrl: https://keycloak.example.com/realms/appbahn
    clientId: appbahn
    clientSecret: my-client-secret
    platformAdminGroups:
      - appbahn-admins
  domain:
    base: appbahn.example.com
  namespacePrefix: abp

operator:
  clusterName: production
  ingressClassName: nginx
  clusterIssuer: letsencrypt
  auth:
    clientId: appbahn-operator
    clientSecret: operator-secret
    tokenEndpoint: https://keycloak.example.com/realms/appbahn/protocol/openid-connect/token
```

### Using existing Secrets for OIDC credentials

Instead of passing client credentials as plaintext in `values.yaml`, you can reference a pre-existing Kubernetes Secret. This is recommended for production deployments and works well with external secret managers (e.g. External Secrets Operator, Sealed Secrets).

The Secret must contain two keys: `client-id` and `client-secret`.

```bash
# Create the secrets before installing the chart
kubectl create secret generic appbahn-platform-oidc \
  --namespace appbahn \
  --from-literal=client-id=appbahn \
  --from-literal=client-secret=my-client-secret

kubectl create secret generic appbahn-operator-oidc \
  --namespace appbahn \
  --from-literal=client-id=appbahn-operator \
  --from-literal=client-secret=operator-secret
```

Then reference them in your `values.yaml`:

```yaml
platform:
  auth:
    issuerUrl: https://keycloak.example.com/realms/appbahn
    existingSecret: appbahn-platform-oidc
    # clientId and clientSecret are ignored when existingSecret is set

operator:
  auth:
    existingSecret: appbahn-operator-oidc
    tokenEndpoint: https://keycloak.example.com/realms/appbahn/protocol/openid-connect/token
```

> **Note**: When `existingSecret` is set, the `clientId` and `clientSecret` values are ignored. All credential fields are read from the referenced Secret.

### Additional objects

You can create arbitrary Kubernetes objects as part of the Helm release using the `additionalObjects` list. Each entry is a complete Kubernetes manifest rendered as-is. This is useful for creating Secrets, ConfigMaps, ExternalSecrets, or any other resources alongside AppBahn.

```yaml
additionalObjects:
  - apiVersion: v1
    kind: Secret
    metadata:
      name: appbahn-platform-oidc
    type: Opaque
    stringData:
      client-id: appbahn
      client-secret: my-client-secret
  - apiVersion: external-secrets.io/v1beta1
    kind: ExternalSecret
    metadata:
      name: appbahn-operator-oidc
    spec:
      refreshInterval: 1h
      secretStoreRef:
        name: vault-backend
        kind: ClusterSecretStore
      target:
        name: appbahn-operator-oidc
      data:
        - secretKey: client-id
          remoteRef:
            key: appbahn/operator
            property: client-id
        - secretKey: client-secret
          remoteRef:
            key: appbahn/operator
            property: client-secret
```

### 4. Verify the installation

```bash
kubectl get pods -n appbahn
# Both appbahn-platform and appbahn-operator should be Running

# Open the console
kubectl port-forward svc/appbahn-platform 8080:8080 -n appbahn
# Visit http://localhost:8080
```

## Quick Start via Web Console

1. Open the AppBahn console at `https://<your-cluster>/console` and log in with your OIDC provider.
2. Click **Create Workspace** and give it a name (e.g. "ACME").
3. Inside the workspace, click **Create Project** and name it (e.g. "backend").
4. Inside the project, click **Create Environment** (e.g. "dev"). This provisions a Kubernetes namespace `abp-{envSlug}`.
5. Deploy a **Resource** from a Git repository (covered in a later guide).

## Quick Start via CLI

Install the `appbahn` CLI and authenticate against your cluster:

```bash
appbahn login --server https://<your-cluster>
```

Create a workspace, project, and environment:

```bash
# Create a workspace
appbahn workspace create --name "ACME"

# Create a project inside the workspace (use the slug returned above)
appbahn project create --name "backend" --workspace <workspace-slug>

# Create an environment inside the project
appbahn env create --name "dev" --project <project-slug>
```

You can list your resources at any time:

```bash
appbahn workspace list
appbahn project list --workspace <workspace-slug>
appbahn env list --project <project-slug>
```

### Invite a member

Add a team member to your workspace by email. If they haven't logged in yet, a pending invitation is created and auto-converts on their first OIDC login:

```bash
appbahn member add --workspace <workspace-slug> --email alice@acme.org --role EDITOR
appbahn member list --workspace <workspace-slug>
```

You can also manage members from the workspace **Settings** page in the console.

### Set quotas

Configure resource quotas for your workspace from the console (Settings → Quotas) or via the API:

```bash
curl -X PATCH https://<your-cluster>/api/v1/workspaces/<slug>/quota \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"maxCpuCores": 8, "maxMemoryMb": 16384, "maxResources": 50}'
```

### Create an environment token

Create a deploy token for CI/CD pipelines:

```bash
appbahn token create --env <env-slug> --name "github-actions" --role EDITOR --expires-in-days 90
# Save the token — it's only shown once!

appbahn token list --env <env-slug>
```
