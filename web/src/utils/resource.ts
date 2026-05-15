import type { components } from '@/api/schema'

type Deployment = components['schemas']['Deployment']
type ProbeConfig = components['schemas']['ProbeConfig']

export type ProbeMode = 'http' | 'tcp' | 'exec'

export interface ProbeInputs {
  mode: ProbeMode
  path: string
  port: number | undefined
  command: string
  initialDelay: number
  period: number
  failureThreshold: number
}

/**
 * Parse a Kubernetes-style CPU quantity into millicores. `250m` → 250,
 * `0.5` → 500. Returns undefined when the input is missing or malformed.
 */
export function parseMillicores(v: string | undefined): number | undefined {
  if (!v) return undefined
  if (v.endsWith('m')) {
    const n = Number(v.slice(0, -1))
    return Number.isFinite(n) ? n : undefined
  }
  const n = Number(v)
  return Number.isFinite(n) ? n * 1000 : undefined
}

/**
 * Parse a Kubernetes-style memory quantity into MiB. `256Mi` → 256,
 * `1Gi` → 1024, bare `256` → 256.
 */
export function parseMebibytes(v: string | undefined): number | undefined {
  if (!v) return undefined
  if (v.endsWith('Mi')) {
    const n = Number(v.slice(0, -2))
    return Number.isFinite(n) ? n : undefined
  }
  if (v.endsWith('Gi')) {
    const n = Number(v.slice(0, -2))
    return Number.isFinite(n) ? n * 1024 : undefined
  }
  const n = Number(v)
  return Number.isFinite(n) ? n : undefined
}

/**
 * Split an OCI image reference into `[image, tag-or-digest]`. Digest takes
 * precedence (`nginx@sha256:abc` → `[nginx, sha256:abc]`). For tag form, the
 * last colon wins, but only if no slash follows (so `registry.example/x:5000`
 * doesn't get mis-split on the port).
 */
export function splitImageRef(ref: string): [string, string] {
  if (!ref) return ['', 'latest']
  const at = ref.lastIndexOf('@')
  if (at > 0) return [ref.slice(0, at), ref.slice(at + 1)]
  const colon = ref.lastIndexOf(':')
  if (colon > 0 && !ref.slice(colon).includes('/')) {
    return [ref.slice(0, colon), ref.slice(colon + 1)]
  }
  return [ref, 'latest']
}

/**
 * `v-model.number` on an empty input produces NaN, not undefined — which
 * sneaks past the common `value !== undefined` guard. Normalises both
 * NaN and undefined into a single missing-value sentinel.
 */
export function asOptionalInt(v: number | undefined): number | undefined {
  return v !== undefined && Number.isFinite(v) ? v : undefined
}

/**
 * Short identifier for a deployment row (compact deploy-history list).
 * Prefers the source commit when present (Git source), falls back to the
 * image tag/digest for Docker-source resources. 7 chars max.
 */
export function depShortRef(dep: Deployment): string {
  const ref = dep.sourceRef || dep.imageRef || ''
  if (!ref) return '—'
  const at = ref.lastIndexOf('@')
  if (at > 0) {
    const digest = ref.slice(at + 1)
    return digest.startsWith('sha256:') ? digest.slice(7, 14) : digest.slice(0, 7)
  }
  const colon = ref.lastIndexOf(':')
  if (colon > 0 && !ref.slice(colon).includes('/')) {
    return ref.slice(colon + 1, colon + 1 + 7)
  }
  return ref.length > 7 ? ref.slice(0, 7) : ref
}

/**
 * Initials extracted from a slug-or-name. `web-api` → `WA`, `backend` → `BA`.
 * Falls back to `··` when input is empty.
 */
export function initials(s?: string): string {
  if (!s) return '··'
  const parts = s.split(/[\s\-_/]+/).filter(Boolean)
  const first = parts[0]
  if (!first) return '··'
  const second = parts[1]
  if (!second) return first.slice(0, 2).toUpperCase()
  return (first.charAt(0) + second.charAt(0)).toUpperCase()
}

/**
 * Build a `ProbeConfig` from editor inputs. `fallbackPort` is used for
 * http/tcp probes when the user hasn't entered an explicit port — defaults
 * to the resource's first port row.
 */
export function buildProbe(h: ProbeInputs, fallbackPort: number | undefined): ProbeConfig {
  const probe: ProbeConfig = {
    initialDelaySeconds: h.initialDelay,
    periodSeconds: h.period,
    failureThreshold: h.failureThreshold,
  }
  const explicitPort = asOptionalInt(h.port)
  if (h.mode === 'http') {
    probe.httpGet = { path: h.path.trim() || '/', port: explicitPort ?? fallbackPort }
  } else if (h.mode === 'tcp') {
    probe.tcpSocket = { port: explicitPort ?? fallbackPort }
  } else {
    probe.exec = { command: h.command.trim().split(/\s+/).filter(Boolean) }
  }
  return probe
}

/**
 * Render a `ProbeConfig` (httpGet / tcpSocket / exec union) as a single-line
 * string for the read-only Overview panel.
 */
export function probeText(p: ProbeConfig): string {
  if (p.httpGet) {
    const port = p.httpGet.port ? `:${p.httpGet.port}` : ''
    return `GET ${p.httpGet.path ?? '/'}${port}`
  }
  if (p.tcpSocket) return `TCP :${p.tcpSocket.port ?? '?'}`
  if (p.exec) return 'exec'
  return '—'
}
