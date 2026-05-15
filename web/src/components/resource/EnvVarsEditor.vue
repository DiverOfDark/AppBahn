<script setup lang="ts">
/**
 * Key/value environment-variable editor. Rows are `{ key, value }` with an
 * inline `× Remove` and an `+ Add variable` button. The `id` is a local-only
 * stable key for v-for / focus management — callers only care about
 * key/value when assembling the final `{ env: { … } }` config.
 */
export interface EnvVarRow {
  id: number
  key: string
  value: string
}

const rows = defineModel<EnvVarRow[]>({ required: true })

function addVar() {
  const next = (rows.value.reduce((max, r) => Math.max(max, r.id), 0) ?? 0) + 1
  rows.value.push({ id: next, key: '', value: '' })
}

function removeVar(id: number) {
  rows.value = rows.value.filter((row) => row.id !== id)
}
</script>

<template>
  <div>
    <div v-if="rows.length" class="env-rows">
      <div v-for="row in rows" :key="row.id" class="env-row">
        <input
          v-model="row.key"
          class="form-input env-key"
          placeholder="DATABASE_URL"
          spellcheck="false"
          autocomplete="off"
        />
        <input
          v-model="row.value"
          class="form-input env-val"
          placeholder="postgres://…"
          spellcheck="false"
          autocomplete="off"
        />
        <button
          type="button"
          class="env-remove"
          :title="`Remove ${row.key || 'variable'}`"
          @click="removeVar(row.id)"
        >
          ×
        </button>
      </div>
    </div>
    <button type="button" class="env-add" @click="addVar">+ Add variable</button>
  </div>
</template>

<style scoped>
.env-rows {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-base);
  margin: 14px 0 10px;
  overflow: hidden;
}
.env-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) 1.5fr 28px;
  border-bottom: 1px solid var(--color-border);
}
.env-row:last-child {
  border-bottom: none;
}
.env-key,
.env-val {
  border: none;
  border-radius: 0;
  background: transparent;
  border-right: 1px solid var(--color-border);
  padding: 8px 12px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-primary);
  outline: none;
  min-width: 0;
}
.env-key:focus,
.env-val:focus {
  background: oklch(20% 0.04 80 / 0.25);
}
.env-remove {
  background: transparent;
  border: none;
  color: var(--color-text-tertiary);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 16px;
  line-height: 1;
}
.env-remove:hover {
  color: var(--color-status-error);
}
.env-add {
  background: transparent;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-tertiary);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  padding: 6px 12px;
}
.env-add:hover {
  color: var(--color-accent);
  border-color: var(--color-accent-muted);
}
</style>
