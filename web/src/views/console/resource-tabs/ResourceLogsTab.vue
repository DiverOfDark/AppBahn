<script setup lang="ts">
/**
 * Logs tab — two modes selected by a sub-tab:
 *   • Live: opens an `EventSource` against `GET /resources/{slug}/logs/stream` and renders
 *     incoming `log` frames (monospace on a dark canvas) interleaved with `k8s_event` frames
 *     (amber-bordered system messages carrying a reason/type badge).
 *   • Build: fetches `GET /resources/{slug}/logs?deploymentId=…` for a chosen deployment and
 *     renders the captured build output as static monospace lines.
 * Both modes degrade gracefully when no log provider is configured — the backend answers with a
 * `message` (live: an `error`-named SSE frame; build: `LogResponse.message`) and we show an
 * EmptyState instead of an empty console.
 */
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { api, getAccessToken } from '@/api/client'
import type { components } from '@/api/schema'
import EmptyState from '@/components/EmptyState.vue'

type Resource = components['schemas']['Resource']
type Deployment = components['schemas']['Deployment']

const props = defineProps<{ resource: Resource; deployments?: Deployment[] }>()

// ── SSE frame shapes (delivered as EventSource data, not typed in the OpenAPI schema) ─────────
interface LogFrame {
  timestamp?: string
  message?: string
  pod?: string
  container?: string
}
interface EventFrame {
  eventType?: string
  reason?: string
  message?: string
  involvedKind?: string
  involvedName?: string
  pod?: string
  count?: number
  eventTime?: string
}
interface RenderedLine {
  key: number
  kind: 'log' | 'event'
  timestamp?: string
  message: string
  pod?: string
  container?: string
  reason?: string
  eventType?: string
}

const MAX_LINES = 2000
const slug = computed(() => props.resource.slug ?? '')

// ── Mode ─────────────────────────────────────────────────────────────────────
type Mode = 'live' | 'build'
const mode = ref<Mode>('live')

// ── Live stream state ────────────────────────────────────────────────────────
const liveLines = ref<RenderedLine[]>([])
const liveMessage = ref('') // graceful "logs not available"
const connected = ref(false)
const consoleEl = ref<HTMLElement | null>(null)
let source: EventSource | null = null
let lineSeq = 0

function pushLine(line: Omit<RenderedLine, 'key'>) {
  liveLines.value.push({ ...line, key: lineSeq++ })
  if (liveLines.value.length > MAX_LINES) {
    liveLines.value.splice(0, liveLines.value.length - MAX_LINES)
  }
  void scrollToBottom()
}

async function scrollToBottom() {
  await nextTick()
  const el = consoleEl.value
  if (el) el.scrollTop = el.scrollHeight
}

function openStream() {
  closeStream()
  if (!slug.value) return
  liveLines.value = []
  liveMessage.value = ''
  // EventSource cannot set an Authorization header, so the bearer token rides as a query
  // parameter; the server resolves it for the SSE handshake.
  const token = getAccessToken()
  const qs = new URLSearchParams({ types: 'log,k8s_event' })
  if (token) qs.set('access_token', token)
  const url = `/api/v1/resources/${encodeURIComponent(slug.value)}/logs/stream?${qs.toString()}`
  source = new EventSource(url)

  source.addEventListener('open', () => {
    connected.value = true
  })
  source.addEventListener('log', (e) => {
    const frame = parse<LogFrame>((e as MessageEvent).data)
    if (!frame) return
    pushLine({
      kind: 'log',
      timestamp: frame.timestamp,
      message: frame.message ?? '',
      pod: frame.pod,
      container: frame.container,
    })
  })
  source.addEventListener('k8s_event', (e) => {
    const frame = parse<EventFrame>((e as MessageEvent).data)
    if (!frame) return
    pushLine({
      kind: 'event',
      timestamp: frame.eventTime,
      message: frame.message ?? '',
      pod: frame.pod ?? frame.involvedName,
      reason: frame.reason,
      eventType: frame.eventType,
    })
  })
  // The backend signals graceful degradation with an `error`-named frame carrying a message.
  source.addEventListener('error', (e) => {
    connected.value = false
    const frame = parse<{ message?: string }>((e as MessageEvent).data)
    if (frame?.message) {
      liveMessage.value = frame.message
      closeStream()
    } else if (liveLines.value.length === 0 && !liveMessage.value) {
      liveMessage.value = 'Logs not available — could not connect to the log stream.'
    }
  })
}

function closeStream() {
  if (source) {
    source.close()
    source = null
  }
  connected.value = false
}

function parse<T>(data: unknown): T | null {
  if (typeof data !== 'string' || data.length === 0) return null
  try {
    return JSON.parse(data) as T
  } catch {
    return null
  }
}

// ── Build log state ──────────────────────────────────────────────────────────
const buildEligible = computed(() => (props.deployments ?? []).filter((d) => d.id))
const selectedDeploymentId = ref<string>('')
const buildLines = ref<RenderedLine[]>([])
const buildMessage = ref('')
const buildLoading = ref(false)

watch(
  buildEligible,
  (list) => {
    if (!selectedDeploymentId.value && list.length) selectedDeploymentId.value = list[0]!.id!
  },
  { immediate: true },
)

async function fetchBuildLogs() {
  const id = selectedDeploymentId.value
  if (!slug.value || !id) return
  buildLoading.value = true
  buildMessage.value = ''
  buildLines.value = []
  try {
    const res = await api.GET('/resources/{slug}/logs', {
      params: { path: { slug: slug.value }, query: { deploymentId: id } },
    })
    const data = res.data
    if (data?.message) {
      buildMessage.value = data.message
    } else {
      buildLines.value = (data?.lines ?? []).map((l, i) => ({
        key: i,
        kind: 'log',
        timestamp: l.timestamp,
        message: l.message ?? '',
        pod: l.pod,
        container: l.container,
      }))
      if (buildLines.value.length === 0) {
        buildMessage.value = 'No build output for this deployment.'
      }
    }
  } catch {
    buildMessage.value = 'Logs not available — failed to load build output.'
  } finally {
    buildLoading.value = false
  }
}

watch([mode, selectedDeploymentId], () => {
  if (mode.value === 'build') fetchBuildLogs()
})

function formatClock(ts?: string): string {
  if (!ts) return ''
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function shortId(id?: string): string {
  return id ? id.slice(0, 8) : ''
}

onMounted(() => {
  if (mode.value === 'live') openStream()
})
onUnmounted(closeStream)

watch(mode, (next) => {
  if (next === 'live') openStream()
  else closeStream()
})
</script>

<template>
  <div class="logs-tab">
    <div class="controls">
      <div class="mode-group" role="group" aria-label="Log mode">
        <button
          type="button"
          class="mode-btn"
          :class="{ on: mode === 'live' }"
          @click="mode = 'live'"
        >
          Live
        </button>
        <button
          type="button"
          class="mode-btn"
          :class="{ on: mode === 'build' }"
          @click="mode = 'build'"
        >
          Build
        </button>
      </div>

      <div v-if="mode === 'live'" class="status" :class="{ live: connected }">
        <span class="dot" aria-hidden="true" />
        {{ connected ? 'Streaming' : 'Disconnected' }}
      </div>

      <label v-else-if="buildEligible.length" class="deploy-filter">
        <span class="deploy-label">Deployment</span>
        <select
          v-model="selectedDeploymentId"
          class="deploy-select"
          aria-label="Filter build logs by deployment"
        >
          <option v-for="d in buildEligible" :key="d.id" :value="d.id">
            {{ shortId(d.id) }} · {{ d.lifecycle }}
          </option>
        </select>
      </label>
    </div>

    <!-- ── Live ─────────────────────────────────────────────────────────── -->
    <template v-if="mode === 'live'">
      <EmptyState v-if="liveMessage" :message="liveMessage" />
      <div v-else ref="consoleEl" class="console" role="log" aria-live="polite">
        <p v-if="liveLines.length === 0" class="waiting">Waiting for log output…</p>
        <template v-for="line in liveLines" :key="line.key">
          <div v-if="line.kind === 'event'" class="event-line">
            <span class="event-badge" :class="(line.eventType || '').toLowerCase()">
              {{ line.eventType || 'Event' }}
            </span>
            <span v-if="line.reason" class="event-reason">{{ line.reason }}</span>
            <span class="event-msg">{{ line.message }}</span>
            <span v-if="line.pod" class="event-pod">{{ line.pod }}</span>
          </div>
          <div v-else class="log-line">
            <span class="ts">{{ formatClock(line.timestamp) }}</span>
            <span v-if="line.pod" class="pod">{{ line.pod }}</span>
            <span class="msg">{{ line.message }}</span>
          </div>
        </template>
      </div>
    </template>

    <!-- ── Build ────────────────────────────────────────────────────────── -->
    <template v-else>
      <EmptyState
        v-if="!buildEligible.length"
        message="No deployments yet — build logs appear after the first deploy."
      />
      <div v-else-if="buildLoading" class="waiting">Loading build logs…</div>
      <EmptyState v-else-if="buildMessage" :message="buildMessage" />
      <div v-else class="console" role="log">
        <div v-for="line in buildLines" :key="line.key" class="log-line">
          <span class="ts">{{ formatClock(line.timestamp) }}</span>
          <span class="msg">{{ line.message }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.logs-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.mode-group {
  display: inline-flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.mode-btn {
  background: transparent;
  border: none;
  border-right: 1px solid var(--color-border);
  padding: 6px 16px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
  cursor: pointer;
}
.mode-btn:last-child {
  border-right: none;
}
.mode-btn:hover {
  color: var(--color-text-secondary);
}
.mode-btn.on {
  color: var(--color-accent);
  background: var(--color-bg-surface);
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--color-text-tertiary);
}
.status .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-text-tertiary);
}
.status.live .dot {
  background: var(--color-accent);
}

.deploy-filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.deploy-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.deploy-select {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-primary);
  font-size: 12px;
  font-family: var(--font-mono);
  padding: 6px 10px;
}

.console {
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  height: 480px;
  overflow-y: auto;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
}

.waiting {
  color: var(--color-text-tertiary);
  font-size: 12px;
  font-family: var(--font-mono);
  padding: 8px 0;
  margin: 0;
}

.log-line {
  display: flex;
  gap: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-secondary);
}
.log-line .ts {
  color: var(--color-text-tertiary);
  flex: 0 0 auto;
}
.log-line .pod {
  color: var(--color-text-tertiary);
  flex: 0 0 auto;
}
.log-line .msg {
  color: var(--color-text-primary);
}

.event-line {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin: 4px 0;
  padding: 6px 10px;
  border-left: 2px solid var(--color-accent);
  background: color-mix(in oklch, var(--color-accent) 8%, transparent);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
.event-badge {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
  flex: 0 0 auto;
}
.event-badge.warning {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.event-reason {
  font-weight: 600;
  color: var(--color-text-primary);
}
.event-msg {
  color: var(--color-text-secondary);
}
.event-pod {
  color: var(--color-text-tertiary);
  margin-left: auto;
}
</style>
