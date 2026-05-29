<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import { useActiveEnvironmentStore } from '@/stores/activeEnvironment'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import ConfirmButton from '@/components/ConfirmButton.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'
import {
  statusClass,
  aggregateStatusClass,
  getDomain,
} from '@/composables/resource/useResourceHelpers'
import { initials } from '@/utils/resource'
import { formatDate } from '@/utils/format'
import { extractApiErrorMessage } from '@/utils/apiError'
import { usePageTitle } from '@/composables/usePageTitle'
import { useSidebarRefresh } from '@/composables/useSidebarRefresh'

type Environment = components['schemas']['Environment']
type Resource = components['schemas']['Resource']
type EnvironmentToken = components['schemas']['EnvironmentToken']

const namespacePrefix = ref('abp')

const TOKEN_ROLES = ['EDITOR', 'VIEWER'] as const
type TokenRole = (typeof TOKEN_ROLES)[number]

const route = useRoute()
const router = useRouter()
const activeEnvStore = useActiveEnvironmentStore()

const environment = ref<Environment | null>(null)
const projectEnvs = ref<Environment[]>([])
const resources = ref<Resource[]>([])
const loading = ref(true)
const error = ref('')
let pollInterval: ReturnType<typeof setInterval> | null = null

const { setPageTitle } = usePageTitle()
const { refreshSidebar } = useSidebarRefresh()
const wsSlug = computed(() => route.params.wsSlug as string)
const projSlug = computed(() => route.params.projSlug as string)
const envSlug = computed(() => route.params.envSlug as string)

const tokens = ref<EnvironmentToken[]>([])
const showCreateToken = ref(false)
const createTokenLoading = ref(false)
const createTokenError = ref('')
const newTokenName = ref('')
const newTokenRole = ref<TokenRole>('VIEWER')
const newTokenExpiresDays = ref<number | undefined>(90)
const createdToken = ref<string | null>(null)

function openCreateToken() {
  createTokenError.value = ''
  newTokenName.value = ''
  newTokenRole.value = 'VIEWER'
  newTokenExpiresDays.value = 90
  showCreateToken.value = true
}

const namespace = computed(() =>
  environment.value?.slug ? `${namespacePrefix.value}-${environment.value.slug}` : '',
)

async function fetchEnvironment() {
  try {
    const { data } = await api.GET('/environments/{slug}', {
      params: { path: { slug: envSlug.value } },
    })
    if (data) {
      environment.value = data
      activeEnvStore.set(data)
      setPageTitle(data.name ?? envSlug.value)
    }
  } catch {
    error.value = 'Failed to load environment'
  }
}

async function fetchProjectEnvs() {
  try {
    const { data } = await api.GET('/environments', {
      params: { query: { projectSlug: projSlug.value, page: 0, size: 50 } },
    })
    if (data?.content) projectEnvs.value = data.content
  } catch {
    // sibling-env list is decorative — ignore failures
  }
}

async function fetchResources({ isPolling = false } = {}) {
  try {
    const { data } = await api.GET('/resources', {
      params: { query: { environmentSlug: envSlug.value, page: 0, size: 50 } },
    })
    if (data) resources.value = data.content ?? []
  } catch {
    if (!isPolling) error.value = 'Failed to load resources'
  }
}

async function fetchTokens() {
  try {
    const { data } = await api.GET('/environments/{slug}/tokens', {
      params: { path: { slug: envSlug.value } },
    })
    if (data) tokens.value = data
  } catch {
    error.value = 'Failed to load tokens'
  }
}

async function fetchNamespacePrefix() {
  try {
    const { data } = await api.GET('/admin/config')
    if (data?.namespacePrefix) namespacePrefix.value = data.namespacePrefix
  } catch {
    /* keep default prefix */
  }
}

async function fetchData() {
  loading.value = true
  error.value = ''
  await Promise.all([
    fetchEnvironment(),
    fetchProjectEnvs(),
    fetchResources(),
    fetchTokens(),
    fetchNamespacePrefix(),
  ])
  loading.value = false
}

function startPolling() {
  stopPolling()
  pollInterval = setInterval(() => fetchResources({ isPolling: true }), 30000)
}

function stopPolling() {
  if (pollInterval !== null) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

// Throws on failure so the ConfirmButton awaiting this handler re-arms
// (its async-handler pattern needs the rejection to restore confirming state).
async function deleteEnvironment(): Promise<void> {
  try {
    const { error: apiError } = await api.DELETE('/environments/{slug}', {
      params: { path: { slug: envSlug.value } },
    })
    if (apiError) {
      const msg = extractApiErrorMessage(apiError, 'Failed to delete environment')
      error.value = msg
      throw new Error(msg)
    }
    refreshSidebar()
    void router.push({
      name: 'project',
      params: { wsSlug: wsSlug.value, projSlug: projSlug.value },
    })
  } catch (e) {
    if (!error.value) error.value = 'Failed to delete environment'
    throw e
  }
}

async function createToken() {
  if (!newTokenName.value.trim()) return
  createTokenLoading.value = true
  createTokenError.value = ''
  createdToken.value = null
  try {
    const { data, error: apiError } = await api.POST('/environments/{slug}/tokens', {
      params: { path: { slug: envSlug.value } },
      body: {
        name: newTokenName.value.trim(),
        role: newTokenRole.value,
        expiresInDays: newTokenExpiresDays.value ?? 90,
      },
    })
    if (apiError) {
      createTokenError.value = extractApiErrorMessage(apiError, 'Failed to create token')
      return
    }
    if (data?.token) createdToken.value = data.token
    showCreateToken.value = false
    newTokenName.value = ''
    newTokenRole.value = 'VIEWER'
    newTokenExpiresDays.value = 90
    await fetchTokens()
  } catch {
    createTokenError.value = 'Failed to create token'
  } finally {
    createTokenLoading.value = false
  }
}

async function deleteToken(tokenId: string): Promise<void> {
  try {
    const { error: apiError } = await api.DELETE('/environments/{slug}/tokens/{token_id}', {
      params: { path: { slug: envSlug.value, token_id: tokenId } },
    })
    if (apiError) {
      const msg = extractApiErrorMessage(apiError, 'Failed to delete token')
      error.value = msg
      throw new Error(msg)
    }
    await fetchTokens()
  } catch (e) {
    if (!error.value) error.value = 'Failed to delete token'
    throw e
  }
}

function openResource(slug?: string) {
  if (!slug) return
  void router.push({
    name: 'resource',
    params: {
      wsSlug: wsSlug.value,
      projSlug: projSlug.value,
      envSlug: envSlug.value,
      resSlug: slug,
    },
  })
}

function envHref(slug?: string): string {
  return slug ? `/console/${wsSlug.value}/${projSlug.value}/${slug}` : '#'
}

watch(
  () => [wsSlug.value, projSlug.value, envSlug.value],
  () => {
    stopPolling()
    fetchData()
    startPolling()
  },
)

function handleVisibilityChange() {
  if (document.hidden) {
    stopPolling()
  } else {
    fetchResources({ isPolling: true })
    startPolling()
  }
}

onMounted(() => {
  fetchData()
  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  activeEnvStore.clear()
})
</script>

<template>
  <div class="environment-view">
    <PageHeader :title="environment?.name ?? 'Environment'">
      <template #subtitle>
        <span v-if="environment" class="env-meta">
          <span class="meta-tag">environment</span>
          <span class="meta-sep">·</span>
          <span class="meta-mono">{{ environment.slug }}</span>
          <template v-if="environment.targetCluster">
            <span class="meta-sep">·</span>
            <span class="meta-mono">cluster {{ environment.targetCluster }}</span>
          </template>
        </span>
      </template>
      <template #actions>
        <ConfirmButton label="Delete Environment" :handler="deleteEnvironment" />
      </template>
    </PageHeader>

    <AppBreadcrumb :items="buildBreadcrumbChain({ wsSlug, projSlug, envSlug }, envSlug, true)" />

    <div v-if="loading" class="loading-state"><p>Loading...</p></div>

    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <button class="btn-secondary" @click="fetchData">Retry</button>
    </div>

    <template v-else>
      <!-- Title block: icon + sibling-env tabs -->
      <div class="title-block">
        <div class="title-l">
          <div class="title-ic">{{ initials(environment?.name) }}</div>
          <div class="title-strip">
            <span class="strip-pill">
              <span class="strip-dot"></span>
              <b>{{ resources.length }}</b>
              {{ resources.length === 1 ? 'resource' : 'resources' }}
            </span>
            <span v-if="namespace" class="strip-pill mono trunc" :title="namespace">
              namespace · {{ namespace }}
            </span>
            <span v-if="environment?.createdAt" class="strip-pill mono">
              created {{ formatDate(environment.createdAt) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Sibling environments tab strip -->
      <nav v-if="projectEnvs.length > 1" class="env-tabs" aria-label="Environments in project">
        <router-link
          v-for="env in projectEnvs"
          :key="env.slug"
          :to="envHref(env.slug)"
          class="env-tab"
          :class="{ on: env.slug === envSlug }"
        >
          <span
            class="env-tab-dot"
            :class="aggregateStatusClass(env.aggregateStatus)"
            :title="env.aggregateStatus ?? 'Unknown'"
          ></span>
          <span>{{ env.name }}</span>
        </router-link>
      </nav>

      <!-- Resources panel -->
      <section class="panel">
        <div class="panel-h">
          <div>
            <h3>Resources</h3>
            <p class="panel-h-sub">
              Workloads running in <span class="mono">{{ envSlug }}</span>
            </p>
          </div>
          <div class="panel-h-r">
            <span class="mono panel-h-count">{{ resources.length }}</span>
            <router-link
              :to="{ name: 'create-resource', params: { wsSlug, projSlug, envSlug } }"
              class="btn-primary btn-sm"
            >
              + Create Resource
            </router-link>
          </div>
        </div>

        <EmptyState v-if="resources.length === 0" message="No resources deployed yet." />

        <div v-else class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th class="col-name">Name</th>
                <th>Type</th>
                <th>Status</th>
                <th>Domain</th>
                <th>Slug</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="res in resources"
                :key="res.slug"
                class="table-row-link"
                tabindex="0"
                @click="openResource(res.slug)"
                @keydown.enter="openResource(res.slug)"
              >
                <td class="cell-name">
                  <span class="row-dot" :class="statusClass(res.status)"></span>
                  {{ res.name }}
                </td>
                <td class="cell-type">{{ res.type }}</td>
                <td>
                  <span class="status-badge" :class="statusClass(res.status)">
                    {{ res.status ?? 'UNKNOWN' }}
                  </span>
                </td>
                <td class="cell-mono">{{ getDomain(res) }}</td>
                <td class="cell-slug">{{ res.slug }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Tokens panel -->
      <section class="panel tokens-panel">
        <div class="panel-h">
          <div>
            <h3>Tokens</h3>
            <p class="panel-h-sub">CI / deploy tokens scoped to this environment.</p>
          </div>
          <div class="panel-h-r">
            <span class="mono panel-h-count">{{ tokens.length }}</span>
            <button class="btn-primary btn-sm" @click="openCreateToken">+ Create Token</button>
          </div>
        </div>

        <div v-if="createdToken" class="token-created-alert">
          <p class="token-created-label">
            Token created. Copy it now — it will not be shown again.
          </p>
          <div class="token-created-value">
            <code class="token-code">{{ createdToken }}</code>
            <button class="btn-secondary btn-sm" @click="createdToken = null">Dismiss</button>
          </div>
        </div>

        <EmptyState v-if="tokens.length === 0" message="No tokens for this environment." />

        <div v-else class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Role</th>
                <th>Expires</th>
                <th>Last Used</th>
                <th>Created</th>
                <th class="col-actions"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="token in tokens" :key="token.id">
                <td class="cell-name">{{ token.name }}</td>
                <td>
                  <span class="role-badge">{{ token.role }}</span>
                </td>
                <td class="cell-date">{{ formatDate(token.expiresAt) }}</td>
                <td class="cell-date">{{ formatDate(token.lastUsedAt) }}</td>
                <td class="cell-date">{{ formatDate(token.createdAt) }}</td>
                <td class="col-actions">
                  <ConfirmButton
                    label="Revoke"
                    confirm-label="Confirm Revoke"
                    btn-class="btn-danger btn-sm"
                    :handler="() => deleteToken(token.id!)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <CreateDialog
        title="Create Token"
        :open="showCreateToken"
        :loading="createTokenLoading"
        :error="createTokenError"
        @close="showCreateToken = false"
        @submit="createToken"
      >
        <div class="form-stack">
          <label class="form-label">
            Name
            <input
              v-model="newTokenName"
              class="form-input"
              type="text"
              placeholder="e.g. ci-deploy"
              autofocus
            />
          </label>
          <label class="form-label">
            Role
            <select v-model="newTokenRole" class="form-input">
              <option v-for="r in TOKEN_ROLES" :key="r" :value="r">{{ r }}</option>
            </select>
          </label>
          <label class="form-label">
            Expires In (days)
            <input
              v-model.number="newTokenExpiresDays"
              class="form-input"
              type="number"
              min="1"
              placeholder="90"
            />
          </label>
        </div>
      </CreateDialog>
    </template>
  </div>
</template>

<style scoped>
.environment-view {
  display: contents;
}

/* Title meta in PageHeader subtitle */
.env-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.meta-tag {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 2px 7px;
}
.meta-sep {
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
}
.meta-mono {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
}

/* Title block — icon + stat strip */
.title-block {
  display: flex;
  margin: 0 0 16px;
}
.title-l {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.title-ic {
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
.title-strip {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.strip-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 2px 8px;
  background: var(--color-bg-surface);
}
.strip-pill b {
  color: var(--color-text-primary);
  font-weight: 500;
}
.strip-pill.trunc {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.strip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-status-ready);
}

/* Env tabs across the project */
.env-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--color-border);
  margin: 6px 0 22px;
  overflow-x: auto;
}
.env-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  color: var(--color-text-secondary);
  text-decoration: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  white-space: nowrap;
  transition: color 120ms ease;
}
.env-tab:hover {
  color: var(--color-text-primary);
}
.env-tab.on {
  color: var(--color-text-primary);
  font-weight: 500;
  border-bottom-color: var(--color-accent);
}
.env-tab-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-tertiary);
  flex-shrink: 0;
}
.env-tab-dot.status-ready {
  background: var(--color-status-ready);
}
.env-tab-dot.status-pending {
  background: var(--color-status-pending);
}
.env-tab-dot.status-degraded {
  background: var(--color-status-degraded);
}
.env-tab-dot.status-error {
  background: var(--color-status-error);
}
.env-tab-dot.status-stopped {
  background: var(--color-status-stopped);
}

/* Panels */
.panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  margin-bottom: 18px;
}
.panel-h {
  padding: 12px 18px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.panel-h h3 {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}
.panel-h-sub {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 2px 0 0;
  font-weight: 300;
}
.panel-h-r {
  display: flex;
  align-items: center;
  gap: 12px;
}
.panel-h-count {
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.tokens-panel {
  margin-top: 24px;
}

/* Tables */
.table-wrap {
  overflow-x: auto;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.data-table th {
  padding: 10px 18px;
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
  padding: 12px 18px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-weight: 300;
  vertical-align: middle;
}
.data-table tbody tr:last-child td {
  border-bottom: none;
}
.col-actions {
  width: 120px;
}

.table-row-link {
  cursor: pointer;
  transition: background-color 120ms ease;
}
.table-row-link:hover td,
.table-row-link:focus-visible td {
  background: var(--color-bg-raised);
}
.table-row-link:focus-visible {
  outline: none;
}

/* Cell types */
.cell-name {
  font-weight: 500;
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
  gap: 10px;
}
.cell-type {
  color: var(--color-text-secondary);
}
.cell-mono {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-secondary);
}
.cell-slug {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
}
.cell-date {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
}
.row-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-text-tertiary);
  flex-shrink: 0;
}
.row-dot.status-ready {
  background: var(--color-status-ready);
}
.row-dot.status-pending {
  background: var(--color-status-pending);
}
.row-dot.status-degraded {
  background: var(--color-status-degraded);
}
.row-dot.status-error {
  background: var(--color-status-error);
}
.row-dot.status-stopped {
  background: var(--color-status-stopped);
}

/* Status badge — same idiom as resource detail */
.status-badge {
  display: inline-flex;
  align-items: center;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-weight: 500;
  padding: 3px 9px 2px;
  border-radius: var(--radius-sm);
  border: 1px solid currentColor;
}

/* Token-specific bits (kept) */
.token-created-alert {
  margin: 14px 18px 0;
  padding: 12px 16px;
  background-color: var(--color-bg-raised);
  border: 1px solid var(--color-status-ready);
  border-radius: var(--radius-md);
}
.token-created-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-status-ready);
  margin-bottom: 8px;
}
.token-created-value {
  display: flex;
  align-items: center;
  gap: 12px;
}
.token-code {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-primary);
  background-color: var(--color-bg-base);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  word-break: break-all;
  flex: 1;
}
.role-badge {
  display: inline-block;
  padding: 2px 8px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-base);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}

.btn-sm {
  padding: 5px 11px;
  font-size: 12px;
}

.mono {
  font-family: var(--font-mono);
}
</style>
