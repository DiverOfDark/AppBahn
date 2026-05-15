<script setup lang="ts">
/**
 * Domains tab — custom-domain status (from operator's statusDetail.customDomains)
 * plus a read-only list of Ingress-exposed ports. No editing yet (the backend
 * doesn't surface a "addCustomDomain" endpoint that the platform respects).
 */
import { computed } from 'vue'
import type { components } from '@/api/schema'
import EmptyState from '@/components/EmptyState.vue'

type Resource = components['schemas']['Resource']

const props = defineProps<{
  resource: Resource
}>()

const ingressPorts = computed(
  () => props.resource.config?.networking?.ports?.filter((p) => p.expose === 'Ingress') ?? [],
)
const customDomains = computed(() => props.resource.statusDetail?.customDomains ?? [])

function domStatusClass(status?: string): string {
  if (!status) return ''
  const s = status.toLowerCase()
  if (s === 'ready' || s === 'active') return 'ok'
  if (s === 'pending' || s === 'verifying') return 'bld'
  if (s === 'error' || s === 'failed') return 'err'
  return ''
}
</script>

<template>
  <section class="content-block content-stack">
    <div class="panel">
      <div class="panel-h">
        <h3>Custom domains</h3>
        <span class="panel-h-r mono">{{ customDomains.length }} attached</span>
      </div>
      <EmptyState v-if="customDomains.length === 0" message="No custom domains attached." />
      <div v-else class="dom-list">
        <div v-for="d in customDomains" :key="d.domain" class="dom-row">
          <span class="dom-status-dot" :class="domStatusClass(d.status)"></span>
          <a :href="`https://${d.domain}`" target="_blank" class="dom-host mono">
            {{ d.domain }} ↗
          </a>
          <span v-if="d.port" class="dom-port mono">:{{ d.port }}</span>
          <span class="dom-state mono">{{ d.status ?? 'unknown' }}</span>
        </div>
      </div>
    </div>

    <div v-if="ingressPorts.length" class="panel">
      <div class="panel-h">
        <h3>Ingress ports</h3>
        <span class="panel-h-r mono">platform-managed</span>
      </div>
      <div class="dom-list">
        <div v-for="p in ingressPorts" :key="p.port" class="dom-row">
          <span class="dom-status-dot ok"></span>
          <span class="dom-host mono">port {{ p.port }}</span>
          <span v-if="p.domain" class="dom-state mono">{{ p.domain }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.content-block {
  display: block;
}
.content-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
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

.dom-list {
  display: flex;
  flex-direction: column;
}
.dom-row {
  display: grid;
  grid-template-columns: 12px 1fr auto auto;
  gap: 12px;
  align-items: center;
  padding: 11px 16px;
  border-bottom: 1px solid var(--color-border);
  font-size: 13px;
}
.dom-row:last-child {
  border-bottom: none;
}
.dom-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-text-tertiary);
}
.dom-status-dot.ok {
  background: var(--color-status-ready);
}
.dom-status-dot.bld {
  background: var(--color-status-building);
}
.dom-status-dot.err {
  background: var(--color-status-error);
}
.dom-host {
  color: var(--color-text-primary);
  text-decoration: none;
  font-size: 12px;
}
.dom-host:hover {
  color: var(--color-accent);
}
.dom-port {
  color: var(--color-text-tertiary);
  font-size: 11px;
}
.dom-state {
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}
</style>
