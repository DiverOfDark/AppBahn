import type { components } from '@/api/schema'

type Resource = components['schemas']['Resource']

const STATUS_CLASS_MAP: Record<string, string> = {
  READY: 'status-ready',
  PENDING: 'status-pending',
  DEGRADED: 'status-degraded',
  ERROR: 'status-error',
  STOPPED: 'status-stopped',
  RESTARTING: 'status-pending',
  QUEUED: 'status-pending',
  BUILDING: 'status-pending',
  DEPLOYING: 'status-pending',
  SUCCEEDED: 'status-ready',
  FAILED: 'status-error',
  REJECTED: 'status-error',
}

export function statusClass(status?: string | null): string {
  if (!status) return ''
  return STATUS_CLASS_MAP[status] ?? ''
}

export function getDomain(res: Resource | null): string {
  return (
    res?.statusDetail?.customDomains?.[0]?.domain ??
    res?.config?.networking?.ports?.find((p) => p.expose === 'ingress')?.domain ??
    '-'
  )
}
