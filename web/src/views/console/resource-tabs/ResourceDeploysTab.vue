<script setup lang="ts">
/**
 * Full Deploys tab — table of every deployment row for the resource. Status
 * column carries the "Current" / "Replaced" / lifecycle pill; rows are dimmed
 * when not the current primary so the live release reads as the hero.
 *
 * Per-row Cancel / Retry actions: Cancel is shown while the deployment is in
 * `Queued` or `Building` (the platform's cancellable window); past `Built` the
 * rollout owns the row and the API returns 409. Retry is offered on terminal
 * lifecycles (`Failed`, `Canceled`, `Superseded`) — it mints a fresh row that
 * reuses the original commit / image digest.
 */
import type { components } from '@/api/schema'
import EmptyState from '@/components/EmptyState.vue'
import { statusClass } from '@/composables/resource/useResourceHelpers'
import { formatDate } from '@/utils/format'

type Deployment = components['schemas']['Deployment']
type Lifecycle = NonNullable<Deployment['lifecycle']>

defineProps<{
  deployments: Deployment[]
  pendingDeploymentId?: string | null
}>()

const emit = defineEmits<{
  cancel: [deploymentId: string]
  retry: [deploymentId: string]
}>()

const CANCELLABLE: ReadonlySet<Lifecycle> = new Set(['Queued', 'Building'])
const TERMINAL: ReadonlySet<Lifecycle> = new Set(['Failed', 'Canceled', 'Superseded'])

function canCancel(dep: Deployment): boolean {
  return !!dep.lifecycle && CANCELLABLE.has(dep.lifecycle)
}
function canRetry(dep: Deployment): boolean {
  return !!dep.lifecycle && TERMINAL.has(dep.lifecycle)
}

interface DepBadge {
  label: string
  cssClass: string
}
function depBadge(dep: Deployment): DepBadge {
  if (dep.isPrimary) return { label: 'Current', cssClass: 'status-current' }
  if (dep.lifecycle === 'Active') return { label: 'Replaced', cssClass: 'status-replaced' }
  return { label: dep.lifecycle ?? 'Unknown', cssClass: statusClass(dep.lifecycle) }
}
function depRowClass(dep: Deployment): string {
  return dep.isPrimary ? 'dep-row-current' : 'dep-row-past'
}

function onCancel(dep: Deployment) {
  if (!dep.id) return
  emit('cancel', dep.id)
}
function onRetry(dep: Deployment) {
  if (!dep.id) return
  emit('retry', dep.id)
}
</script>

<template>
  <section class="content-block">
    <div class="panel">
      <div class="panel-h">
        <h3>Deployments</h3>
        <span class="panel-h-r mono">{{ deployments.length }} entries</span>
      </div>
      <EmptyState v-if="deployments.length === 0" message="No deployments yet." />
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>Status</th>
              <th>Image</th>
              <th>Trigger</th>
              <th>Created</th>
              <th class="th-actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="dep in deployments" :key="dep.id" :class="depRowClass(dep)">
              <td>
                <span class="status-badge" :class="depBadge(dep).cssClass">
                  {{ depBadge(dep).label }}
                </span>
              </td>
              <td class="cell-mono">{{ dep.imageRef ?? '—' }}</td>
              <td>{{ dep.triggeredBy }}</td>
              <td class="cell-mono">{{ formatDate(dep.createdAt) }}</td>
              <td class="cell-actions">
                <button
                  v-if="canCancel(dep)"
                  type="button"
                  class="btn-secondary btn-sm dep-cancel-btn"
                  :disabled="pendingDeploymentId === dep.id"
                  @click="onCancel(dep)"
                >
                  {{ pendingDeploymentId === dep.id ? 'Cancelling…' : 'Cancel' }}
                </button>
                <button
                  v-else-if="dep.lifecycle === 'Built' || dep.lifecycle === 'Activating'"
                  type="button"
                  class="btn-secondary btn-sm dep-cancel-btn"
                  disabled
                  title="Cancel is only available while the deployment is Queued or Building. Past Built, use rollback to revert."
                >
                  Cancel
                </button>
                <button
                  v-if="canRetry(dep)"
                  type="button"
                  class="btn-secondary btn-sm dep-retry-btn"
                  :disabled="pendingDeploymentId === dep.id"
                  @click="onRetry(dep)"
                >
                  {{ pendingDeploymentId === dep.id ? 'Retrying…' : 'Retry' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
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
}
.panel-h h3 {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 600;
  margin: 0;
}
.panel-h-r {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.table-wrap {
  overflow-x: auto;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.data-table th {
  padding: 10px 16px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 500;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}
.th-actions {
  text-align: right;
}
.data-table td {
  padding: 11px 16px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-weight: 300;
  vertical-align: middle;
}
.data-table tbody tr:last-child td {
  border-bottom: none;
}
.cell-mono {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-primary);
  font-weight: 400;
}
.cell-actions {
  text-align: right;
  white-space: nowrap;
}
.cell-actions .btn-sm {
  padding: 4px 10px;
  font-size: 12px;
  margin-left: 6px;
}
.cell-actions .btn-sm:first-child {
  margin-left: 0;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 500;
  padding: 3px 9px 2px;
  border-radius: 2px;
  background: transparent;
  border: 1px solid currentColor;
}
.status-current {
  color: var(--color-accent);
}
.status-replaced {
  color: var(--color-text-tertiary);
}
.dep-row-past td {
  opacity: 0.72;
}
.dep-row-current td {
  background: oklch(20% 0.04 80 / 0.18);
}
</style>
