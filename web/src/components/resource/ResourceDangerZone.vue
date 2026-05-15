<script setup lang="ts">
/**
 * Danger-zone panel for the resource Settings tab: Pause/Resume · Restart ·
 * Delete. Emits action requests upward so the parent (which owns the
 * cross-tab pending state) can run them. Pending state drives a spinner +
 * "Stopping…/Resuming…/Restarting…" label on the affected button.
 */
import type { components } from '@/api/schema'
import ConfirmButton from '@/components/ConfirmButton.vue'

type Resource = components['schemas']['Resource']
type PendingActionKind = 'pause' | 'resume' | 'restart'

defineProps<{
  resource: Resource
  pendingAction: PendingActionKind | null
  effectiveStopped: boolean
  // Async delete handler — see ResourceHeader.vue for the rationale.
  onDelete: () => Promise<void>
}>()

defineEmits<{
  (e: 'pause'): void
  (e: 'resume'): void
  (e: 'restart'): void
}>()
</script>

<template>
  <div class="panel danger-zone">
    <div class="panel-h">
      <h3 class="danger-h">Danger zone</h3>
      <p>Irreversible or service-affecting actions.</p>
    </div>

    <div class="danger-row">
      <div class="danger-info">
        <div class="danger-t">
          {{ effectiveStopped ? 'Resume resource' : 'Pause resource' }}
        </div>
        <div class="danger-d">
          {{
            effectiveStopped
              ? 'Scale back up to the configured replicas.'
              : 'Scale to 0 pods. Configuration preserved. Resume any time.'
          }}
        </div>
      </div>
      <button
        type="button"
        class="danger-btn"
        :class="{
          'danger-btn-pending': pendingAction === 'pause' || pendingAction === 'resume',
        }"
        :disabled="pendingAction !== null"
        @click="effectiveStopped ? $emit('resume') : $emit('pause')"
      >
        <span v-if="pendingAction === 'pause' || pendingAction === 'resume'" class="spinner"></span>
        {{
          pendingAction === 'pause'
            ? 'Stopping…'
            : pendingAction === 'resume'
              ? 'Resuming…'
              : effectiveStopped
                ? 'Resume'
                : 'Pause'
        }}
      </button>
    </div>

    <div class="danger-row">
      <div class="danger-info">
        <div class="danger-t">Force restart all pods</div>
        <div class="danger-d">Recreate every pod sequentially.</div>
      </div>
      <button
        type="button"
        class="danger-btn"
        :class="{ 'danger-btn-pending': pendingAction === 'restart' }"
        :disabled="pendingAction !== null"
        @click="$emit('restart')"
      >
        <span v-if="pendingAction === 'restart'" class="spinner"></span>
        {{ pendingAction === 'restart' ? 'Restarting…' : 'Restart' }}
      </button>
    </div>

    <div class="danger-row">
      <div class="danger-info">
        <div class="danger-t danger-bad">Delete resource</div>
        <div class="danger-d">
          Remove {{ resource.name }} permanently. Pods, deploy history, env vars and any attached
          domains are deleted.
        </div>
      </div>
      <ConfirmButton class="danger-btn-danger" :handler="onDelete" />
    </div>
  </div>
</template>

<style scoped>
.panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
}
.danger-zone {
  border-color: oklch(45% 0.15 25);
}
.panel-h {
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: flex-start;
}
.danger-zone .panel-h {
  background: oklch(20% 0.04 25);
  border-bottom-color: oklch(45% 0.15 25);
}
.panel-h h3 {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  margin: 0;
}
.panel-h p {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0;
  font-weight: 300;
}
.danger-h {
  color: var(--color-status-error);
}

.danger-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 18px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
  align-items: center;
}
.danger-row:last-child {
  border-bottom: none;
}
.danger-info .danger-t {
  font-size: 13px;
  color: var(--color-text-primary);
  font-weight: 500;
}
.danger-info .danger-bad {
  color: var(--color-status-error);
}
.danger-info .danger-d {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 3px;
  font-weight: 300;
}
.danger-btn {
  background: transparent;
  border: 1px solid var(--color-border-strong);
  color: var(--color-text-secondary);
  padding: 7px 14px;
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 12px;
  cursor: pointer;
}
.danger-btn:hover:not(:disabled) {
  color: var(--color-text-primary);
  border-color: var(--color-text-secondary);
}
.danger-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.danger-btn-pending {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  opacity: 1;
  color: var(--color-accent);
  border-color: var(--color-accent-muted);
}
.spinner {
  width: 12px;
  height: 12px;
  border: 1.5px solid var(--color-accent-muted);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
