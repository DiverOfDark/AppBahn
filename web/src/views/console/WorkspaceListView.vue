<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import PaginationControls from '@/components/PaginationControls.vue'
import { useSidebarRefresh } from '@/composables/useSidebarRefresh'
import { useCurrentUser } from '@/composables/useCurrentUser'
import { formatDateShort } from '@/utils/format'
import { extractApiErrorMessage } from '@/utils/apiError'
import { initials } from '@/utils/resource'

type Workspace = components['schemas']['Workspace']
type ViewMode = 'cards' | 'list'
type RoleFilter = 'all' | 'owned' | 'member'

const VIEW_PREF_KEY = 'appbahn.workspacesView'

const workspaces = ref<Workspace[]>([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const showCreate = ref(false)
const createLoading = ref(false)
const createError = ref('')

function openCreate() {
  createError.value = ''
  newName.value = ''
  showCreate.value = true
}
const newName = ref('')
const error = ref('')
const searchQuery = ref('')
const viewMode = ref<ViewMode>(readSavedView())
const roleFilter = ref<RoleFilter>('all')
const { refreshSidebar } = useSidebarRefresh()
const { user, fetch: fetchCurrentUser } = useCurrentUser()

const userLabel = computed(() => user.value?.name ?? user.value?.email ?? null)

function readSavedView(): ViewMode {
  if (typeof localStorage === 'undefined') return 'cards'
  return localStorage.getItem(VIEW_PREF_KEY) === 'list' ? 'list' : 'cards'
}

watch(viewMode, (m) => {
  if (typeof localStorage !== 'undefined') localStorage.setItem(VIEW_PREF_KEY, m)
})

const ROLE_LABEL: Record<string, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  EDITOR: 'Editor',
  VIEWER: 'Viewer',
}

function roleChipTone(role?: string): string {
  switch (role) {
    case 'OWNER':
      return 'owner'
    case 'ADMIN':
      return 'admin'
    case 'EDITOR':
      return 'editor'
    default:
      return 'viewer'
  }
}

const filtered = computed(() => {
  let list = workspaces.value

  if (roleFilter.value === 'owned') {
    list = list.filter((w) => w.callerRole === 'OWNER')
  } else if (roleFilter.value === 'member') {
    list = list.filter((w) => w.callerRole !== 'OWNER')
  }

  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(
    (w) => (w.name ?? '').toLowerCase().includes(q) || (w.slug ?? '').toLowerCase().includes(q),
  )
})

function paletteSlot(slug?: string): string {
  if (!slug) return 'a'
  let h = 0
  for (let i = 0; i < slug.length; i++) h = (h * 31 + slug.charCodeAt(i)) >>> 0
  return ['a', 'b', 'c', 'd', 'e', 'f'][h % 6] ?? 'a'
}

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
      totalElements.value = data.totalElements ?? workspaces.value.length
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
  createError.value = ''
  try {
    const { error: apiError } = await api.POST('/workspaces', {
      body: { name: newName.value.trim() },
    })
    if (apiError) {
      createError.value = extractApiErrorMessage(apiError, 'Failed to create workspace')
      return
    }
    showCreate.value = false
    newName.value = ''
    await fetchWorkspaces()
    refreshSidebar()
  } catch {
    createError.value = 'Failed to create workspace'
  } finally {
    createLoading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  fetchWorkspaces()
}

onMounted(() => {
  fetchWorkspaces()
  fetchCurrentUser()
})
</script>

<template>
  <div class="workspaces-view">
    <div v-if="userLabel" class="signed-in-line">Signed in as {{ userLabel }}</div>

    <PageHeader title="Workspaces">
      <template #subtitle>
        <span v-if="!loading && workspaces.length > 0">
          <b class="sub-strong">{{ totalElements }}</b>
          {{ totalElements === 1 ? 'workspace' : 'workspaces' }}
        </span>
        <span v-else-if="!loading">no workspaces yet</span>
      </template>
      <template #actions>
        <button class="btn-primary" @click="openCreate">+ Create Workspace</button>
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
        <button class="btn-primary" @click="openCreate">+ Create Workspace</button>
      </template>
    </EmptyState>

    <!-- Workspace list -->
    <template v-else>
      <div class="bar">
        <input
          v-model="searchQuery"
          class="bar-input"
          type="search"
          placeholder="Filter workspaces on this page…"
          aria-label="Filter workspaces"
        />
        <div class="bar-sep" aria-hidden="true"></div>
        <div class="vw" role="tablist" aria-label="Role filter">
          <button
            v-for="tab in ['all', 'owned', 'member'] as RoleFilter[]"
            :key="tab"
            type="button"
            class="vw-btn"
            :class="{ on: roleFilter === tab }"
            role="tab"
            :aria-selected="roleFilter === tab"
            @click="roleFilter = tab"
          >
            {{ tab === 'all' ? 'All' : tab === 'owned' ? 'Owned' : 'Member' }}
          </button>
        </div>
        <div class="bar-sep" aria-hidden="true"></div>
        <div class="vw" role="tablist" aria-label="View mode">
          <button
            type="button"
            class="vw-btn"
            :class="{ on: viewMode === 'cards' }"
            role="tab"
            :aria-selected="viewMode === 'cards'"
            @click="viewMode = 'cards'"
          >
            ▦ Cards
          </button>
          <button
            type="button"
            class="vw-btn"
            :class="{ on: viewMode === 'list' }"
            role="tab"
            :aria-selected="viewMode === 'list'"
            @click="viewMode = 'list'"
          >
            ≡ List
          </button>
        </div>
      </div>

      <div v-if="filtered.length === 0" class="filter-empty">
        No workspaces match <span class="mono">"{{ searchQuery }}"</span>.
      </div>

      <!-- Cards -->
      <div v-else-if="viewMode === 'cards'" class="card-grid">
        <router-link
          v-for="ws in filtered"
          :key="ws.slug"
          :to="`/console/${ws.slug}`"
          class="card ws-card"
        >
          <div class="ws-h">
            <div class="ws-l">
              <div class="ws-ic" :data-tone="paletteSlot(ws.slug)">{{ initials(ws.name) }}</div>
              <div class="ws-info">
                <div class="card-name">{{ ws.name }}</div>
                <div class="card-slug">{{ ws.slug }}</div>
              </div>
            </div>
            <span v-if="ws.callerRole" class="role-chip" :data-role="roleChipTone(ws.callerRole)">
              {{ ROLE_LABEL[ws.callerRole] ?? ws.callerRole }}
            </span>
          </div>
          <div class="ws-foot">
            <span class="ws-foot-l">workspace</span>
            <span v-if="ws.createdAt" class="ws-foot-r">
              created {{ formatDateShort(ws.createdAt) }}
            </span>
          </div>
        </router-link>

        <button type="button" class="card ws-card create-card" @click="openCreate">
          <span class="plus">+</span>
          <span class="create-text">
            <span class="create-title">Create a new workspace</span>
            <span class="create-sub">Group projects, environments, and members.</span>
          </span>
        </button>
      </div>

      <!-- List -->
      <div v-else class="ws-list">
        <router-link
          v-for="ws in filtered"
          :key="ws.slug"
          :to="`/console/${ws.slug}`"
          class="ws-row"
        >
          <div class="ws-ic ws-ic-sm" :data-tone="paletteSlot(ws.slug)">
            {{ initials(ws.name) }}
          </div>
          <div class="ws-row-info">
            <div class="card-name">{{ ws.name }}</div>
            <div class="card-slug">{{ ws.slug }}</div>
          </div>
          <span v-if="ws.callerRole" class="role-chip" :data-role="roleChipTone(ws.callerRole)">
            {{ ROLE_LABEL[ws.callerRole] ?? ws.callerRole }}
          </span>
          <span class="ws-row-date">
            {{ ws.createdAt ? `created ${formatDateShort(ws.createdAt)}` : '' }}
          </span>
          <span class="ws-row-arrow" aria-hidden="true">→</span>
        </router-link>
      </div>

      <PaginationControls :page="page" :total-pages="totalPages" @update:page="onPageChange" />
    </template>

    <!-- Create dialog -->
    <CreateDialog
      title="Create Workspace"
      :open="showCreate"
      :loading="createLoading"
      :error="createError"
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

<style scoped>
.workspaces-view {
  display: contents;
}

.signed-in-line {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  margin-bottom: 16px;
}

.sub-strong {
  color: var(--color-text-primary);
  font-weight: 500;
}

/* filter / view bar */
.bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
  margin: 16px 0 16px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  border-radius: var(--radius-md);
}
.bar-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--color-text-primary);
  font-family: var(--font-body);
  font-size: 13px;
  padding: 8px 12px;
  min-width: 0;
}
.bar-input::placeholder {
  color: var(--color-text-tertiary);
}
.bar-sep {
  width: 1px;
  height: 20px;
  background: var(--color-border);
}
.vw {
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin-right: 4px;
}
.vw-btn {
  padding: 5px 12px;
  background: transparent;
  border: none;
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  cursor: pointer;
  border-right: 1px solid var(--color-border);
}
.vw-btn:last-child {
  border-right: none;
}
.vw-btn:hover {
  color: var(--color-text-secondary);
}
.vw-btn.on {
  background: var(--color-bg-base);
  color: var(--color-accent);
}

.filter-empty {
  padding: 32px;
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: 13px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
}
.filter-empty .mono {
  font-family: var(--font-mono);
  color: var(--color-text-secondary);
}

/* card grid */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

.ws-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  padding: 18px 20px;
  text-decoration: none;
  color: inherit;
  transition:
    border-color 120ms ease,
    background-color 120ms ease;
  min-height: 130px;
}
.ws-card:hover {
  border-color: var(--color-border-strong);
  background: oklch(20% 0.015 80 / 0.35);
}
.ws-h {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}
.ws-l {
  display: flex;
  gap: 14px;
  align-items: center;
  min-width: 0;
}
.ws-ic {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.01em;
  flex-shrink: 0;
}
.ws-ic-sm {
  width: 32px;
  height: 32px;
  font-size: 12px;
}
.ws-ic[data-tone='a'] {
  background: var(--color-accent);
  color: oklch(15% 0.02 80);
}
.ws-ic[data-tone='b'] {
  background: oklch(22% 0.05 250);
  color: oklch(78% 0.1 250);
}
.ws-ic[data-tone='c'] {
  background: oklch(22% 0.04 150);
  color: oklch(76% 0.1 150);
}
.ws-ic[data-tone='d'] {
  background: oklch(22% 0.05 30);
  color: oklch(76% 0.1 30);
}
.ws-ic[data-tone='e'] {
  background: oklch(22% 0.05 320);
  color: oklch(76% 0.1 320);
}
.ws-ic[data-tone='f'] {
  background: oklch(20% 0.04 80);
  color: var(--color-accent);
}
.ws-info {
  min-width: 0;
}
.card-name {
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.card-slug {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ws-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  border-top: 1px solid var(--color-border);
  padding-top: 11px;
  margin-top: auto;
}
.ws-foot-l {
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

/* create card */
.create-card {
  border-style: dashed;
  background: oklch(22% 0.03 80 / 0.15);
  align-items: center;
  justify-content: center;
  flex-direction: row;
  gap: 14px;
  cursor: pointer;
  color: var(--color-text-tertiary);
  text-align: left;
  font-family: inherit;
  font-size: inherit;
}
.create-card:hover {
  border-color: var(--color-accent);
  background: oklch(20% 0.04 80 / 0.25);
  color: var(--color-text-primary);
}
.create-card .plus {
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 300;
  line-height: 1;
  color: var(--color-text-tertiary);
}
.create-card:hover .plus {
  color: var(--color-accent);
}
.create-text {
  display: flex;
  flex-direction: column;
}
.create-title {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.create-sub {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

/* list view */
.ws-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-surface);
}
.ws-row {
  display: grid;
  grid-template-columns: 32px 1fr auto auto 24px;
  gap: 14px;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  text-decoration: none;
  color: inherit;
  transition: background-color 120ms ease;
}
.ws-row:last-child {
  border-bottom: none;
}
.ws-row:hover {
  background: var(--color-bg-raised);
}
.ws-row-info {
  min-width: 0;
}
.ws-row-date {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  white-space: nowrap;
}
.ws-row-arrow {
  font-family: var(--font-mono);
  color: var(--color-text-tertiary);
  text-align: right;
}

/* role chip */
.role-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
}
.role-chip[data-role='owner'] {
  background: oklch(26% 0.06 80 / 0.5);
  color: var(--color-accent);
  border: 1px solid oklch(40% 0.1 80 / 0.4);
}
.role-chip[data-role='admin'] {
  background: oklch(24% 0.05 250 / 0.5);
  color: oklch(78% 0.1 250);
  border: 1px solid oklch(40% 0.08 250 / 0.4);
}
.role-chip[data-role='editor'] {
  background: oklch(24% 0.05 150 / 0.5);
  color: oklch(76% 0.1 150);
  border: 1px solid oklch(40% 0.08 150 / 0.4);
}
.role-chip[data-role='viewer'] {
  background: oklch(22% 0.02 80 / 0.4);
  color: var(--color-text-tertiary);
  border: 1px solid var(--color-border);
}
</style>
