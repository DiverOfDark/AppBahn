---
title: CLI, login, landing page
date: 2026-04-07
summary: The `appbahn` CLI ships, login works against the platform, and the public landing page goes live.
---

First user-facing surface. The CLI ships as a single static binary via GoReleaser;
`appbahn login` authenticates against the platform via OIDC; the landing page
explains what AppBahn is to someone who has never seen it.

### What shipped

- `appbahn` Cobra-based CLI with `login`, `workspace`, `project`, `env`,
  `resource`, `deploy`, `member`, `token`, and `version` command groups.
- GoReleaser pipeline producing signed binaries for Linux, macOS, and Windows,
  published to GitHub releases.
- Public landing page with the design-system-aligned dark theme.
- Login flow on the platform side — OIDC delegation to your IdP.

### Why this matters

Distribution is a product decision. Shipping the CLI as a real binary from day two
(rather than `go run`) meant packaging and signing had to work — and those are the
things that decide whether people actually try a tool.
