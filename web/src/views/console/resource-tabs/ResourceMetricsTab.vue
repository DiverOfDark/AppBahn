<script setup lang="ts">
/**
 * Metrics tab — CPU / RAM / network time-series for a resource, one chart per
 * metric with a line per pod. A shared time-range selector recomputes the query
 * window + step; an optional pod filter narrows every chart to a single pod.
 * Limit-threshold overlays (amber) are drawn where the resource declares a
 * CPU/memory limit. When the metrics provider is unconfigured the backend
 * returns a `message` and no series — rendered as a graceful empty state.
 */
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  Title,
  Tooltip,
  Legend,
  LineElement,
  LinearScale,
  PointElement,
  Filler,
  type ChartOptions,
  type ChartData,
} from 'chart.js'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import EmptyState from '@/components/EmptyState.vue'
import { parseMillicores, parseMebibytes } from '@/utils/resource'

ChartJS.register(Title, Tooltip, Legend, LineElement, LinearScale, PointElement, Filler)

type Resource = components['schemas']['Resource']
type MetricsResponse = components['schemas']['MetricsResponse']

const props = defineProps<{ resource: Resource }>()

// ── Time ranges ────────────────────────────────────────────────────────────
// Each range pairs a lookback window with a sensible Prometheus step so the
// chart stays readable (≈60–120 points) regardless of zoom.
interface RangeOption {
  id: string
  label: string
  windowMs: number
  stepSeconds: number
}
const RANGES: ReadonlyArray<RangeOption> = [
  { id: '15m', label: '15m', windowMs: 15 * 60_000, stepSeconds: 15 },
  { id: '1h', label: '1h', windowMs: 60 * 60_000, stepSeconds: 60 },
  { id: '6h', label: '6h', windowMs: 6 * 60 * 60_000, stepSeconds: 300 },
  { id: '24h', label: '24h', windowMs: 24 * 60 * 60_000, stepSeconds: 1200 },
]
const selectedRange = ref<RangeOption>(RANGES[1]!)

// ── Pod filter ─────────────────────────────────────────────────────────────
const selectedPod = ref<string>('')
const knownPods = computed(() => {
  const names = new Set<string>()
  for (const r of [cpu.value, ram.value, netIn.value, netOut.value]) {
    for (const s of r?.series ?? []) {
      if (s.pod) names.add(s.pod)
    }
  }
  return [...names].sort()
})

// ── Metric series state ──────────────────────────────────────────────────────
const cpu = ref<MetricsResponse | null>(null)
const ram = ref<MetricsResponse | null>(null)
const netIn = ref<MetricsResponse | null>(null)
const netOut = ref<MetricsResponse | null>(null)
const loading = ref(true)
const error = ref('')
let pollInterval: ReturnType<typeof setInterval> | null = null

const slug = computed(() => props.resource.slug ?? '')

// Provider unconfigured ⇒ every endpoint returns a message and no series.
const providerMessage = computed(() => {
  const responses = [cpu.value, ram.value, netIn.value, netOut.value]
  const anySeries = responses.some((r) => (r?.series?.length ?? 0) > 0)
  if (anySeries) return ''
  const withMessage = responses.find((r) => r?.message)
  return withMessage?.message ?? ''
})

// ── Limit thresholds (resource-declared CPU / memory limits) ──────────────────
// CPU metrics arrive in millicores; memory metrics in bytes. The mebibyte
// limit is scaled to bytes so the overlay lines up with the series.
const cpuLimitMillicores = computed(
  () => parseMillicores(props.resource.config?.hosting?.cpu) ?? null,
)
const memLimitBytes = computed(() => {
  const mib = parseMebibytes(props.resource.config?.hosting?.memory)
  return mib != null ? mib * 1024 * 1024 : null
})

// ── Palette: deterministic per-pod colour so a pod keeps its colour across charts.
const POD_HUES = [200, 280, 140, 30, 330, 100, 250, 60]
function podColor(pod: string, index: number): string {
  const hue = POD_HUES[index % POD_HUES.length] ?? 200
  void pod
  return `oklch(70% 0.14 ${hue})`
}

function buildChartData(resp: MetricsResponse | null, limit: number | null): ChartData<'line'> {
  const series = resp?.series ?? []
  const datasets = series.map((s, i) => ({
    label: s.pod ?? `series ${i + 1}`,
    data: (s.values ?? []).map((p) => ({
      x: (p.timestamp ?? 0) * 1000,
      y: p.value ?? 0,
    })),
    borderColor: podColor(s.pod ?? '', i),
    backgroundColor: podColor(s.pod ?? '', i),
    borderWidth: 1.5,
    pointRadius: 0,
    tension: 0.25,
    fill: false,
  }))

  if (limit != null && limit > 0 && series.length > 0) {
    // Draw the threshold as a flat amber line spanning the queried window.
    const first = series[0]?.values ?? []
    const xs = first.length
      ? [first[0]!.timestamp ?? 0, first[first.length - 1]!.timestamp ?? 0]
      : []
    datasets.push({
      label: 'Limit',
      data: xs.map((t) => ({ x: t * 1000, y: limit })),
      borderColor: 'var(--color-accent)',
      backgroundColor: 'var(--color-accent)',
      borderWidth: 1.5,
      pointRadius: 0,
      tension: 0,
      fill: false,
      // @ts-expect-error chart.js accepts borderDash at runtime on line datasets
      borderDash: [6, 4],
    })
  }
  return { datasets }
}

function formatClock(epochMs: number): string {
  return new Date(epochMs).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  })
}

function chartOptions(unit: string): ChartOptions<'line'> {
  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    parsing: false,
    normalized: true,
    scales: {
      x: {
        type: 'linear',
        grid: { color: 'var(--color-border)' },
        ticks: {
          color: 'var(--color-text-tertiary)',
          maxRotation: 0,
          autoSkip: true,
          callback: (v) => formatClock(Number(v)),
        },
      },
      y: {
        beginAtZero: true,
        grid: { color: 'var(--color-border)' },
        ticks: {
          color: 'var(--color-text-tertiary)',
          callback: (v) => `${v} ${unit}`,
        },
      },
    },
    plugins: {
      legend: {
        position: 'bottom',
        labels: { color: 'var(--color-text-secondary)', boxWidth: 12, font: { size: 11 } },
      },
      tooltip: {
        enabled: true,
        callbacks: {
          title: (items) => (items.length ? formatClock(Number(items[0]!.parsed.x)) : ''),
        },
      },
    },
  }
}

const cpuData = computed(() => buildChartData(cpu.value, cpuLimitMillicores.value))
const ramData = computed(() => buildChartData(ram.value, memLimitBytes.value))
const netInData = computed(() => buildChartData(netIn.value, null))
const netOutData = computed(() => buildChartData(netOut.value, null))

const cpuOptions = chartOptions('m')
const ramOptions = chartOptions('B')
const netOptions = chartOptions('B/s')

async function fetchMetrics() {
  if (!slug.value) return
  const end = new Date()
  const start = new Date(end.getTime() - selectedRange.value.windowMs)
  const query = {
    start: start.toISOString(),
    end: end.toISOString(),
    step: selectedRange.value.stepSeconds,
    ...(selectedPod.value ? { pod: selectedPod.value } : {}),
  }
  const path = { slug: slug.value }
  try {
    const [c, r, ni, no] = await Promise.all([
      api.GET('/resources/{slug}/metrics/cpu', { params: { path, query } }),
      api.GET('/resources/{slug}/metrics/ram', { params: { path, query } }),
      api.GET('/resources/{slug}/metrics/network/inbound', { params: { path, query } }),
      api.GET('/resources/{slug}/metrics/network/outbound', { params: { path, query } }),
    ])
    cpu.value = c.data ?? null
    ram.value = r.data ?? null
    netIn.value = ni.data ?? null
    netOut.value = no.data ?? null
    error.value = ''
  } catch {
    error.value = 'Failed to load metrics'
  } finally {
    loading.value = false
  }
}

async function reload() {
  loading.value = true
  await fetchMetrics()
}

watch([selectedRange, selectedPod], reload)

function startPolling() {
  stopPolling()
  pollInterval = setInterval(fetchMetrics, 30_000)
}
function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

onMounted(() => {
  fetchMetrics()
  startPolling()
})
onUnmounted(stopPolling)
</script>

<template>
  <div class="metrics-tab">
    <div class="controls">
      <div class="range-group" role="group" aria-label="Time range">
        <button
          v-for="r in RANGES"
          :key="r.id"
          type="button"
          class="range-btn"
          :class="{ on: selectedRange.id === r.id }"
          @click="selectedRange = r"
        >
          {{ r.label }}
        </button>
      </div>

      <label v-if="knownPods.length > 1" class="pod-filter">
        <span class="pod-label">Pod</span>
        <select v-model="selectedPod" class="pod-select" aria-label="Filter by pod">
          <option value="">All pods</option>
          <option v-for="p in knownPods" :key="p" :value="p">{{ p }}</option>
        </select>
      </label>
    </div>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <div v-if="loading && !cpu" class="loading">Loading metrics…</div>

    <EmptyState
      v-else-if="providerMessage"
      :message="providerMessage || 'Metrics are not available for this resource.'"
    />

    <div v-else class="charts">
      <section class="chart-card">
        <h3 class="chart-title">CPU <span class="chart-unit">millicores</span></h3>
        <div class="chart-canvas">
          <Line :data="cpuData" :options="cpuOptions" />
        </div>
      </section>

      <section class="chart-card">
        <h3 class="chart-title">Memory <span class="chart-unit">bytes</span></h3>
        <div class="chart-canvas">
          <Line :data="ramData" :options="ramOptions" />
        </div>
      </section>

      <section class="chart-card">
        <h3 class="chart-title">Network in <span class="chart-unit">bytes/s</span></h3>
        <div class="chart-canvas">
          <Line :data="netInData" :options="netOptions" />
        </div>
      </section>

      <section class="chart-card">
        <h3 class="chart-title">Network out <span class="chart-unit">bytes/s</span></h3>
        <div class="chart-canvas">
          <Line :data="netOutData" :options="netOptions" />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.metrics-tab {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.range-group {
  display: inline-flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.range-btn {
  background: transparent;
  border: none;
  border-right: 1px solid var(--color-border);
  padding: 6px 12px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
  cursor: pointer;
}
.range-btn:last-child {
  border-right: none;
}
.range-btn:hover {
  color: var(--color-text-secondary);
}
.range-btn.on {
  color: var(--color-accent);
  background: var(--color-bg-surface);
}

.pod-filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.pod-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.pod-select {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-primary);
  font-size: 12px;
  padding: 6px 10px;
}

.charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
@media (max-width: 880px) {
  .charts {
    grid-template-columns: minmax(0, 1fr);
  }
}

.chart-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  padding: 16px;
}
.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 12px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.chart-unit {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 400;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
}
.chart-canvas {
  position: relative;
  height: 220px;
}

.loading {
  color: var(--color-text-tertiary);
  font-size: 13px;
  padding: 32px 0;
}
.error-banner {
  background: var(--color-bg-error);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  font-size: 13px;
  color: var(--color-text-primary);
}
</style>
