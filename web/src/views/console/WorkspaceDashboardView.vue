<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import PaginationControls from '@/components/PaginationControls.vue'
import EntityCard from '@/components/EntityCard.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'

type Workspace = components['schemas']['Workspace']
type Project = components['schemas']['Project']

const route = useRoute()

const workspace = ref<Workspace | null>(null)
const projects = ref<Project[]>([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const showCreate = ref(false)
const createLoading = ref(false)
const newName = ref('')
const error = ref('')

const wsSlug = ref(route.params.wsSlug as string)

async function fetchData() {
  loading.value = true
  error.value = ''
  try {
    const [wsRes, projRes] = await Promise.all([
      api.GET('/workspaces/{slug}', {
        params: { path: { slug: wsSlug.value } },
      }),
      api.GET('/projects', {
        params: { query: { workspaceSlug: wsSlug.value, page: page.value, size: 20 } },
      }),
    ])
    if (wsRes.data) {
      workspace.value = wsRes.data
    }
    if (projRes.data) {
      projects.value = projRes.data.content ?? []
      totalPages.value = projRes.data.totalPages ?? 0
    }
  } catch {
    error.value = 'Failed to load workspace data'
  } finally {
    loading.value = false
  }
}

async function createProject() {
  if (!newName.value.trim()) return
  createLoading.value = true
  try {
    await api.POST('/projects', {
      body: { name: newName.value.trim(), workspaceSlug: wsSlug.value },
    })
    showCreate.value = false
    newName.value = ''
    await fetchData()
  } catch {
    error.value = 'Failed to create project'
  } finally {
    createLoading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  fetchData()
}

watch(
  () => route.params.wsSlug,
  (slug) => {
    if (slug && typeof slug === 'string') {
      wsSlug.value = slug
      page.value = 0
      fetchData()
    }
  },
)

onMounted(fetchData)
</script>

<template>
  <div>
    <PageHeader :title="workspace?.name ?? 'Workspace'">
      <template #actions>
        <button class="btn-primary" @click="showCreate = true">+ Create Project</button>
      </template>
    </PageHeader>

    <AppBreadcrumb :items="buildBreadcrumbChain({ wsSlug }, wsSlug, true)" />

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
      v-else-if="projects.length === 0"
      message="No projects in this workspace yet. Create one to get started."
    >
      <template #action>
        <button class="btn-primary" @click="showCreate = true">+ Create Project</button>
      </template>
    </EmptyState>

    <!-- Project grid -->
    <template v-else>
      <div class="card-grid">
        <EntityCard
          v-for="proj in projects"
          :key="proj.slug"
          :name="proj.name ?? ''"
          :slug="proj.slug ?? ''"
          :to="`/console/${wsSlug}/${proj.slug}`"
          :date="proj.createdAt"
        />
      </div>

      <PaginationControls :page="page" :total-pages="totalPages" @update:page="onPageChange" />
    </template>

    <!-- Create dialog -->
    <CreateDialog
      title="Create Project"
      :open="showCreate"
      :loading="createLoading"
      @close="showCreate = false"
      @submit="createProject"
    >
      <label class="form-label">
        Name
        <input
          v-model="newName"
          class="form-input"
          type="text"
          placeholder="e.g. Frontend App"
          autofocus
        />
      </label>
    </CreateDialog>
  </div>
</template>
