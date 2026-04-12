<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import PaginationControls from '@/components/PaginationControls.vue'
import EntityCard from '@/components/EntityCard.vue'
import { useSidebarRefresh } from '@/composables/useSidebarRefresh'

type Workspace = components['schemas']['Workspace']

const workspaces = ref<Workspace[]>([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const showCreate = ref(false)
const createLoading = ref(false)
const newName = ref('')
const error = ref('')
const { refreshSidebar } = useSidebarRefresh()

async function fetchWorkspaces() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.GET('/workspaces', {
      params: { query: { page: page.value, size: 20 } },
    })
    if (data) {
      workspaces.value = data.content ?? []
      totalPages.value = data.totalPages ?? 0
    }
  } catch {
    error.value = 'Failed to load workspaces'
  } finally {
    loading.value = false
  }
}

async function createWorkspace() {
  if (!newName.value.trim()) return
  createLoading.value = true
  try {
    await api.POST('/workspaces', {
      body: { name: newName.value.trim() },
    })
    showCreate.value = false
    newName.value = ''
    await fetchWorkspaces()
    refreshSidebar()
  } catch {
    error.value = 'Failed to create workspace'
  } finally {
    createLoading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  fetchWorkspaces()
}

onMounted(fetchWorkspaces)
</script>

<template>
  <div>
    <PageHeader title="Workspaces">
      <template #actions>
        <button class="btn-primary" @click="showCreate = true">+ Create Workspace</button>
      </template>
    </PageHeader>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <p>Loading workspaces...</p>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <button class="btn-secondary" @click="fetchWorkspaces">Retry</button>
    </div>

    <!-- Empty -->
    <EmptyState
      v-else-if="workspaces.length === 0"
      message="No workspaces yet. Create one to get started."
    >
      <template #action>
        <button class="btn-primary" @click="showCreate = true">+ Create Workspace</button>
      </template>
    </EmptyState>

    <!-- Workspace grid -->
    <template v-else>
      <div class="card-grid">
        <EntityCard
          v-for="ws in workspaces"
          :key="ws.slug"
          :name="ws.name ?? ''"
          :slug="ws.slug ?? ''"
          :to="`/console/${ws.slug}`"
          :date="ws.createdAt"
        />
      </div>

      <PaginationControls :page="page" :total-pages="totalPages" @update:page="onPageChange" />
    </template>

    <!-- Create dialog -->
    <CreateDialog
      title="Create Workspace"
      :open="showCreate"
      :loading="createLoading"
      @close="showCreate = false"
      @submit="createWorkspace"
    >
      <label class="form-label">
        Name
        <input
          v-model="newName"
          class="form-input"
          type="text"
          placeholder="e.g. My Team"
          autofocus
        />
      </label>
    </CreateDialog>
  </div>
</template>
