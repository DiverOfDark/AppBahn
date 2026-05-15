<script setup lang="ts">
/**
 * Environment tab — note banner explaining the per-service scope, search-filter
 * toolbar, table of (key, value, scope) rows, and a count footer. Read-only;
 * edits go through the Settings tab (linked from the note + toolbar).
 */
import { computed, ref } from 'vue'
import type { components } from '@/api/schema'
import EmptyState from '@/components/EmptyState.vue'
import { formatDate } from '@/utils/format'

type Resource = components['schemas']['Resource']

const props = defineProps<{
  resource: Resource
}>()

const emit = defineEmits<{
  (e: 'navigate-settings'): void
}>()

const envEntries = computed<Array<[string, string]>>(() => {
  const env = props.resource.config?.env ?? {}
  return Object.entries(env).sort(([a], [b]) => a.localeCompare(b))
})

const envFilter = ref('')
const filteredEnvEntries = computed<Array<[string, string]>>(() => {
  const q = envFilter.value.trim().toLowerCase()
  if (!q) return envEntries.value
  return envEntries.value.filter(([k]) => k.toLowerCase().includes(q))
})
</script>

<template>
  <section class="content-block content-stack">
    <div class="env-note">
      <span class="env-note-glyph">▸</span>
      <span>
        Variables are scoped to this <b>service</b>. Edit them in
        <a href="#" class="env-note-link" @click.prevent="emit('navigate-settings')">Settings</a>;
        saving restarts all pods.
      </span>
      <span class="env-note-meta">last sync · {{ formatDate(resource.lastSyncedAt) }}</span>
    </div>

    <div class="env-toolbar">
      <div class="env-search">
        <span class="env-search-glyph">⌕</span>
        <input v-model="envFilter" placeholder="Filter by key…" />
      </div>
      <button type="button" class="btn-secondary" @click="emit('navigate-settings')">
        Edit in settings
      </button>
    </div>

    <div class="panel envt-panel">
      <EmptyState v-if="envEntries.length === 0" message="No environment variables configured." />
      <div v-else-if="filteredEnvEntries.length === 0" class="envt-empty mono">
        No variables match "{{ envFilter }}"
      </div>
      <div v-else class="table-wrap">
        <table class="data-table envt">
          <thead>
            <tr>
              <th>Key</th>
              <th>Value</th>
              <th>Scope</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="[k, v] in filteredEnvEntries" :key="k">
              <td class="cell-mono envt-k">{{ k }}</td>
              <td class="cell-mono envt-v" :title="v">{{ v }}</td>
              <td>
                <span class="scope-tag scope-tag-svc">service</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="envEntries.length" class="envt-foot mono">
        <span>{{ filteredEnvEntries.length }} of {{ envEntries.length }} shown</span>
        <span>changes restart pods</span>
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

.env-note {
  background: oklch(20% 0.04 80 / 0.35);
  border: 1px solid var(--color-accent-muted);
  border-radius: var(--radius-md);
  padding: 14px 18px;
  font-size: 13px;
  color: var(--color-text-secondary);
  display: flex;
  gap: 14px;
  align-items: center;
}
.env-note b {
  color: var(--color-accent);
}
.env-note-glyph {
  font-family: var(--font-heading);
  font-size: 18px;
  color: var(--color-accent);
}
.env-note-link {
  color: var(--color-accent);
  text-decoration: none;
}
.env-note-link:hover {
  text-decoration: underline;
}
.env-note-meta {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  margin-left: auto;
}

.env-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.env-search {
  flex: 1;
  max-width: 320px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border-strong, var(--color-border));
  border-radius: var(--radius-sm);
  padding: 7px 12px;
  font-size: 13px;
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--color-text-tertiary);
}
.env-search-glyph {
  font-family: var(--font-mono);
}
.env-search input {
  background: transparent;
  border: none;
  outline: none;
  color: var(--color-text-primary);
  flex: 1;
  font-family: var(--font-body);
  font-size: 13px;
}

.envt-panel .table-wrap {
  border-radius: var(--radius-md);
  overflow: hidden;
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
.data-table td {
  padding: 11px 16px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  vertical-align: middle;
}
.data-table tbody tr:last-child td {
  border-bottom: none;
}
.cell-mono {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-primary);
}
.envt-k {
  color: var(--color-text-primary);
  font-weight: 500;
}
.envt-v {
  color: var(--color-text-secondary);
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.envt tr:hover td {
  background: var(--color-bg-raised, var(--color-bg-base));
}
.scope-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 2px 7px;
  border-radius: 2px;
  border: 1px solid var(--color-border);
  color: var(--color-text-tertiary);
}
.scope-tag-svc {
  color: var(--color-accent);
  border-color: var(--color-accent-muted);
}
.envt-empty {
  padding: 24px 18px;
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: 12px;
}
.envt-foot {
  padding: 14px 18px;
  border-top: 1px solid var(--color-border);
  font-size: 11px;
  color: var(--color-text-tertiary);
  display: flex;
  justify-content: space-between;
  letter-spacing: 0.04em;
}
.mono {
  font-family: var(--font-mono);
}
</style>
