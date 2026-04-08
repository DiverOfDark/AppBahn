<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import DataTable from '@/components/DataTable.vue'
import CreateDialog from '@/components/CreateDialog.vue'

type Environment = components['schemas']['Environment']
type Resource = components['schemas']['Resource']
type EnvironmentToken = components['schemas']['EnvironmentToken']

const TOKEN_ROLES = ['EDITOR', 'VIEWER'] as const
type TokenRole = (typeof TOKEN_ROLES)[number]

const route = useRoute()
const router = useRouter()

const environment = ref<Environment | null>(null)
const resources = ref<Resource[]>([])
const loading = ref(true)
const error = ref('')
let pollInterval: ReturnType<typeof setInterval> | null = null

const wsSlug = ref(route.params.wsSlug as string)
const projSlug = ref(route.params.projSlug as string)
const envSlug = ref(route.params.envSlug as string)

// -- Tokens --
const tokens = ref<EnvironmentToken[]>([])
const showCreateToken = ref(false)
const createTokenLoading = ref(false)
const newTokenName = ref('')
const newTokenRole = ref<TokenRole>('VIEWER')
const newTokenExpiresDays = ref<number | undefined>(90)
const createdToken = ref<string | null>(null)

async function fetchEnvironment() {
  try {
    const { data } = await api.GET('/environments/{slug}', {
      params: { path: { slug: envSlug.value } },
    })
    if (data) {
      environment.value = data
    }
  } catch {
    error.value = 'Failed to load environment'
  }
}

async function fetchResources() {
  try {
    const { data } = await api.GET('/resources', {
      params: { query: { environmentSlug: envSlug.value, page: 0, size: 50 } },
    })
    if (data) {
      resources.value = data.content ?? []
    }
  } catch {
    // Silently fail on poll — keep showing last data
  }
}

async function fetchTokens() {
  try {
    const { data } = await api.GET('/environments/{slug}/tokens', {
      params: { path: { slug: envSlug.value } },
    })
    if (data) {
      tokens.value = data
    }
  } catch {
    // Non-critical — don't block the page
  }
}

async function fetchData() {
  loading.value = true
  error.value = ''
  await Promise.all([fetchEnvironment(), fetchResources(), fetchTokens()])
  loading.value = false
}

function startPolling() {
  stopPolling()
  pollInterval = setInterval(fetchResources, 10000)
}

function stopPolling() {
  if (pollInterval !== null) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

async function deleteEnvironment() {
  const confirmed = window.confirm(
    `Are you sure you want to delete environment "${environment.value?.name ?? envSlug.value}"? This action cannot be undone.`,
  )
  if (!confirmed) return

  try {
    await api.DELETE('/environments/{slug}', {
      params: { path: { slug: envSlug.value } },
    })
    router.push(`/console/${wsSlug.value}/${projSlug.value}`)
  } catch {
    error.value = 'Failed to delete environment'
  }
}

// -- Token actions --

async function createToken() {
  if (!newTokenName.value.trim()) return
  createTokenLoading.value = true
  createdToken.value = null
  try {
    const { data } = await api.POST('/environments/{slug}/tokens', {
      params: { path: { slug: envSlug.value } },
      body: {
        name: newTokenName.value.trim(),
        role: newTokenRole.value,
        expiresInDays: newTokenExpiresDays.value,
      },
    })
    if (data?.token) {
      createdToken.value = data.token
    }
    showCreateToken.value = false
    newTokenName.value = ''
    newTokenRole.value = 'VIEWER'
    newTokenExpiresDays.value = 90
    await fetchTokens()
  } catch {
    error.value = 'Failed to create token'
  } finally {
    createTokenLoading.value = false
  }
}

async function deleteToken(tokenId: string, tokenName?: string) {
  const confirmed = window.confirm(`Revoke token "${tokenName ?? tokenId}"? This cannot be undone.`)
  if (!confirmed) return
  try {
    await api.DELETE('/environments/{slug}/tokens/{tokenId}', {
      params: { path: { slug: envSlug.value, tokenId } },
    })
    await fetchTokens()
  } catch {
    error.value = 'Failed to delete token'
  }
}

function formatDate(iso?: string): string {
  if (!iso) return '--'
  const d = new Date(iso)
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function statusClass(status?: string): string {
  if (!status) return ''
  const map: Record<string, string> = {
    READY: 'status-ready',
    PENDING: 'status-pending',
    DEGRADED: 'status-degraded',
    ERROR: 'status-error',
    STOPPED: 'status-stopped',
    RESTARTING: 'status-pending',
  }
  return map[status] ?? ''
}

watch(
  () => [route.params.wsSlug, route.params.projSlug, route.params.envSlug],
  ([ws, proj, env]) => {
    if (
      ws &&
      proj &&
      env &&
      typeof ws === 'string' &&
      typeof proj === 'string' &&
      typeof env === 'string'
    ) {
      wsSlug.value = ws
      projSlug.value = proj
      envSlug.value = env
      fetchData()
      startPolling()
    }
  },
)

onMounted(() => {
  fetchData()
  startPolling()
})

onUnmounted(stopPolling)
</script>

<template>
  <div>
    <PageHeader :title="environment?.name ?? 'Environment'">
      <template #actions>
        <button class="btn-danger" @click="deleteEnvironment">Delete Environment</button>
      </template>
    </PageHeader>

    <!-- Breadcrumb -->
    <div class="breadcrumb">
      <router-link to="/console" class="breadcrumb-link">Workspaces</router-link>
      <span class="breadcrumb-sep">/</span>
      <router-link :to="`/console/${wsSlug}`" class="breadcrumb-link">{{ wsSlug }}</router-link>
      <span class="breadcrumb-sep">/</span>
      <router-link :to="`/console/${wsSlug}/${projSlug}`" class="breadcrumb-link">{{
        projSlug
      }}</router-link>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-current">{{ envSlug }}</span>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <p>Loading...</p>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <button class="btn-secondary" @click="fetchData">Retry</button>
    </div>

    <template v-else>
      <!-- Summary bar -->
      <div class="summary-bar">
        <div class="summary-item">
          <span class="summary-label">Name</span>
          <span class="summary-value">{{ environment?.name }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Slug</span>
          <span class="summary-value summary-value--mono">{{ environment?.slug }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Target Cluster</span>
          <span class="summary-value">{{ environment?.targetCluster ?? '(not set)' }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Namespace</span>
          <span class="summary-value summary-value--mono">abp-{{ environment?.slug }}</span>
        </div>
      </div>

      <!-- Resources -->
      <h2 class="section-title">Resources</h2>

      <EmptyState v-if="resources.length === 0" message="No resources deployed yet." />

      <DataTable v-else>
        <template #header>
          <th>Name</th>
          <th>Type</th>
          <th>Status</th>
          <th>Slug</th>
        </template>
        <template #body>
          <tr
            v-for="res in resources"
            :key="res.slug"
            class="table-row-link"
            @click="$router.push(`/console/${wsSlug}/${projSlug}/${envSlug}/${res.slug}`)"
          >
            <td class="cell-name">{{ res.name }}</td>
            <td class="cell-type">{{ res.type }}</td>
            <td>
              <span class="status-badge" :class="statusClass(res.status)">
                {{ res.status ?? 'UNKNOWN' }}
              </span>
            </td>
            <td class="cell-slug">{{ res.slug }}</td>
          </tr>
        </template>
      </DataTable>

      <!-- Tokens -->
      <div class="tokens-section">
        <div class="tokens-header">
          <h2 class="section-title">Tokens</h2>
          <button class="btn-primary" @click="showCreateToken = true">+ Create Token</button>
        </div>

        <!-- Created token alert (shown once) -->
        <div v-if="createdToken" class="token-created-alert">
          <p class="token-created-label">
            Token created successfully. Copy it now -- it will not be shown again.
          </p>
          <div class="token-created-value">
            <code class="token-code">{{ createdToken }}</code>
            <button class="btn-secondary btn-sm" @click="createdToken = null">Dismiss</button>
          </div>
        </div>

        <EmptyState v-if="tokens.length === 0" message="No tokens for this environment." />

        <DataTable v-else>
          <template #header>
            <th>Name</th>
            <th>Role</th>
            <th>Expires At</th>
            <th>Last Used</th>
            <th>Created At</th>
            <th>Actions</th>
          </template>
          <template #body>
            <tr v-for="token in tokens" :key="token.id">
              <td class="cell-name">{{ token.name }}</td>
              <td>
                <span class="role-badge">{{ token.role }}</span>
              </td>
              <td class="cell-date">{{ formatDate(token.expiresAt) }}</td>
              <td class="cell-date">{{ formatDate(token.lastUsedAt) }}</td>
              <td class="cell-date">{{ formatDate(token.createdAt) }}</td>
              <td>
                <button class="btn-danger btn-sm" @click="deleteToken(token.id!, token.name)">
                  Revoke
                </button>
              </td>
            </tr>
          </template>
        </DataTable>
      </div>

      <!-- Create Token Dialog -->
      <CreateDialog
        title="Create Token"
        :open="showCreateToken"
        :loading="createTokenLoading"
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
.summary-bar {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1px;
  background-color: var(--color-border);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: 32px;
}

@media (min-width: 768px) {
  .summary-bar {
    grid-template-columns: repeat(4, 1fr);
  }
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  background-color: var(--color-bg-surface);
}

.summary-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-tertiary);
}

.summary-value {
  font-size: 14px;
  color: var(--color-text-primary);
}

.summary-value--mono {
  font-family: var(--font-mono);
  font-size: 13px;
}

.section-title {
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 12px;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-raised);
  color: var(--color-text-secondary);
}

.status-ready {
  color: var(--color-status-ready);
}

.status-pending {
  color: var(--color-status-pending);
}

.status-degraded {
  color: var(--color-status-degraded);
}

.status-error {
  color: var(--color-status-error);
}

.status-stopped {
  color: var(--color-status-stopped);
}

/* ── Tokens section ──────────────────────────────────────────────── */

.tokens-section {
  margin-top: 40px;
}

.tokens-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.tokens-header .section-title {
  margin-bottom: 0;
}

.token-created-alert {
  padding: 12px 16px;
  margin-bottom: 16px;
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
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-raised);
  color: var(--color-text-secondary);
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
