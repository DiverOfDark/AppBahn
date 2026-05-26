<script setup lang="ts">
import { computed } from 'vue'
import type { PortRow } from '@/components/resource/PortRowsEditor.vue'
import type { EnvVarRow } from '@/components/resource/EnvVarsEditor.vue'
import type { HealthCheckState } from '@/components/resource/HealthCheckEditor.vue'
import type { Kind, SourceKind } from '@/composables/resource/useResourceCreateForm'

const props = defineProps<{
  name: string
  kind: Kind
  source: SourceKind
  fullImage: string
  ports: PortRow[]
  cpu: number
  memory: number
  minReplicas: number
  maxReplicas: number | undefined
  health: HealthCheckState
  envVars: EnvVarRow[]
  envSlug: string
  promoteEnvSlug?: string
  promoteResourceSlug?: string
  promotionBinding?: 'track' | 'pin'
}>()

const envVarCount = computed(() => props.envVars.filter((r) => r.key.trim()).length)

const promoteSummary = computed(() => {
  if (props.source !== 'promote') return ''
  const env = props.promoteEnvSlug?.trim() ?? ''
  const res = props.promoteResourceSlug?.trim() ?? ''
  if (!env || !res) return '—'
  const binding = props.promotionBinding === 'pin' ? 'pin' : 'track'
  return `${env}/${res} (${binding})`
})

function exposeLabel(expose: PortRow['expose']): string {
  switch (expose) {
    case 'Ingress':
      return 'pub'
    case 'Tcp':
      return 'tcp'
    default:
      return 'priv'
  }
}
</script>

<template>
  <aside class="aside">
    <div class="panel summary">
      <div class="panel-h">
        <h3>Summary</h3>
        <p>Live preview of the resource you're about to create.</p>
      </div>
      <div class="summary-rows">
        <div class="srow">
          <span class="sk">name</span>
          <span class="sv mono">
            {{ name || '—' }}
            <span v-if="name" class="pill">new</span>
          </span>
        </div>
        <div class="srow">
          <span class="sk">kind</span>
          <span class="sv mono">{{ kind }}</span>
        </div>
        <div class="srow">
          <span class="sk">source</span>
          <span class="sv mono">{{ source }}</span>
        </div>
        <div v-if="source !== 'promote'" class="srow">
          <span class="sk">image</span>
          <span class="sv mono trunc" :title="fullImage">{{ fullImage || '—' }}</span>
        </div>
        <div v-else class="srow">
          <span class="sk">promote</span>
          <span class="sv mono trunc" :title="promoteSummary">{{ promoteSummary }}</span>
        </div>
        <div class="srow">
          <span class="sk">ports</span>
          <span class="sv mono trunc">
            <template v-if="ports.length">
              <span v-for="(row, i) in ports" :key="row.id">
                <template v-if="i > 0">, </template>{{ row.port }}/{{ exposeLabel(row.expose) }}
              </span>
            </template>
            <template v-else>—</template>
          </span>
        </div>
        <div class="srow">
          <span class="sk">cpu / mem</span>
          <span class="sv mono">{{ cpu }}m · {{ memory }}Mi</span>
        </div>
        <div class="srow">
          <span class="sk">replicas</span>
          <span class="sv mono">
            {{ minReplicas }}<template v-if="maxReplicas"> → {{ maxReplicas }}</template>
          </span>
        </div>
        <div class="srow">
          <span class="sk">health</span>
          <span class="sv mono">
            <template v-if="health.enabled">{{ health.mode }}</template>
            <template v-else>off</template>
          </span>
        </div>
        <div class="srow">
          <span class="sk">env vars</span>
          <span class="sv mono">{{ envVarCount }}</span>
        </div>
        <div class="srow">
          <span class="sk">environment</span>
          <span class="sv mono trunc">{{ envSlug }}</span>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
@import '@/assets/form-fields.css';

.aside {
  display: flex;
  flex-direction: column;
  gap: 14px;
  /* `align-self: start` keeps the aside at its intrinsic height (instead of
     stretching to the row), which is what `position: sticky` needs to have
     room to scroll past it. */
  align-self: start;
  position: sticky;
  top: 16px;
}

@media (max-width: 1100px) {
  .aside {
    position: static;
  }
}

.summary .panel-body {
  padding: 0;
}
.summary-rows {
  display: flex;
  flex-direction: column;
}
.srow {
  padding: 10px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
  font-size: 12px;
  gap: 12px;
}
.srow:last-child {
  border-bottom: none;
}
.sk {
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
}
.sv {
  color: var(--color-text-primary);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  display: flex;
  gap: 6px;
  align-items: center;
  text-align: right;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 200px;
}
.sv .pill {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 2px 6px;
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
  border-radius: var(--radius-sm);
}
.sv.trunc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
}
</style>
