---
title: Your First Deployment
description: Deploy a Docker container to AppBahn in 5 minutes
---

This guide walks you through deploying a Docker container (nginx) to AppBahn. You can follow along using the web console or the CLI.

## Prerequisites

- An AppBahn instance running on your Kubernetes cluster
- A workspace, project, and environment already created (see [Getting Started](/docs/getting-started/))

## Step 1: Navigate to Your Environment

### Console

1. Open the AppBahn console
2. Select your workspace
3. Select your project
4. Select your environment

### CLI

```bash
appbahn env list --project your-project-slug
```

## Step 2: Create a Resource

### Console

1. Click **+ Create Resource**
2. Fill in the form:
   - **Name**: `my-nginx`
   - **Type**: Deployment
   - **Image**: `nginx`
   - **Tag**: `1.27`
   - **Port**: `80`
3. Click **Create Resource**

### CLI

```bash
appbahn resource create \
  --name my-nginx \
  --type deployment \
  --env your-env-slug \
  --image nginx:1.27 \
  --port 80 \
  --expose ingress
```

## Step 3: Watch It Deploy

The resource starts in `PENDING` status. The AppBahn operator creates a Kubernetes Deployment, Service, and Ingress for your container.

### Console

The resource list polls automatically. Watch the status badge change from `PENDING` to `READY`.

### CLI

```bash
appbahn resource get my-nginx-abc12ef
```

Replace the slug with the one returned from the create command.

## Step 4: Trigger a Deployment

To update the running container (e.g., after pushing a new image tag):

### Console

1. Navigate to the resource detail page
2. Click **Deploy**

### CLI

```bash
appbahn deploy trigger my-nginx-abc12ef
```

## Step 5: Verify

Once the status is `READY`, the deployment is accessible. If ingress is enabled, visit the domain shown in the resource detail page.

```bash
appbahn deploy list my-nginx-abc12ef
```

## What Happens Under the Hood

1. `POST /resources` validates quotas and license limits, then creates a Kubernetes Custom Resource (CRD)
2. The AppBahn operator watches for new Resource CRDs
3. The operator creates a Deployment, Service, and (if `expose: ingress`) an Ingress
4. The operator monitors pod health and syncs status back to the platform API
5. The web console and CLI read the cached status for display

## Next Steps

- [Resource Types](/docs/resource-types/) — Available types and configuration options
- [Concepts](/docs/concepts/) — Architecture and design overview
