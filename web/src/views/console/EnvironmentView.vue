<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import DataTable from '@/components/DataTable.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import ConfirmButton from '@/components/ConfirmButton.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'
import { statusClass, getDomain } from '@/composables/useResourceHelpers'
import { formatDate } from '@/utils/format'
import { usePageTitle } from '@/composables/usePageTitle'

type Environment = components['schemas']['Environment']
type Resource = components['schemas']['Resource']
type EnvironmentToken = components['schemas']['EnvironmentToken']

const namespacePrefix = ref('abp')

const TOKEN_ROLES = ['EDITOR', 'VIEWER'] as const
type TokenRole = (typeof TOKEN_ROLES)[number]

const route = useRoute()
const router = useRouter()

const environment = ref<Environment | null>(null)
const resources = ref<Resource[]>([])
const loading = ref(true)
const error = ref('')
let pollInterval: ReturnType<typeof setInterval> | null = null

const { setPageTitle } = usePageTitle()
const wsSlug = computed(() => route.params.wsSlug as string)
const projSlug = computed(() => route.params.projSlug as string)
const envSlug = computed(() => route.params.envSlug as string)

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
      setPageTitle(data.name ?? envSlug.value)
    }
  } catch {
    error.value = 'Failed to load environment'
  }
}

async function fetchResources({ isPolling = false } = {}) {
  try {
    const { data } = await api.GET('/resources', {
      params: { query: { environmentSlug: envSlug.value, page: 0, size: 50 } },
    })
    if (data) {
      resources.value = data.content ?? []
    }
  } catch {
    if (!isPolling) {
      error.value = 'Failed to load resources'
    }
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
    error.value = 'Failed to load tokens'
  }
}

async function fetchNamespacePrefix() {
  try {
    const { data } = await api.GET('/admin/config')
    if (data?.namespacePrefix) {
      namespacePrefix.value = data.namespacePrefix
    }
  } catch {
    // Fall back to default prefix
  }
}

async function fetchData() {
  loading.value = true
  error.value = ''
  await Promise.all([fetchEnvironment(), fetchResources(), fetchTokens(), fetchNamespacePrefix()])
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

async function deleteEnvironment() {
  try {
    await api.DELETE('/environments/{slug}', {
      params: { path: { slug: envSlug.value } },
    })
    router.push({ name: 'project', params: { wsSlug: wsSlug.value, projSlug: projSlug.value } })
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
        expiresInDays: newTokenExpiresDays.value ?? 90,
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

async function deleteToken(tokenId: string) {
  try {
    await api.DELETE('/environments/{slug}/tokens/{tokenId}', {
      params: { path: { slug: envSlug.value, tokenId } },
    })
    await fetchTokens()
  } catch {
    error.value = 'Failed to delete token'
  }
}

watch(
  () => [wsSlug.value, projSlug.value, envSlug.value],
  () => {
    stopPolling()
    fetchData()
    startPolling()
  },
  { immediate: true },
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
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div>
    <PageHeader :title="environment?.name ?? 'Environment'">
      <template #actions>
        <ConfirmButton label="Delete Environment" @confirm="deleteEnvironment" />
      </template>
    </PageHeader>

    <AppBreadcrumb :items="buildBreadcrumbChain({ wsSlug, projSlug, envSlug }, envSlug, true)" />

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
          <span class="summary-value summary-value--mono"
            >{{ namespacePrefix }}-{{ environment?.slug }}</span
          >
        </div>
      </div>

      <!-- Resources -->
      <div class="resources-header">
        <h2 class="section-title">Resources</h2>
        <router-link
          :to="{ name: 'create-resource', params: { wsSlug, projSlug, envSlug } }"
          class="btn-primary"
          >+ Create Resource</router-link
        >
      </div>

      <EmptyState v-if="resources.length === 0" message="No resources deployed yet." />

      <DataTable v-else>
        <template #header>
          <th>Name</th>
          <th>Type</th>
          <th>Status</th>
          <th>Domain</th>
          <th>Slug</th>
        </template>
        <template #body>
          <tr
            v-for="res in resources"
            :key="res.slug"
            class="table-row-link"
            tabindex="0"
            @click="
              $router.push({
                name: 'resource',
                params: { wsSlug, projSlug, envSlug, resSlug: res.slug },
              })
            "
            @keydown.enter="
              $router.push({
                name: 'resource',
                params: { wsSlug, projSlug, envSlug, resSlug: res.slug },
              })
            "
          >
            <td class="cell-name">{{ res.name }}</td>
            <td class="cell-type">{{ res.type }}</td>
            <td>
              <span class="status-badge" :class="statusClass(res.status)">
                {{ res.status ?? 'UNKNOWN' }}
              </span>
            </td>
            <td class="cell-mono">{{ getDomain(res) }}</td>
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
                <ConfirmButton
                  label="Revoke"
                  confirm-label="Confirm Revoke"
                  btn-class="btn-danger btn-sm"
                  @confirm="deleteToken(token.id!)"
                />
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

.resources-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.resources-header .section-title {
  margin-bottom: 0;
}
</style>
