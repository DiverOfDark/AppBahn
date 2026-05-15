<script setup lang="ts">
/**
 * Multi-port editor: a list of `{ port, expose }` rows with a segmented
 * Public/TCP/Private visibility picker per row and an `+ Add port` button.
 * Used by CreateResourceView and the Settings tab in ResourceDetailView.
 *
 * `id` is a local-only number for v-for keys and stable focus across reorder /
 * removal — Vue's deep-mutation-friendly two-way binding (`defineModel`) keeps
 * the caller's array in sync without prop drilling.
 */
export type Expose = 'Ingress' | 'Tcp' | 'None'

export interface PortRow {
  id: number
  port: number
  expose: Expose
}

const rows = defineModel<PortRow[]>({ required: true })

function addPort() {
  const next = (rows.value.reduce((max, r) => Math.max(max, r.id), 0) ?? 0) + 1
  rows.value.push({ id: next, port: 8080, expose: 'None' })
}

function removePort(id: number) {
  rows.value = rows.value.filter((row) => row.id !== id)
}
</script>

<template>
  <div>
    <div v-if="rows.length" class="port-rows">
      <div v-for="row in rows" :key="row.id" class="port-row">
        <input
          v-model.number="row.port"
          type="number"
          class="form-input port-num"
          min="1"
          max="65535"
          aria-label="Port number"
        />
        <div class="seg port-seg">
          <button
            type="button"
            :class="{ on: row.expose === 'Ingress' }"
            @click="row.expose = 'Ingress'"
          >
            Public
          </button>
          <button type="button" :class="{ on: row.expose === 'Tcp' }" @click="row.expose = 'Tcp'">
            TCP
          </button>
          <button type="button" :class="{ on: row.expose === 'None' }" @click="row.expose = 'None'">
            Private
          </button>
        </div>
        <button
          type="button"
          class="port-remove"
          :title="`Remove port ${row.port}`"
          @click="removePort(row.id)"
        >
          ×
        </button>
      </div>
    </div>
    <button type="button" class="port-add" @click="addPort">+ Add port</button>
  </div>
</template>

<style scoped>
.port-rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}
.port-row {
  display: grid;
  grid-template-columns: 100px 1fr 28px;
  gap: 8px;
  align-items: center;
}
.port-num {
  width: 100%;
}
.form-input {
  width: 100%;
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  font-size: 12px;
  outline: none;
  transition: border-color 120ms ease;
}
.form-input:focus {
  border-color: var(--color-accent);
}
.seg {
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.seg button {
  padding: 7px 14px;
  background: var(--color-bg-base);
  color: var(--color-text-tertiary);
  border: none;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  cursor: pointer;
  border-right: 1px solid var(--color-border);
  transition: color 120ms ease;
}
.seg button:last-child {
  border-right: none;
}
.seg button.on {
  background: var(--color-bg-raised);
  color: var(--color-accent);
}
.seg button:hover:not(.on) {
  color: var(--color-text-secondary);
}
.port-seg {
  width: 100%;
}
.port-remove {
  background: transparent;
  border: none;
  color: var(--color-text-tertiary);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 16px;
  line-height: 1;
}
.port-remove:hover {
  color: var(--color-status-error);
}
.port-add {
  background: transparent;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-tertiary);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  padding: 6px 12px;
  margin-bottom: 6px;
}
.port-add:hover {
  color: var(--color-accent);
  border-color: var(--color-accent-muted);
}
</style>
