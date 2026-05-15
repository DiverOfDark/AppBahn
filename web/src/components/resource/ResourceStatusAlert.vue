<script setup lang="ts">
/**
 * Status-banner that surfaces operator-provided diagnostics whenever a
 * resource isn't Ready: phase tag, the operator's human-readable message,
 * the more-specific `lastError` if it differs, plus any False conditions.
 * Returns nothing when the resource is healthy (or has no real signal yet).
 */
import { computed } from 'vue'
import type { components } from '@/api/schema'

type Resource = components['schemas']['Resource']

const props = defineProps<{
  resource: Resource
}>()

interface StatusAlert {
  severity: 'error' | 'warn'
  tag: string
  headline: string
  detail: string
  failedConditions: components['schemas']['ResourceCondition'][]
}

const alert = computed<StatusAlert | null>(() => {
  const r = props.resource
  const phase = r.status
  if (phase === 'Ready') return null
  const detail = r.statusDetail
  if (!detail) {
    if (phase === 'Pending') return null
    return {
      severity: phase === 'Error' ? 'error' : 'warn',
      tag: severityTag(phase),
      headline: '',
      detail: '',
      failedConditions: [],
    }
  }
  const message = detail.message?.trim() ?? ''
  const lastError = detail.lastError?.trim() ?? ''
  const failedConditions = (detail.conditions ?? []).filter(
    (c) => c.status && c.status.toLowerCase() === 'false',
  )
  // Hide the banner for vanilla "still booting" cases — only surface when the
  // operator has a real grievance.
  if (
    phase === 'Pending' &&
    !lastError &&
    failedConditions.length === 0 &&
    (!message || message === 'Waiting for pods to be ready' || message === 'Waiting for deployment')
  ) {
    return null
  }
  return {
    severity: phase === 'Error' ? 'error' : 'warn',
    tag: severityTag(phase),
    headline: message,
    detail: lastError && lastError !== message ? lastError : '',
    failedConditions,
  }
})

function severityTag(phase: string | undefined): string {
  switch (phase) {
    case 'Error':
      return 'Error'
    case 'Degraded':
      return 'Degraded'
    case 'Stopped':
      return 'Stopped'
    case 'Restarting':
      return 'Restarting'
    case 'Pending':
      return 'Pending'
    default:
      return 'Status'
  }
}
</script>

<template>
  <div v-if="alert" class="status-alert" :class="`status-alert-${alert.severity}`">
    <div class="status-alert-h">
      <span class="status-alert-tag">{{ alert.tag }}</span>
      <span class="status-alert-phase">{{ resource.status ?? 'Unknown' }}</span>
    </div>
    <div v-if="alert.headline" class="status-alert-headline">
      {{ alert.headline }}
    </div>
    <div v-if="alert.detail" class="status-alert-detail mono">
      {{ alert.detail }}
    </div>
    <ul v-if="alert.failedConditions.length" class="status-alert-conditions">
      <li v-for="c in alert.failedConditions" :key="c.type ?? c.message">
        <span class="cond-type mono">{{ c.type ?? 'Condition' }}</span>
        <span v-if="c.reason" class="cond-reason mono">{{ c.reason }}</span>
        <span v-if="c.message" class="cond-msg">{{ c.message }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.status-alert {
  border: 1px solid var(--color-status-error);
  border-radius: var(--radius-md);
  background: oklch(20% 0.04 25 / 0.4);
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.status-alert-warn {
  border-color: var(--color-status-building, oklch(60% 0.13 70));
  background: oklch(20% 0.04 70 / 0.35);
}
.status-alert-h {
  display: flex;
  align-items: center;
  gap: 10px;
}
.status-alert-tag {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-status-error);
  border: 1px solid var(--color-status-error);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}
.status-alert-warn .status-alert-tag {
  color: var(--color-status-building, oklch(60% 0.13 70));
  border-color: var(--color-status-building, oklch(60% 0.13 70));
}
.status-alert-phase {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}
.status-alert-headline {
  font-size: 13px;
  color: var(--color-text-primary);
  font-weight: 500;
  line-height: 1.45;
}
.status-alert-detail {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.55;
  word-break: break-word;
}
.status-alert-conditions {
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-top: 1px solid var(--color-border);
  padding-top: 8px;
}
.status-alert-conditions li {
  font-size: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
}
.cond-type {
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}
.cond-reason {
  font-size: 11px;
  color: var(--color-status-error);
}
.status-alert-warn .cond-reason {
  color: var(--color-status-building, oklch(60% 0.13 70));
}
.cond-msg {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.45;
}
.mono {
  font-family: var(--font-mono);
}
</style>
