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
- **Token claims**: Ensure the ID token includes `email` and optionally `groups` (for OIDC group mapping)

**2. Operator client** (Client Credentials flow, for machine-to-machine):

- **Client type**: Confidential (with client secret)
- **Grant type**: Client Credentials
- **Scopes**: `openid`, `internal` (the `internal` scope grants access to the platform's internal sync API)
- No redirect URI needed

> **Note**: AppBahn validates tokens using the issuer (`iss`) claim. All tokens from the configured OIDC provider are trusted. The `internal` scope is what distinguishes operator tokens from user tokens — only operator tokens with `SCOPE_internal` can access the internal sync API.

### Platform admin groups

To grant users platform-wide admin access, configure `platform.auth.platformAdminGroups` with the OIDC group names. Any user whose `groups` JWT claim intersects with this list gets full Owner-level access on all workspaces.

### Example: Keycloak

Keycloak uses standard OIDC clients for both the platform and operator.

#### 1. Create a Realm

Create a new realm (e.g. `appbahn`) or use an existing one. All clients will live in this realm.

#### 2. Platform Client (Authorization Code)

Go to **Clients** → **Create client**:

- **Client ID**: `appbahn`
- **Client authentication**: On (confidential client)
- **Authentication flow**: Check **Standard flow** (Authorization Code), uncheck Direct access grants
- **Valid redirect URIs**: `https://appbahn.example.com/login/oauth2/code/appbahn`
- **Web origins**: `https://appbahn.example.com`

Save the generated **Client Secret** from the **Credentials** tab.

#### 3. Operator Client (Client Credentials)

Go to **Clients** → **Create client**:

- **Client ID**: `appbahn-operator`
- **Client authentication**: On
- **Authentication flow**: Check **Service accounts roles** only (this enables Client Credentials grant)

Save the generated **Client Secret** from the **Credentials** tab.

#### 4. Create the `internal` Client Scope

The operator needs the `internal` scope to access the platform's sync API.

1. Go to **Client scopes** → **Create client scope**
2. Configure:
   - **Name**: `internal`
   - **Type**: Optional
   - **Include in token scope**: On
3. Assign to the operator client: go to **Clients** → `appbahn-operator` → **Client scopes** → **Add client scope** → select `internal` → **Add** as **Default**

#### 5. Add Groups to ID Tokens

AppBahn uses the `groups` claim in the ID token for OIDC group mapping and platform admin access.

1. Go to **Client scopes** → `appbahn` (the dedicated scope for your platform client, created automatically)
2. **Mappers** → **Add mapper** → **By configuration** → **Group Membership**
3. Configure:
   - **Name**: `groups`
   - **Token claim name**: `groups`
   - **Full group path**: Off (use short names)
   - **Add to ID token**: On
   - **Add to access token**: On

#### 6. Create Admin Group

1. Go to **Groups** → **Create group** → name it `appbahn-admins`
2. Add your admin users to this group (**Users** → select user → **Groups** → **Join group**)
3. Set `platform.auth.platformAdminGroups: ["appbahn-admins"]` in your Helm values

#### Configuration values

- **Issuer URL**: `https://keycloak.example.com/realms/appbahn`
- **Token endpoint**: `https://keycloak.example.com/realms/appbahn/protocol/openid-connect/token`

### Example: Zitadel

Zitadel handles the two client types differently:

- **Platform client** → standard OIDC Application (Authorization Code flow)
- **Operator client** → Service Account (Machine User) with client credentials

#### 1. Create a Project

Create a project (e.g. "AppBahn") in the Zitadel console. Note the **Project ID** — you'll need it for audience configuration.

#### 2. Platform OIDC Application

In your project, create an OIDC Application:

- **Name**: `AppBahn Platform`
- **Application type**: Web
- **Authentication method**: Basic (confidential client with secret)
- **Redirect URI**: `https://appbahn.example.com/login/oauth2/code/appbahn`
- **Post-logout redirect URI**: `https://appbahn.example.com`
- **Grant type**: Authorization Code
- **Response type**: Code
- **Token type**: JWT
- Enable: Access Token Role Assertion, ID Token Role Assertion, ID Token Userinfo Assertion

Save the generated **Client ID** and **Client Secret**.

#### 3. Operator Service Account (Machine User)

> **Important**: The operator requires Client Credentials flow, which in Zitadel is only supported by **Service Accounts (Machine Users)** — not by OIDC or API applications. OIDC application credentials cannot be used with `grant_type=client_credentials`, and API application credentials are only for token introspection, not token acquisition.

1. Go to **Users** → **Service Users** → **New**
2. Create a machine user (e.g. username `appbahn-operator`, name "AppBahn Operator")
3. Click **Actions** → **Generate Client Secret**
4. Copy the **Client ID** and **Client Secret** immediately (the secret cannot be retrieved later)

#### 4. Create the `internal` project role

The operator requests `scope=openid internal` when fetching tokens. In Zitadel, custom scopes map to project roles:

1. Go to your project → **Roles** → **New**
2. Create a role with key `internal` and display name "AppBahn Internal"

#### 5. Grant the operator access to the project

The machine user needs a **User Grant** on the project so its tokens include the correct audience and roles:

1. Go to **Authorizations** → **New**
2. Select the `appbahn-operator` service user
3. Select your project
4. Assign the `internal` role

Without this grant, the operator's tokens won't include the correct roles.

#### Terraform example

If you manage Zitadel with Terraform, here's the complete setup:

```hcl
# Platform OIDC client (Authorization Code flow)
resource "zitadel_application_oidc" "appbahn_platform" {
  org_id                      = var.zitadel_org_id
  project_id                  = zitadel_project.your_project.id
  name                        = "AppBahn Platform"
  redirect_uris               = ["https://appbahn.example.com/login/oauth2/code/appbahn"]
  post_logout_redirect_uris   = ["https://appbahn.example.com"]
  response_types              = ["OIDC_RESPONSE_TYPE_CODE"]
  grant_types                 = ["OIDC_GRANT_TYPE_AUTHORIZATION_CODE"]
  app_type                    = "OIDC_APP_TYPE_WEB"
  auth_method_type            = "OIDC_AUTH_METHOD_TYPE_BASIC"
  version                     = "OIDC_VERSION_1_0"
  access_token_type           = "OIDC_TOKEN_TYPE_JWT"
  access_token_role_assertion = true
  id_token_role_assertion     = true
  id_token_userinfo_assertion = true
}

# Operator service account (Client Credentials flow)
resource "zitadel_machine_user" "appbahn_operator" {
  org_id            = var.zitadel_org_id
  user_name         = "appbahn-operator"
  name              = "AppBahn Operator"
  with_secret       = true
  access_token_type = "ACCESS_TOKEN_TYPE_JWT"
}

# Project role for the 'internal' scope
resource "zitadel_project_role" "appbahn_internal" {
  org_id       = var.zitadel_org_id
  project_id   = zitadel_project.your_project.id
  role_key     = "internal"
  display_name = "AppBahn Internal"
}

# Grant the operator access to the project with the internal role
resource "zitadel_user_grant" "appbahn_operator" {
  org_id     = var.zitadel_org_id
  project_id = zitadel_project.your_project.id
  user_id    = zitadel_machine_user.appbahn_operator.id
  role_keys  = [zitadel_project_role.appbahn_internal.role_key]
}
```

Your issuer URL will be: `https://auth.example.com` (your Zitadel domain)
Your token endpoint will be: `https://auth.example.com/oauth/v2/token`

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
| `platform.database.password`        | Yes\*    | `appbahn`                                 | Database password (ignored when `credentialRef` is set)                          |
| `platform.database.credentialRef`   | No       |                                           | Existing Secret with `username` and `password` keys for database credentials     |
| `platform.auth.issuerUrl`           | Yes      |                                           | OIDC provider issuer URL (supports `.well-known/openid-configuration` discovery) |
| `platform.auth.clientId`            | Yes\*    |                                           | OAuth2 client ID for the platform (ignored when `existingSecret` is set)         |
| `platform.auth.clientSecret`        | Yes\*    |                                           | OAuth2 client secret (ignored when `existingSecret` is set)                      |
| `platform.auth.existingSecret`      | No       |                                           | Name of an existing Secret containing `client-id` and `client-secret` keys       |
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
