<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import PaginationControls from '@/components/PaginationControls.vue'
import DataTable from '@/components/DataTable.vue'

type Project = components['schemas']['Project']
type Environment = components['schemas']['Environment']

const route = useRoute()

const project = ref<Project | null>(null)
const environments = ref<Environment[]>([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const showCreate = ref(false)
const createLoading = ref(false)
const newName = ref('')
const error = ref('')

const wsSlug = ref(route.params.wsSlug as string)
const projSlug = ref(route.params.projSlug as string)

async function fetchData() {
  loading.value = true
  error.value = ''
  try {
    const [projRes, envRes] = await Promise.all([
      api.GET('/projects/{slug}', {
        params: { path: { slug: projSlug.value } },
      }),
      api.GET('/environments', {
        params: { query: { projectSlug: projSlug.value, page: page.value, size: 20 } },
      }),
    ])
    if (projRes.data) {
      project.value = projRes.data
    }
    if (envRes.data) {
      environments.value = envRes.data.content ?? []
      totalPages.value = envRes.data.totalPages ?? 0
    }
  } catch {
    error.value = 'Failed to load project data'
  } finally {
    loading.value = false
  }
}

async function createEnvironment() {
  if (!newName.value.trim()) return
  createLoading.value = true
  try {
    await api.POST('/environments', {
      body: { name: newName.value.trim(), projectSlug: projSlug.value },
    })
    showCreate.value = false
    newName.value = ''
    await fetchData()
  } catch {
    error.value = 'Failed to create environment'
  } finally {
    createLoading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  fetchData()
}

function formatDate(iso?: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

watch(
  () => [route.params.wsSlug, route.params.projSlug],
  ([ws, proj]) => {
    if (ws && proj && typeof ws === 'string' && typeof proj === 'string') {
      wsSlug.value = ws
      projSlug.value = proj
      page.value = 0
      fetchData()
    }
  },
)

onMounted(fetchData)
</script>

<template>
  <div>
    <PageHeader :title="project?.name ?? 'Project'">
      <template #actions>
        <button class="btn-primary" @click="showCreate = true">+ Create Environment</button>
      </template>
    </PageHeader>

    <!-- Breadcrumb -->
    <div class="breadcrumb">
      <router-link to="/console" class="breadcrumb-link">Workspaces</router-link>
      <span class="breadcrumb-sep">/</span>
      <router-link :to="`/console/${wsSlug}`" class="breadcrumb-link">{{ wsSlug }}</router-link>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-current">{{ projSlug }}</span>
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

    <!-- Empty -->
    <EmptyState
      v-else-if="environments.length === 0"
      message="No environments in this project yet. Create one to get started."
    >
      <template #action>
        <button class="btn-primary" @click="showCreate = true">+ Create Environment</button>
      </template>
    </EmptyState>

    <!-- Environments table -->
    <template v-else>
      <DataTable>
        <template #header>
          <th>Name</th>
          <th>Slug</th>
          <th>Target Cluster</th>
          <th>Created</th>
        </template>
        <template #body>
          <tr
            v-for="env in environments"
            :key="env.slug"
            class="table-row-link"
            @click="$router.push(`/console/${wsSlug}/${projSlug}/${env.slug}`)"
          >
            <td class="cell-name">{{ env.name }}</td>
            <td class="cell-slug">{{ env.slug }}</td>
            <td class="cell-cluster">{{ env.targetCluster ?? '(not set)' }}</td>
            <td class="cell-date">{{ formatDate(env.createdAt) }}</td>
          </tr>
        </template>
      </DataTable>

      <PaginationControls :page="page" :total-pages="totalPages" @update:page="onPageChange" />
    </template>

    <!-- Create dialog -->
    <CreateDialog
      title="Create Environment"
      :open="showCreate"
      :loading="createLoading"
      @close="showCreate = false"
      @submit="createEnvironment"
    >
      <label class="form-label">
        Name
        <input
          v-model="newName"
          class="form-input"
          type="text"
          placeholder="e.g. staging"
          autofocus
        />
      </label>
    </CreateDialog>
  </div>
</template>
