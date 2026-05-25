<script setup lang="ts">
/**
 * Overview tab — metric strip (full width), then a 2-col grid:
 *   left: status alert (when non-Ready) + deploy history
 *   right: Configuration, Health checks, Environment preview
 */
import { computed } from 'vue'
import type { components } from '@/api/schema'
import ResourceStatusAlert from '@/components/resource/ResourceStatusAlert.vue'
import DeployHistoryList from '@/components/resource/DeployHistoryList.vue'
import { formatDate } from '@/utils/format'
import { probeText } from '@/utils/resource'

type Resource = components['schemas']['Resource']
type Deployment = components['schemas']['Deployment']
type ProbeOutcome = NonNullable<
  NonNullable<components['schemas']['ResourceStatusDetail']['probeStatus']>['liveness']
>

const props = defineProps<{
  resource: Resource
  deployments: Deployment[]
}>()

function probeOutcome(slot: 'liveness' | 'readiness' | 'startup'): ProbeOutcome | undefined {
  return props.resource.statusDetail?.probeStatus?.[slot]
}

function probeDotClass(o?: ProbeOutcome): string {
  if (!o) return 'probe-dot probe-dot-idle'
  if (o.ok === true) return 'probe-dot probe-dot-ok'
  if (o.ok === false) return 'probe-dot probe-dot-fail'
  return 'probe-dot probe-dot-idle'
}

function probeLatencyText(o?: ProbeOutcome): string {
  if (!o || o.lastLatencyMs == null) return ''
  return `${o.lastLatencyMs}ms`
}

const emit = defineEmits<{
  (e: 'navigate-deploys'): void
  (e: 'navigate-environment'): void
}>()

const recentDeploys = computed(() => props.deployments.slice(0, 6))

const envEntries = computed<Array<[string, string]>>(() => {
  const env = props.resource.config?.env ?? {}
  return Object.entries(env).sort(([a], [b]) => a.localeCompare(b))
})
const ENV_PREVIEW_LIMIT = 6
const envPreview = computed<Array<[string, string]>>(() =>
  envEntries.value.slice(0, ENV_PREVIEW_LIMIT),
)

const activeImageRef = computed(() => props.resource.statusDetail?.activeRelease?.imageRef ?? '')
const activeImageShort = computed(() => {
  const ref = activeImageRef.value
  if (!ref) return ''
  const at = ref.lastIndexOf('@')
  if (at > 0) {
    const digest = ref.slice(at + 1)
    const short = digest.startsWith('sha256:') ? digest.slice(7, 14) : digest.slice(0, 12)
    return ref.slice(0, at).split('/').pop() + '@' + short
  }
  return ref.split('/').pop() ?? ref
})
</script>

<template>
  <section class="content-grid">
    <div class="main-col">
      <ResourceStatusAlert :resource="resource" />

      <!-- Metric strip — full-width 4-cell summary above the panel pair. No
           sparklines yet (no metrics backend); each cell shows the
           authoritative spec/status value. -->
      <div class="metrics-strip">
        <div class="metric-cell">
          <div class="metric-l">Replicas</div>
          <div class="metric-v">
            {{ resource.statusDetail?.replicas?.ready ?? 0 }}
            <span class="metric-u"
              >/ {{ resource.statusDetail?.replicas?.desired ?? 0 }} ready</span
            >
          </div>
        </div>
        <div class="metric-cell">
          <div class="metric-l">CPU</div>
          <div class="metric-v">
            {{ resource.config?.hosting?.cpu ?? '—' }}
            <span class="metric-u">per pod</span>
          </div>
        </div>
        <div class="metric-cell">
          <div class="metric-l">Memory</div>
          <div class="metric-v">
            {{ resource.config?.hosting?.memory ?? '—' }}
            <span class="metric-u">per pod</span>
          </div>
        </div>
        <div class="metric-cell">
          <div class="metric-l">Image</div>
          <div class="metric-v metric-v-mono" :title="activeImageRef">
            {{ activeImageShort || '—' }}
          </div>
          <div v-if="resource.statusDetail?.activeRelease?.activatedAt" class="metric-delta">
            activated · {{ formatDate(resource.statusDetail.activeRelease.activatedAt) }}
          </div>
        </div>
      </div>

      <DeployHistoryList
        :deployments="recentDeploys"
        :view-all-count="deployments.length"
        @view-all="emit('navigate-deploys')"
      />
    </div>

    <aside class="side-col">
      <div class="panel">
        <div class="panel-h">
          <h3>Configuration</h3>
          <span class="panel-h-r mono">live</span>
        </div>
        <div class="panel-body kv">
          <div v-if="resource.config?.hosting?.minReplicas !== undefined" class="kv-row">
            <span class="k">Replicas</span>
            <span class="v">
              {{ resource.config.hosting.minReplicas }}
              <template v-if="resource.config.hosting.maxReplicas">
                / {{ resource.config.hosting.maxReplicas }} max
              </template>
            </span>
          </div>
          <div v-if="resource.config?.hosting?.cpu" class="kv-row">
            <span class="k">CPU limit</span>
            <span class="v">{{ resource.config.hosting.cpu }}</span>
          </div>
          <div v-if="resource.config?.hosting?.memory" class="kv-row">
            <span class="k">Memory limit</span>
            <span class="v">{{ resource.config.hosting.memory }}</span>
          </div>
          <div v-if="resource.config?.runMode" class="kv-row">
            <span class="k">Run mode</span>
            <span class="v">{{ resource.config.runMode }}</span>
          </div>
          <div class="kv-row">
            <span class="k">Type</span>
            <span class="v">{{ resource.type }}</span>
          </div>
        </div>
      </div>

      <div v-if="resource.config?.healthCheck" class="panel">
        <div class="panel-h">
          <h3>Health checks</h3>
          <span class="panel-h-r mono">paths</span>
        </div>
        <div class="panel-body hc">
          <div
            v-if="resource.config.healthCheck.liveness"
            class="hc-row"
            data-testid="hc-row-liveness"
          >
            <span class="hc-name">
              <span
                :class="probeDotClass(probeOutcome('liveness'))"
                data-testid="probe-dot-liveness"
              />
              Liveness
            </span>
            <span class="hc-path mono">
              {{ probeText(resource.config.healthCheck.liveness) }}
              <span
                v-if="probeLatencyText(probeOutcome('liveness'))"
                class="hc-lat mono"
                data-testid="probe-lat-liveness"
              >
                · {{ probeLatencyText(probeOutcome('liveness')) }}
              </span>
            </span>
          </div>
          <div
            v-if="resource.config.healthCheck.readiness"
            class="hc-row"
            data-testid="hc-row-readiness"
          >
            <span class="hc-name">
              <span
                :class="probeDotClass(probeOutcome('readiness'))"
                data-testid="probe-dot-readiness"
              />
              Readiness
            </span>
            <span class="hc-path mono">
              {{ probeText(resource.config.healthCheck.readiness) }}
              <span
                v-if="probeLatencyText(probeOutcome('readiness'))"
                class="hc-lat mono"
                data-testid="probe-lat-readiness"
              >
                · {{ probeLatencyText(probeOutcome('readiness')) }}
              </span>
            </span>
          </div>
          <div
            v-if="resource.config.healthCheck.startup"
            class="hc-row"
            data-testid="hc-row-startup"
          >
            <span class="hc-name">
              <span
                :class="probeDotClass(probeOutcome('startup'))"
                data-testid="probe-dot-startup"
              />
              Startup
            </span>
            <span class="hc-path mono">
              {{ probeText(resource.config.healthCheck.startup) }}
              <span
                v-if="probeLatencyText(probeOutcome('startup'))"
                class="hc-lat mono"
                data-testid="probe-lat-startup"
              >
                · {{ probeLatencyText(probeOutcome('startup')) }}
              </span>
            </span>
          </div>
        </div>
      </div>

      <div v-if="envEntries.length" class="panel">
        <div class="panel-h">
          <h3>Environment</h3>
          <span class="panel-h-r mono">
            {{ envEntries.length }} {{ envEntries.length === 1 ? 'var' : 'vars' }}
          </span>
        </div>
        <div class="env-preview">
          <div v-for="[k, v] in envPreview" :key="k" class="env-preview-row">
            <span class="env-preview-k mono">{{ k }}</span>
            <span class="env-preview-v mono">{{ v }}</span>
          </div>
          <button
            v-if="envEntries.length > envPreview.length"
            type="button"
            class="env-preview-more"
            @click="emit('navigate-environment')"
          >
            + {{ envEntries.length - envPreview.length }} more
          </button>
        </div>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.content-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 18px;
  align-items: start;
}
.main-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.side-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-self: start;
  position: sticky;
  top: 16px;
}
@media (max-width: 1100px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
  .side-col {
    position: static;
  }
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
  gap: 12px;
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
.panel-body {
  padding: 14px 16px;
}

/* KV list (Configuration aside) */
.kv {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.kv-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  padding-bottom: 9px;
  border-bottom: 1px solid var(--color-border);
}
.kv-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}
.k {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.v {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-primary);
  text-align: right;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mono {
  font-family: var(--font-mono);
}

/* Health checks aside */
.hc {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.hc-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}
.hc-name {
  color: var(--color-text-primary);
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.hc-path {
  color: var(--color-text-tertiary);
  font-size: 11px;
}
.hc-lat {
  color: var(--color-text-tertiary);
  font-size: 11px;
  margin-left: 2px;
}
.probe-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.probe-dot-ok {
  background: var(--color-status-ready, #16a34a);
}
.probe-dot-fail {
  background: var(--color-status-error, #dc2626);
}
.probe-dot-idle {
  background: var(--color-border, #d4d4d8);
}

/* Metric strip */
.metrics-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  overflow: hidden;
}
.metric-cell {
  padding: 16px 18px;
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.metric-cell:last-child {
  border-right: none;
}
.metric-l {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.metric-v {
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: var(--color-text-primary);
  min-width: 0;
}
.metric-v-mono {
  font-family: var(--font-mono);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.metric-u {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  font-weight: 400;
}
.metric-delta {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
}
@media (max-width: 900px) {
  .metrics-strip {
    grid-template-columns: repeat(2, 1fr);
  }
  .metric-cell:nth-child(2) {
    border-right: none;
  }
  .metric-cell:nth-child(-n + 2) {
    border-bottom: 1px solid var(--color-border);
  }
}

/* Environment preview aside */
.env-preview {
  display: flex;
  flex-direction: column;
}
.env-preview-row {
  display: grid;
  grid-template-columns: minmax(110px, 1fr) 1.5fr;
  gap: 10px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--color-border);
  font-size: 11px;
  align-items: center;
}
.env-preview-row:last-of-type {
  border-bottom: none;
}
.env-preview-k {
  color: var(--color-text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.env-preview-v {
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.env-preview-more {
  background: transparent;
  border: none;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  cursor: pointer;
  padding: 10px 16px;
  text-align: left;
  border-top: 1px solid var(--color-border);
}
.env-preview-more:hover {
  text-decoration: underline;
}
</style>
