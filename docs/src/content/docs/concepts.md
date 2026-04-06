---
title: Concepts
description: Key concepts in the AppBahn platform.
---

AppBahn organizes your applications into a four-level hierarchy. Understanding these concepts is essential for working with the platform.

## Workspaces

A **Workspace** is the top-level organizational unit. It represents a team, department, or tenant. Each workspace has its own set of projects, members, and resource quotas.

<!-- TODO: Expand on workspace settings, quotas, and member roles -->

## Projects

A **Project** groups related applications and services within a workspace. Projects contain environments that represent the stages of your delivery pipeline.

<!-- TODO: Expand on project configuration and Git integration -->

## Environments

An **Environment** maps to a Kubernetes namespace and represents a deployment stage (e.g. `dev`, `staging`, `production`). Each environment has its own set of deployed resources and configuration.

Namespace pattern: `{prefix}-{environmentSlug}` (default prefix: `abp`)

<!-- TODO: Expand on environment promotion, configuration inheritance -->

## Resources

A **Resource** is a deployable unit — a container, database, or other service — that runs inside an environment. Resources are defined as Kubernetes Custom Resources (CRDs) and managed by the AppBahn Operator.

<!-- TODO: Expand on resource types, build configuration, deployment strategies -->
