<script setup lang="ts">
/**
 * Resource detail page — orchestrator. Owns the resource + deployments fetch
 * loop and the cross-tab action state (pause/resume/restart pending). Tab
 * content is delegated to per-tab components under `./resource-tabs/`.
 */
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import { useActiveEnvironmentStore } from '@/stores/activeEnvironment'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import ResourceHeader from '@/components/resource/ResourceHeader.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'
import { extractApiErrorMessage } from '@/utils/apiError'
import { usePageTitle } from '@/composables/usePageTitle'
import ResourceOverviewTab from './resource-tabs/ResourceOverviewTab.vue'
import ResourceDeploysTab from './resource-tabs/ResourceDeploysTab.vue'
import ResourceEnvTab from './resource-tabs/ResourceEnvTab.vue'
import ResourceDomainsTab from './resource-tabs/ResourceDomainsTab.vue'
import ResourceMetricsTab from './resource-tabs/ResourceMetricsTab.vue'
import ResourceSettingsTab from './resource-tabs/ResourceSettingsTab.vue'

type Resource = components['schemas']['Resource']
type Deployment = components['schemas']['Deployment']
type Tab = 'overview' | 'deploys' | 'environment' | 'domains' | 'metrics' | 'settings'
type PendingActionKind = 'pause' | 'resume' | 'restart'

const TABS: ReadonlyArray<{ id: Tab; label: string }> = [
  { id: 'overview', label: 'Overview' },
  { id: 'deploys', label: 'Deploys' },
  { id: 'environment', label: 'Environment' },
  { id: 'domains', label: 'Domains' },
  { id: 'metrics', label: 'Metrics' },
  { id: 'settings', label: 'Settings' },
]

const route = useRoute()
const router = useRouter()
const activeEnvStore = useActiveEnvironmentStore()
const wsSlug = computed(() => route.params.wsSlug as string)
const projSlug = computed(() => route.params.projSlug as string)
const envSlug = computed(() => route.params.envSlug as string)
const resSlug = computed(() => route.params.resSlug as string)
const { setPageTitle } = usePageTitle()

const resource = ref<Resource | null>(null)
const deployments = ref<Deployment[]>([])
const loading = ref(true)
const error = ref('')
const tab = ref<Tab>(readInitialTab())
const pendingDeploymentId = ref<string | null>(null)
let pollInterval: ReturnType<typeof setInterval> | null = null

// Cross-tab pending-action lifecycle (Pause / Resume / Restart). Spans from
// click → operator pickup → 60s timeout. Drives the page header's Restart
// button AND the danger-zone buttons inside the Settings tab.
interface PendingState {
  action: PendingActionKind
  baselineStatus: string | undefined
  startedAt: number
  hasLeftBaseline: boolean
}
const pendingState = ref<PendingState | null>(null)
const PENDING_TIMEOUT_MS = 60_000
const pendingAction = computed(() => pendingState.value?.action ?? null)
const effectiveStopped = computed(() => {
  if (pendingState.value?.action === 'pause') return true
  if (pendingState.value?.action === 'resume') return false
  return resource.value?.status === 'Stopped'
})

function readInitialTab(): Tab {
  const q = route.query.tab
  if (typeof q === 'string' && TABS.some((t) => t.id === q)) return q as Tab
  return 'overview'
}

watch(tab, (next) => {
  if (route.query.tab === next) return
  void router.replace({ query: { ...route.query, tab: next } })
})

watch(
  () => route.query.tab,
  (q) => {
    if (typeof q === 'string' && TABS.some((t) => t.id === q) && tab.value !== q) {
      tab.value = q as Tab
    }
  },
)

// Counts shown next to tab labels.
const envEntries = computed(() => Object.entries(resource.value?.config?.env ?? {}))
const ingressPorts = computed(
  () => resource.value?.config?.networking?.ports?.filter((p) => p.expose === 'Ingress') ?? [],
)
const customDomains = computed(() => resource.value?.statusDetail?.customDomains ?? [])

async function fetchResource() {
  try {
    const { data } = await api.GET('/resources/{slug}', {
      params: { path: { slug: resSlug.value } },
    })
    if (data) {
      resource.value = data
      setPageTitle(data.name ?? resSlug.value)
    }
  } catch {
    error.value = 'Failed to load resource'
  }
}

async function fetchDeployments() {
  try {
    const { data } = await api.GET('/resources/{slug}/deployments', {
      params: {
        path: { slug: resSlug.value },
        query: { page: 0, size: 20 },
      },
    })
    if (data?.content) deployments.value = data.content
  } catch {
    error.value = 'Failed to load deployments'
  }
}

async function fetchEnvironment() {
  try {
    const { data } = await api.GET('/environments/{slug}', {
      params: { path: { slug: envSlug.value } },
    })
    if (data) activeEnvStore.set(data)
  } catch {
    // Non-fatal: footer degrades gracefully without the env context
  }
}

async function fetchAll() {
  loading.value = true
  await Promise.all([fetchResource(), fetchDeployments()])
  loading.value = false
}

// ── Pause / Resume / Restart ────────────────────────────────────────────
function beginPending(action: PendingActionKind) {
  pendingState.value = {
    action,
    baselineStatus: resource.value?.status,
    startedAt: Date.now(),
    hasLeftBaseline: false,
  }
}
function clearPending() {
  pendingState.value = null
}

async function restartResource() {
  beginPending('restart')
  try {
    const { error: apiError } = await api.POST('/resources/{slug}/restart', {
      params: { path: { slug: resSlug.value } },
    })
    if (apiError) {
      error.value = extractApiErrorMessage(apiError, 'Failed to restart resource')
      clearPending()
      return
    }
    await fetchResource()
  } catch {
    error.value = 'Failed to restart resource'
    clearPending()
  }
}
async function pauseResource() {
  beginPending('pause')
  try {
    const { error: apiError } = await api.POST('/resources/{slug}/stop', {
      params: { path: { slug: resSlug.value } },
    })
    if (apiError) {
      error.value = extractApiErrorMessage(apiError, 'Failed to pause resource')
      clearPending()
      return
    }
    await fetchResource()
  } catch {
    error.value = 'Failed to pause resource'
    clearPending()
  }
}
async function resumeResource() {
  beginPending('resume')
  try {
    const { error: apiError } = await api.POST('/resources/{slug}/start', {
      params: { path: { slug: resSlug.value } },
    })
    if (apiError) {
      error.value = extractApiErrorMessage(apiError, 'Failed to resume resource')
      clearPending()
      return
    }
    await fetchResource()
  } catch {
    error.value = 'Failed to resume resource'
    clearPending()
  }
}
// ── Deployment-row actions ──────────────────────────────────────────────
async function cancelDeployment(deploymentId: string): Promise<void> {
  pendingDeploymentId.value = deploymentId
  try {
    const { error: apiError } = await api.POST(
      '/resources/{slug}/deployments/{deployment_id}/cancel',
      {
        params: { path: { slug: resSlug.value, deployment_id: deploymentId } },
      },
    )
    if (apiError) {
      error.value = extractApiErrorMessage(apiError, 'Failed to cancel deployment')
      return
    }
    await fetchDeployments()
  } catch {
    error.value = 'Failed to cancel deployment'
  } finally {
    pendingDeploymentId.value = null
  }
}
async function retryDeployment(deploymentId: string): Promise<void> {
  pendingDeploymentId.value = deploymentId
  try {
    const { error: apiError } = await api.POST(
      '/resources/{slug}/deployments/{deployment_id}/retry',
      {
        params: { path: { slug: resSlug.value, deployment_id: deploymentId } },
      },
    )
    if (apiError) {
      error.value = extractApiErrorMessage(apiError, 'Failed to retry deployment')
      return
    }
    await fetchDeployments()
  } catch {
    error.value = 'Failed to retry deployment'
  } finally {
    pendingDeploymentId.value = null
  }
}

// Throws on failure so the ConfirmButton that triggered it can re-arm.
// Two ConfirmButtons (header + danger-zone) share this single handler;
// rejecting lets each instance reset itself.
async function deleteResource(): Promise<void> {
  try {
    const { error: apiError } = await api.DELETE('/resources/{slug}', {
      params: { path: { slug: resSlug.value } },
    })
    if (apiError) {
      const msg = extractApiErrorMessage(apiError, 'Failed to delete resource')
      error.value = msg
      throw new Error(msg)
    }
    void router.push({
      name: 'environment',
      params: { wsSlug: wsSlug.value, projSlug: projSlug.value, envSlug: envSlug.value },
    })
  } catch (e) {
    if (!error.value) error.value = 'Failed to delete resource'
    throw e
  }
}

// Clear the pending state once the operator's observed status converges.
// `restart` waits for the full round-trip (baseline → off-baseline → back),
// matching the backend's "must be READY to restart" precondition.
watch(
  () => resource.value?.status,
  (s) => {
    const ps = pendingState.value
    if (!ps || !s) return
    if (s !== ps.baselineStatus) ps.hasLeftBaseline = true
    if (ps.action === 'pause' && s === 'Stopped') {
      clearPending()
    } else if (ps.action === 'resume' && s !== 'Stopped' && s !== ps.baselineStatus) {
      clearPending()
    } else if (ps.action === 'restart' && ps.hasLeftBaseline && s === ps.baselineStatus) {
      clearPending()
    }
  },
)

// 60s timeout fallback for stalled operators.
let pendingTimer: ReturnType<typeof setTimeout> | null = null
watch(pendingState, (ps) => {
  if (pendingTimer) {
    clearTimeout(pendingTimer)
    pendingTimer = null
  }
  if (ps) {
    const startedAt = ps.startedAt
    pendingTimer = setTimeout(() => {
      if (pendingState.value?.startedAt === startedAt) clearPending()
    }, PENDING_TIMEOUT_MS)
  }
})

// ── Lifecycle ────────────────────────────────────────────────────────────
watch(resSlug, () => {
  error.value = ''
  resource.value = null
  deployments.value = []
  fetchAll()
})

function startPolling() {
  stopPolling()
  pollInterval = setInterval(() => {
    fetchResource()
    fetchDeployments()
  }, 30000)
}
function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}
function handleVisibilityChange() {
  if (document.hidden) {
    stopPolling()
  } else {
    fetchResource()
    fetchDeployments()
    startPolling()
  }
}

onMounted(() => {
  fetchAll()
  fetchEnvironment()
  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (pendingTimer) clearTimeout(pendingTimer)
  activeEnvStore.clear()
})
</script>

<template>
  <div class="resource-detail">
    <AppBreadcrumb :items="buildBreadcrumbChain({ wsSlug, projSlug, envSlug }, resSlug, true)" />

    <ResourceHeader
      v-if="resource"
      :resource="resource"
      :pending-action="pendingAction"
      :on-delete="deleteResource"
      @restart="restartResource"
    />

    <div v-if="error" class="error-banner">{{ error }}</div>
    <div v-if="loading" class="loading">Loading...</div>

    <template v-else-if="resource">
      <nav class="tabs" role="tablist" aria-label="Resource section">
        <button
          v-for="t in TABS"
          :key="t.id"
          type="button"
          class="tab-btn"
          :class="{ on: tab === t.id }"
          role="tab"
          :aria-selected="tab === t.id"
          @click="tab = t.id"
        >
          {{ t.label }}
          <span v-if="t.id === 'deploys' && deployments.length" class="tab-ct">
            {{ deployments.length }}
          </span>
          <span v-if="t.id === 'environment' && envEntries.length" class="tab-ct">
            {{ envEntries.length }}
          </span>
          <span
            v-if="t.id === 'domains' && customDomains.length + ingressPorts.length"
            class="tab-ct"
          >
            {{ customDomains.length + ingressPorts.length }}
          </span>
        </button>
      </nav>

      <ResourceOverviewTab
        v-if="tab === 'overview'"
        :resource="resource"
        :deployments="deployments"
        @navigate-deploys="tab = 'deploys'"
        @navigate-environment="tab = 'environment'"
      />

      <ResourceDeploysTab
        v-else-if="tab === 'deploys'"
        :deployments="deployments"
        :pending-deployment-id="pendingDeploymentId"
        @cancel="cancelDeployment"
        @retry="retryDeployment"
      />

      <ResourceEnvTab
        v-else-if="tab === 'environment'"
        :resource="resource"
        @navigate-settings="tab = 'settings'"
      />

      <ResourceDomainsTab v-else-if="tab === 'domains'" :resource="resource" />

      <ResourceMetricsTab v-else-if="tab === 'metrics'" :resource="resource" />

      <ResourceSettingsTab
        v-else-if="tab === 'settings'"
        :resource="resource"
        :pending-action="pendingAction"
        :effective-stopped="effectiveStopped"
        :on-delete="deleteResource"
        @saved="fetchResource"
        @restart="restartResource"
        @pause="pauseResource"
        @resume="resumeResource"
      />
    </template>
  </div>
</template>

<style scoped>
.resource-detail {
  display: contents;
}

.tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--color-border);
  margin: 14px 0 22px;
  overflow-x: auto;
}
.tab-btn {
  background: transparent;
  border: none;
  padding: 12px 16px 10px;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-tertiary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  letter-spacing: -0.005em;
}
.tab-btn:hover {
  color: var(--color-text-secondary);
}
.tab-btn.on {
  color: var(--color-text-primary);
  border-bottom-color: var(--color-accent);
}
.tab-ct {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  background: var(--color-bg-surface);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  letter-spacing: 0.06em;
}
.tab-btn.on .tab-ct {
  color: var(--color-accent);
  background: oklch(20% 0.04 80);
}
</style>
