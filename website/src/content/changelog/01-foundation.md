---
title: Platform scaffolding, Helm chart, CI/CD
date: 2026-04-06
summary: The foundation. A Spring Boot platform, Vue console, Kubernetes operator, Helm chart, and a CI pipeline.
---

No user-facing feature works yet end-to-end, but every layer exists and the deploy
pipeline can ship a working platform build onto a Kubernetes cluster.

### What shipped

- Multi-module Gradle repo: backend platform, operator, shared modules.
- Vue 3 console wired to the platform via an auto-generated OpenAPI client.
- Helm chart that installs the platform and operator into a fresh cluster.
- GitHub Actions CI pipeline: build, test, publish container images.
- Release workflow producing signed Docker images + versioned Helm chart.

### Why this matters

Starting a self-hosted platform with "it installs from a real Helm chart onto a
real cluster" sets the bar for everything after it. No ad-hoc `kubectl apply`
scripts, no hidden manual steps — the same install path that contributors see is
the one users will.
