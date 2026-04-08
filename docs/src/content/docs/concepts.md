---
title: Concepts
description: Key concepts in the AppBahn platform.
---

AppBahn organizes your applications into a four-level hierarchy. Understanding these concepts is essential for working with the platform.

## Workspaces

A **Workspace** is the top-level organizational unit and the multi-tenancy boundary in AppBahn. It represents a team, department, or tenant. Each workspace has its own set of projects, members, and resource quotas.

Every workspace has members with one of four roles:

- **Owner** — full control over the workspace, including deletion and member management.
- **Admin** — can manage projects, environments, resources, and members below the Admin level.
- **Editor** — can manage resources within existing projects and environments.
- **Viewer** — read-only access to everything in the workspace.

OIDC group mappings can be configured to auto-assign roles when users log in through your identity provider.

Deleting a workspace is blocked (HTTP 409) if it still contains projects. Remove all projects first.

## Projects

A **Project** groups related applications and services within a workspace. Projects contain environments that represent the stages of your delivery pipeline. Creating a project requires the **Admin** role or higher.

Deleting a project is blocked (HTTP 409) if it still contains environments. Remove all environments first.

## Environments

An **Environment** maps to a Kubernetes namespace and represents a deployment stage (e.g. `dev`, `staging`, `production`). Each environment has its own set of deployed resources and configuration. Creating an environment requires the **Admin** role or higher.

Namespace pattern: `abp-{environmentSlug}`

Deleting an environment **cascades** — the corresponding Kubernetes namespace and all resources inside it are removed.

## Resources

A **Resource** is a deployable unit — a container, database, or other service — that runs inside an environment. Resources are defined as Kubernetes Custom Resources (CRDs) and managed by the AppBahn Operator. Full CRUD on resources requires the **Editor** role or higher.

## Slugs

Every workspace, project, environment, and resource receives a **slug** — a human-readable, immutable, DNS-valid identifier used in URLs, API paths, and Kubernetes object names.

Format: `{name-truncated-to-10-chars}-{7-random-alphanum}`

Examples: `acme-corp-a1b2c3d`, `backend-x9y8z7w`, `dev-env-m4n5o6p`

Slugs are assigned at creation time and never change, even if you rename the object.

## Roles and permissions

| Role   | Workspaces   | Projects           | Environments       | Resources | Members                    |
| ------ | ------------ | ------------------ | ------------------ | --------- | -------------------------- |
| Owner  | Edit, delete | Create/edit/delete | Create/edit/delete | Full CRUD | Manage all                 |
| Admin  | Edit         | Create/edit/delete | Create/edit/delete | Full CRUD | Manage Editors and Viewers |
| Editor | View         | View               | View               | Full CRUD | View                       |
| Viewer | View         | View               | View               | View      | View                       |

## Deletion rules

| Entity      | Behavior                                       |
| ----------- | ---------------------------------------------- |
| Workspace   | Blocked (409) if projects exist                |
| Project     | Blocked (409) if environments exist            |
| Environment | Cascades — deletes namespace and all resources |
