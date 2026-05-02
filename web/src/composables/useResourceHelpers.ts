import type { components } from '@/api/schema'

type Resource = components['schemas']['Resource']

const STATUS_CLASS_MAP: Record<string, string> = {
  Ready: 'status-ready',
  Pending: 'status-pending',
  Degraded: 'status-degraded',
  Error: 'status-error',
  Stopped: 'status-stopped',
  Restarting: 'status-pending',
  Queued: 'status-pending',
  Building: 'status-pending',
  Deploying: 'status-pending',
  Succeeded: 'status-ready',
  Failed: 'status-error',
  Rejected: 'status-error',
}

export function statusClass(status?: string | null): string {
  if (!status) return ''
  return STATUS_CLASS_MAP[status] ?? ''
}

export function getDomain(res: Resource | null): string {
  return (
    res?.statusDetail?.customDomains?.[0]?.domain ??
    res?.config?.networking?.ports?.find((p) => p.expose === 'Ingress')?.domain ??
    '-'
  )
}
