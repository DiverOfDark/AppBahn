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
- **Scopes**: `openid`
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
4. For **both** clients, add an audience mapper (Mappers → Add mapper → Audience):
   - Included Custom Audience: `appbahn`
   - Add to ID token: On
   - Add to access token: On
5. Create a group `appbahn-admins` and add your admin users
6. Add a group mapper to the `appbahn` client scope to include `groups` in the token

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
| `platform.auth.clientId`            | Yes      |                                           | OAuth2 client ID for the platform                                                |
| `platform.auth.clientSecret`        | Yes      |                                           | OAuth2 client secret                                                             |
| `platform.auth.audience`            | No       | `appbahn`                                 | Expected JWT audience claim — tokens without this `aud` are rejected             |
| `platform.auth.platformAdminGroups` | No       | `[]`                                      | OIDC group names that grant platform admin access                                |
| `platform.namespacePrefix`          | No       | `abp`                                     | Prefix for Kubernetes namespaces (`{prefix}-{envSlug}`)                          |

#### Operator

| Value                           | Required | Default            | Description                                 |
| ------------------------------- | -------- | ------------------ | ------------------------------------------- |
| `operator.platformApi.endpoint` | No       | auto-detected      | URL of the platform API (internal service)  |
| `operator.auth.clientId`        | Yes      | `appbahn-operator` | OAuth2 client ID (client credentials grant) |
| `operator.auth.clientSecret`    | Yes      |                    | OAuth2 client secret                        |
| `operator.auth.tokenEndpoint`   | Yes      |                    | OIDC token endpoint URL                     |

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
  namespacePrefix: abp

operator:
  auth:
    clientId: appbahn-operator
    clientSecret: operator-secret
    tokenEndpoint: https://keycloak.example.com/realms/appbahn/protocol/openid-connect/token
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
