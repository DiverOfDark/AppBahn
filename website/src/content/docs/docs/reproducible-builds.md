---
title: Reproducible Builds
description: What AppBahn guarantees about build determinism, and what it does not.
---

AppBahn's CI pipeline produces byte-identical artefacts when the same commit is built twice. This page describes what that guarantee covers, how it is enforced, and what is explicitly out of scope today.

## What is guaranteed

For a given source commit, two CI runs produce the same SHA-256 for every published artefact:

- **Backend JARs** — `appbahn-platform.jar` and `appbahn-operator.jar` (Spring Boot bootJars and every dependency JAR built by the multi-module Gradle project).
- **OCI images** — `ghcr.io/diverofdark/appbahn/platform`, `.../operator`, and `.../cli`. The manifest digest is deterministic; layer blobs are byte-identical.
- **Helm chart** — `appbahn-<version>.tgz` produced by `helm package`.
- **CLI archives** — `appbahn_<version>_<os>_<arch>.tar.gz` (and `.zip` for Windows), plus the GoReleaser checksums file.
- **Web bundle** — Vite's content-hashed asset filenames are stable across rebuilds (Vite hashes the post-transform contents, so the same source produces the same `assets/index-<hash>.js`).
- **Marketing site** — Astro's static-build output (`website/dist/`) is byte-identical, including the `_astro/*` content-hashed bundles.

## How it works

The pipeline uses two mechanisms.

**`SOURCE_DATE_EPOCH`** — every CI build job exports this environment variable as the commit's Unix timestamp:

```bash
SOURCE_DATE_EPOCH=$(git log -1 --format=%ct)
```

`SOURCE_DATE_EPOCH` is the [reproducible-builds.org](https://reproducible-builds.org/specs/source-date-epoch/) convention. The following tools read it natively and use it to override embedded timestamps:

- `helm package` — chart tarball entry timestamps.
- GoReleaser (`mod_timestamp: '{{ .CommitTimestamp }}'`) — Go binary build IDs and tarball entries.
- BuildKit (`DOCKER_BUILDKIT=1`) — OCI layer mtimes, when the env var is forwarded as a build-arg.

**Gradle archive normalisation** — every `Jar`/`Zip` task across the backend modules is configured for deterministic output:

```kotlin
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    filePermissions { unix("rw-r--r--") }
    dirPermissions { unix("rwxr-xr-x") }
}
```

This zeroes per-entry mtimes, sorts entries alphabetically (so the central directory layout is stable), and strips filesystem-specific permission bits.

**Go binary normalisation** — GoReleaser builds the CLI with `-trimpath`, `-buildvcs=false`, and `-mod=readonly` so the toolchain stops embedding host paths, workspace VCS status, or dependency-mutation hints.

## Verifying locally

Build twice from the same commit:

```bash
# First build
SOURCE_DATE_EPOCH=$(git log -1 --format=%ct) ./gradlew :platform:app:bootJar :operator:bootJar
sha256sum backend/platform/app/build/libs/*.jar backend/operator/build/libs/*.jar > /tmp/first.sha256

# Clean and rebuild
./gradlew clean
SOURCE_DATE_EPOCH=$(git log -1 --format=%ct) ./gradlew :platform:app:bootJar :operator:bootJar
sha256sum backend/platform/app/build/libs/*.jar backend/operator/build/libs/*.jar > /tmp/second.sha256

# Compare
diff /tmp/first.sha256 /tmp/second.sha256
```

The same recipe works for `helm package` (the chart tarball) and `goreleaser build --single-target --snapshot` (the CLI binary).

## What is not guaranteed

The following are explicitly out of scope for now. They are tracked separately.

- **Independent-rebuilder attestation.** No third party is currently building the same source and publishing a matching SHA-256. The guarantee here is "our CI is deterministic", not "our CI matches an independent rebuilder".
- **CI rebuild-and-diff matrix.** The pipeline does not yet run a second build of every commit and fail on a digest mismatch. The reproducibility plumbing is in place; the matrix check is future work.
- **User-image reproducibility.** Images that AppBahn builds _on behalf of users_ (the buildpack/Dockerfile pipeline) inherit whatever determinism their source tree and base image provide. AppBahn does not currently mutate them to add `SOURCE_DATE_EPOCH`.
- **Build-environment reproducibility.** Two rebuilds at different points in time may use different base images (Chainguard's Wolfi is rebuilt daily for CVE patches), different Go toolchain patches, different Node minor versions. The guarantee is "same commit, same CI pipeline, same artefact bytes" — not "same commit at any future time".

## Why this matters

Reproducible builds let supply-chain verifiers prove that a published artefact corresponds to a specific source commit, without trusting the build infrastructure. Combined with signed tags and a [Sigstore](https://www.sigstore.dev/) attestation, an independent rebuilder can detect tampering between source and release.

AppBahn's reproducibility today is necessary but not sufficient for that full guarantee — independent rebuilders and an attestation pipeline are still to come. The current scope eliminates the easy class of bugs (embedded build timestamps, host paths, filesystem ordering) so the harder work is the only work left.
