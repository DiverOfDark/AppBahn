---
title: Getting Started
description: Set up AppBahn on your Kubernetes cluster.
---

This guide will walk you through setting up AppBahn on your Kubernetes cluster.

## Prerequisites

<!-- TODO: List prerequisites (Kubernetes version, Helm, kubectl, etc.) -->

- A running Kubernetes cluster (v1.28+)
- `kubectl` configured to access your cluster
- Helm 3 installed
- A PostgreSQL database (or use the bundled one)

## Installation

<!-- TODO: Add Helm install instructions -->

```bash
helm install appbahn oci://ghcr.io/diverofdark/appbahn/charts/appbahn \
  --namespace appbahn --create-namespace
```

## Quick Start

<!-- TODO: Walk through creating a workspace, project, environment, and deploying a first resource -->

1. Open the AppBahn console at `https://<your-cluster>/console`
2. Create your first **Workspace**
3. Add a **Project** inside the workspace
4. Create an **Environment** (e.g. `dev`)
5. Deploy a **Resource** from a Git repository
