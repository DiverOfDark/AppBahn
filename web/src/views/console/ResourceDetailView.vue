<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import DataTable from '@/components/DataTable.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import ConfirmButton from '@/components/ConfirmButton.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'
import { statusClass, getDomain } from '@/composables/useResourceHelpers'
import { formatDate } from '@/utils/format'
import { usePageTitle } from '@/composables/usePageTitle'

type Resource = components['schemas']['Resource']
type Deployment = components['schemas']['Deployment']

const route = useRoute()
const router = useRouter()

const wsSlug = computed(() => route.params.wsSlug as string)
const projSlug = computed(() => route.params.projSlug as string)
const envSlug = computed(() => route.params.envSlug as string)
const resSlug = computed(() => route.params.resSlug as string)
const { setPageTitle } = usePageTitle()

const resource = ref<Resource | null>(null)
const deployments = ref<Deployment[]>([])
const deploymentsPage = ref(0)
const deploymentsTotalPages = ref(0)
const loading = ref(true)
const error = ref('')
const deleteBtn = ref<InstanceType<typeof ConfirmButton> | null>(null)
const deployLoading = ref(false)
let pollInterval: ReturnType<typeof setInterval> | null = null

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
        query: { page: deploymentsPage.value, size: 10 },
      },
    })
    if (data?.content) {
      deployments.value = data.content
    }
    deploymentsTotalPages.value = data?.totalPages ?? 0
  } catch {
    error.value = 'Failed to load deployments'
  }
}

async function fetchAll() {
  loading.value = true
  await Promise.all([fetchResource(), fetchDeployments()])
  loading.value = false
}

async function triggerDeploy() {
  deployLoading.value = true
  try {
    await api.POST('/resources/{slug}/deployments', {
      params: { path: { slug: resSlug.value } },
      body: {},
    })
    await fetchDeployments()
  } catch {
    error.value = 'Failed to trigger deployment'
  } finally {
    deployLoading.value = false
  }
}

async function deleteResource() {
  try {
    await api.DELETE('/resources/{slug}', {
      params: { path: { slug: resSlug.value } },
    })
    router.push({
      name: 'environment',
      params: { wsSlug: wsSlug.value, projSlug: projSlug.value, envSlug: envSlug.value },
    })
  } catch {
    error.value = 'Failed to delete resource'
    deleteBtn.value?.reset()
  }
}

watch(resSlug, () => {
  error.value = ''
  deleteBtn.value?.reset()
  deployLoading.value = false
  resource.value = null
  deployments.value = []
  deploymentsPage.value = 0
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
  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div class="page">
    <PageHeader :title="resource?.name ?? resSlug">
      <template #actions>
        <button class="btn-secondary" :disabled="deployLoading" @click="triggerDeploy">
          {{ deployLoading ? 'Deploying...' : 'Deploy' }}
        </button>
        <ConfirmButton ref="deleteBtn" @confirm="deleteResource" />
      </template>
    </PageHeader>

    <AppBreadcrumb :items="buildBreadcrumbChain({ wsSlug, projSlug, envSlug }, resSlug, true)" />

    <div v-if="error" class="error-banner">{{ error }}</div>

    <div v-if="loading" class="loading">Loading...</div>

    <template v-else-if="resource">
      <!-- Overview -->
      <div class="summary-bar">
        <div class="summary-item">
          <span class="summary-label">Status</span>
          <span class="status-badge" :class="statusClass(resource.status)">
            {{ resource.status ?? 'UNKNOWN' }}
          </span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Type</span>
          <span class="summary-value">{{ resource.type }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Slug</span>
          <span class="summary-value summary-value--mono">{{ resource.slug }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Domain</span>
          <span class="summary-value summary-value--mono">{{ getDomain(resource) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Last Synced</span>
          <span class="summary-value">{{ formatDate(resource.lastSyncedAt) }}</span>
        </div>
      </div>

      <!-- Config -->
      <h2 class="section-title">Configuration</h2>
      <pre class="config-block">{{ JSON.stringify(resource.config, null, 2) }}</pre>

      <!-- Deployments -->
      <h2 class="section-title">Deployments</h2>

      <EmptyState v-if="deployments.length === 0" message="No deployments yet." />

      <DataTable v-else>
        <template #header>
          <th>Status</th>
          <th>Image</th>
          <th>Triggered By</th>
          <th>Primary</th>
          <th>Created</th>
        </template>
        <template #body>
          <tr v-for="dep in deployments" :key="dep.id">
            <td>
              <span class="status-badge" :class="statusClass(dep.status)">
                {{ dep.status }}
              </span>
            </td>
            <td class="cell-mono">{{ dep.imageRef ?? '-' }}</td>
            <td>{{ dep.triggeredBy }}</td>
            <td>{{ dep.isPrimary ? 'Yes' : 'No' }}</td>
            <td>{{ formatDate(dep.createdAt) }}</td>
          </tr>
        </template>
      </DataTable>

      <div v-if="deploymentsTotalPages > 1" class="pagination">
        <button
          class="btn-secondary btn-sm"
          :disabled="deploymentsPage === 0"
          @click="(deploymentsPage--, fetchDeployments())"
        >
          Previous
        </button>
        <span class="pagination-info"
          >Page {{ deploymentsPage + 1 }} of {{ deploymentsTotalPages }}</span
        >
        <button
          class="btn-secondary btn-sm"
          :disabled="deploymentsPage >= deploymentsTotalPages - 1"
          @click="(deploymentsPage++, fetchDeployments())"
        >
          Next
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 12px;
}

.pagination-info {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}
</style>
