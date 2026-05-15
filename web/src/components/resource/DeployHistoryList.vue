<script setup lang="ts">
/**
 * Compact deploy-history rendering — one row per deploy, dot + short sha +
 * image/trigger + when. The current `is_primary` row gets the accent dot
 * with a glow; rows whose lifecycle is `Failed` get the red dot.
 *
 * Used by the Overview tab; could be reused on a future dashboard widget.
 */
import type { components } from '@/api/schema'
import EmptyState from '@/components/EmptyState.vue'
import { formatDate } from '@/utils/format'
import { depShortRef } from '@/utils/resource'

type Deployment = components['schemas']['Deployment']

defineProps<{
  deployments: Deployment[]
  /** When set, controls the "View all" button label / hides it if false. */
  viewAllCount?: number
}>()

defineEmits<{
  (e: 'view-all'): void
}>()

function rowClass(dep: Deployment): string {
  if (dep.isPrimary) return 'dep-row-cur'
  if (dep.lifecycle === 'Failed') return 'dep-row-err'
  return ''
}
</script>

<template>
  <div class="panel">
    <div class="panel-h">
      <h3>Deploy history</h3>
      <span class="panel-h-r">
        <button
          v-if="viewAllCount != null && viewAllCount > deployments.length"
          type="button"
          class="btn-link"
          @click="$emit('view-all')"
        >
          View all {{ viewAllCount }} →
        </button>
        <span v-else class="mono">last {{ deployments.length }}</span>
      </span>
    </div>
    <EmptyState v-if="deployments.length === 0" message="No deployments yet." />
    <div v-else class="deploy-history">
      <div v-for="dep in deployments" :key="dep.id" class="dep-row" :class="rowClass(dep)">
        <span class="dep-ic"></span>
        <span class="dep-sha mono">{{ depShortRef(dep) }}</span>
        <span class="dep-msg">
          {{ dep.imageRef ?? '—' }}
          <span class="dep-msg-sub mono">· {{ dep.triggeredBy ?? '—' }}</span>
        </span>
        <span class="dep-when mono">
          {{ dep.isPrimary ? 'CURRENT · ' : '' }}{{ formatDate(dep.createdAt) }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
}
.panel-h {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.panel-h h3 {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.01em;
  margin: 0;
}
.panel-h-r {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.btn-link {
  background: transparent;
  border: none;
  color: var(--color-accent);
  font-family: var(--font-body);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  letter-spacing: 0;
  text-transform: none;
}
.btn-link:hover {
  text-decoration: underline;
}

.deploy-history {
  display: flex;
  flex-direction: column;
}
.dep-row {
  display: grid;
  grid-template-columns: 18px 90px 1fr auto;
  gap: 12px;
  padding: 11px 18px;
  border-bottom: 1px solid var(--color-border);
  align-items: center;
  font-size: 13px;
}
.dep-row:last-child {
  border-bottom: none;
}
.dep-ic {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-status-ready);
  justify-self: center;
}
.dep-row.dep-row-cur .dep-ic {
  background: var(--color-accent);
  box-shadow: 0 0 0 4px oklch(80% 0.16 80 / 0.22);
}
.dep-row.dep-row-err .dep-ic {
  background: var(--color-status-error);
}
.dep-sha {
  font-size: 12px;
  color: var(--color-accent);
}
.dep-msg {
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.dep-msg-sub {
  color: var(--color-text-tertiary);
  margin-left: 8px;
  font-size: 11px;
}
.dep-when {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-align: right;
  white-space: nowrap;
}
.dep-row.dep-row-cur .dep-when {
  color: var(--color-accent);
}
.mono {
  font-family: var(--font-mono);
}
</style>
