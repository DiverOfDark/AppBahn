<script setup lang="ts">
/**
 * Resource-detail page header — icon + h1 + status pill + meta line on the
 * left, Restart + Delete actions on the right. Lives in its own component so
 * future resource-scoped routes (logs, metrics, …) can reuse the same chrome.
 */
import { computed } from 'vue'
import type { components } from '@/api/schema'
import ConfirmButton from '@/components/ConfirmButton.vue'
import { statusClass, getDomain } from '@/composables/resource/useResourceHelpers'
import { initials } from '@/utils/resource'

type Resource = components['schemas']['Resource']
type PendingActionKind = 'pause' | 'resume' | 'restart'

const props = defineProps<{
  resource: Resource
  pendingAction: PendingActionKind | null
  // Async delete handler — ConfirmButton awaits this and re-arms on
  // rejection. Allows the header + danger-zone delete buttons to share one
  // failure-recovery path without ref-based coordination from the parent.
  onDelete: () => Promise<void>
}>()

const emit = defineEmits<{
  (e: 'restart'): void
}>()

const liveDomain = computed(() => getDomain(props.resource))
</script>

<template>
  <header class="r-head">
    <div class="r-head-l">
      <div class="r-head-ic">{{ initials(resource.name) }}</div>
      <div class="r-head-text">
        <h1 class="r-head-h1">
          {{ resource.name }}
          <span class="status-badge" :class="statusClass(resource.status)">
            <span class="status-dot"></span>{{ resource.status ?? 'Unknown' }}
          </span>
        </h1>
        <div class="r-head-meta">
          <span>
            <b>{{ resource.type }}</b>
          </span>
          <span v-if="resource.statusDetail?.replicas">
            <b
              >{{ resource.statusDetail.replicas.ready ?? 0 }}/{{
                resource.statusDetail.replicas.desired ?? 0
              }}</b
            >
            pods
            <template v-if="resource.config?.hosting?.cpu">
              · {{ resource.config.hosting.cpu }} cpu
            </template>
            <template v-if="resource.config?.hosting?.memory">
              · {{ resource.config.hosting.memory }} mem
            </template>
          </span>
          <span
            v-if="resource.statusDetail?.activeRelease?.imageRef"
            class="r-head-meta-trunc"
            :title="resource.statusDetail.activeRelease.imageRef"
          >
            Image ·
            <span class="accent">{{ resource.statusDetail.activeRelease.imageRef }}</span>
          </span>
          <span v-if="liveDomain && liveDomain !== '-'">
            Domain ·
            <a :href="`https://${liveDomain}`" target="_blank" class="accent">
              {{ liveDomain }} ↗
            </a>
          </span>
        </div>
      </div>
    </div>
    <div class="r-head-actions">
      <button
        type="button"
        class="btn-secondary"
        :class="{ 'danger-btn-pending': pendingAction === 'restart' }"
        :disabled="pendingAction !== null"
        @click="emit('restart')"
      >
        <span v-if="pendingAction === 'restart'" class="spinner"></span>
        {{ pendingAction === 'restart' ? 'Restarting…' : 'Restart' }}
      </button>
      <ConfirmButton :handler="onDelete" />
    </div>
  </header>
</template>

<style scoped>
.r-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin: 8px 0 18px;
}
.r-head-l {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  min-width: 0;
}
.r-head-text {
  min-width: 0;
}
.r-head-ic {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: oklch(20% 0.04 80);
  border: 1px solid var(--color-accent-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-accent);
  flex-shrink: 0;
}
.r-head-h1 {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.018em;
  margin: 0 0 6px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--color-text-primary);
}
.r-head-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  align-items: center;
}
.r-head-meta b {
  color: var(--color-text-secondary);
  font-weight: 500;
}
.r-head-meta a {
  text-decoration: none;
}
.r-head-meta a:hover {
  text-decoration: underline;
}
.r-head-meta-trunc {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.r-head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}
.accent {
  color: var(--color-accent);
}

/* Outline-only status pill — `currentColor` border tracks the color set by
   the .status-{ready,error,…} colour class on the parent. */
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
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

/* Spinner shared with danger-zone pending buttons. */
.spinner {
  width: 12px;
  height: 12px;
  border: 1.5px solid var(--color-accent-muted);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
  margin-right: 4px;
}
.danger-btn-pending {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  opacity: 1;
  color: var(--color-accent);
  border-color: var(--color-accent-muted);
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
