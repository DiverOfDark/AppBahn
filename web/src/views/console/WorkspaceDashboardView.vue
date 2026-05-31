<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import PaginationControls from '@/components/PaginationControls.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'
import { usePageTitle } from '@/composables/usePageTitle'
import { useSidebarRefresh } from '@/composables/useSidebarRefresh'
import { formatDateShort } from '@/utils/format'
import { extractApiErrorMessage } from '@/utils/apiError'
import { initials } from '@/utils/resource'
import { statusClass } from '@/composables/resource/useResourceHelpers'

type Workspace = components['schemas']['Workspace']
type Project = components['schemas']['Project']
type ProjectStats = components['schemas']['ProjectStats']
type EnvironmentRollup = components['schemas']['EnvironmentRollup']
type WorkspaceMember = components['schemas']['WorkspaceMember']
type ViewMode = 'grid' | 'table'

const VIEW_PREF_KEY = 'appbahn.projectsView'

const route = useRoute()
const router = useRouter()

const workspace = ref<Workspace | null>(null)
const projects = ref<Project[]>([])
const statsBySlug = ref<Record<string, ProjectStats>>({})
const members = ref<WorkspaceMember[]>([])
const membersLoading = ref(true)
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

const { setPageTitle } = usePageTitle()
const { refreshSidebar } = useSidebarRefresh()
const wsSlug = ref(route.params.wsSlug as string)

function readSavedView(): ViewMode {
  if (typeof localStorage === 'undefined') return 'grid'
  return localStorage.getItem(VIEW_PREF_KEY) === 'table' ? 'table' : 'grid'
}

watch(viewMode, (m) => {
  if (typeof localStorage !== 'undefined') localStorage.setItem(VIEW_PREF_KEY, m)
})

const filteredProjects = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return projects.value
  return projects.value.filter(
    (p) => (p.name ?? '').toLowerCase().includes(q) || (p.slug ?? '').toLowerCase().includes(q),
  )
})

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
      setPageTitle(wsRes.data.name ?? wsSlug.value)
    }
    if (projRes.data) {
      projects.value = projRes.data.content ?? []
      totalPages.value = projRes.data.totalPages ?? 0
      totalElements.value = projRes.data.totalElements ?? projects.value.length
    }
  } catch {
    error.value = 'Failed to load workspace data'
  } finally {
    loading.value = false
  }
  // Stats are best-effort enrichment — a failure must not blank the project list.
  fetchStats()
}

async function fetchStats() {
  try {
    const { data } = await api.GET('/projects/stats', {
      params: { query: { workspaceSlug: wsSlug.value } },
    })
    const map: Record<string, ProjectStats> = {}
    for (const s of data ?? []) {
      if (s.slug) map[s.slug] = s
    }
    statsBySlug.value = map
  } catch {
    statsBySlug.value = {}
  }
}

function projectStats(slug?: string): ProjectStats | undefined {
  return slug ? statsBySlug.value[slug] : undefined
}

function projectEnvs(slug?: string): EnvironmentRollup[] {
  return projectStats(slug)?.envs ?? []
}

function formatUptime(pct?: number): string {
  if (pct == null) return '—'
  return `${pct.toFixed(pct >= 99.95 ? 0 : 1)}%`
}

async function fetchMembers() {
  membersLoading.value = true
  try {
    const { data } = await api.GET('/workspaces/{slug}/members', {
      params: { path: { slug: wsSlug.value } },
    })
    members.value = data ?? []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

async function createProject() {
  if (!newName.value.trim()) return
  createLoading.value = true
  createError.value = ''
  try {
    const { error: apiError } = await api.POST('/projects', {
      body: { name: newName.value.trim(), workspaceSlug: wsSlug.value },
    })
    if (apiError) {
      createError.value = extractApiErrorMessage(apiError, 'Failed to create project')
      return
    }
    showCreate.value = false
    newName.value = ''
    await fetchData()
    refreshSidebar()
  } catch {
    createError.value = 'Failed to create project'
  } finally {
    createLoading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  fetchData()
}

function openProject(slug?: string) {
  if (slug) router.push(`/console/${wsSlug.value}/${slug}`)
}

watch(
  () => route.params.wsSlug,
  (slug) => {
    if (slug && typeof slug === 'string') {
      wsSlug.value = slug
      page.value = 0
      fetchData()
      fetchMembers()
    }
  },
)

onMounted(() => {
  fetchData()
  fetchMembers()
})
</script>

<template>
  <div class="projects-view">
    <PageHeader :title="workspace?.name ?? 'Workspace'">
      <template #subtitle>
        <span v-if="!loading && projects.length > 0">
          <b class="sub-strong">{{ totalElements }}</b>
          {{ totalElements === 1 ? 'project' : 'projects' }}
          <span class="sub-sep">·</span>
          workspace <span class="sub-mono">{{ wsSlug }}</span>
        </span>
        <span v-else-if="!loading" class="sub-mono">{{ wsSlug }}</span>
      </template>
      <template #actions>
        <button class="btn-primary" @click="openCreate">+ Create Project</button>
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
        <button class="btn-primary" @click="openCreate">+ Create Project</button>
      </template>
    </EmptyState>

    <!-- Filters + grid/table -->
    <template v-else>
      <div class="filters">
        <div class="count">
          <b>{{ filteredProjects.length }}</b>
          {{ filteredProjects.length === 1 ? 'project' : 'projects' }}
          <span v-if="searchQuery" class="count-mut">of {{ projects.length }}</span>
        </div>
        <label class="search">
          <span class="search-ic">⌕</span>
          <input
            v-model="searchQuery"
            class="search-input"
            type="search"
            placeholder="Filter on this page…"
            aria-label="Filter projects"
          />
        </label>
        <div class="view-toggle" role="tablist" aria-label="View mode">
          <button
            type="button"
            class="view-btn"
            :class="{ on: viewMode === 'grid' }"
            role="tab"
            :aria-selected="viewMode === 'grid'"
            @click="viewMode = 'grid'"
          >
            Grid
          </button>
          <button
            type="button"
            class="view-btn"
            :class="{ on: viewMode === 'table' }"
            role="tab"
            :aria-selected="viewMode === 'table'"
            @click="viewMode = 'table'"
          >
            Table
          </button>
        </div>
      </div>

      <div v-if="filteredProjects.length === 0" class="filter-empty">
        No projects match <span class="mono">"{{ searchQuery }}"</span>.
      </div>

      <!-- Grid view -->
      <div v-else-if="viewMode === 'grid'" class="card-grid">
        <router-link
          v-for="proj in filteredProjects"
          :key="proj.slug"
          :to="`/console/${wsSlug}/${proj.slug}`"
          class="card"
        >
          <div class="card-h">
            <div class="card-ic">{{ initials(proj.name) }}</div>
            <div class="card-info">
              <div class="card-name">{{ proj.name }}</div>
              <div class="card-slug">{{ proj.slug }}</div>
            </div>
          </div>

          <div class="card-stats">
            <div class="stat">
              <span class="stat-val">{{ projectStats(proj.slug)?.services ?? 0 }}</span>
              <span class="stat-lbl">services</span>
            </div>
            <div class="stat">
              <span class="stat-val">{{ projectStats(proj.slug)?.deploys7d ?? 0 }}</span>
              <span class="stat-lbl">deploys 7d</span>
            </div>
            <div class="stat">
              <span class="stat-val">{{ formatUptime(projectStats(proj.slug)?.uptimePct) }}</span>
              <span class="stat-lbl">uptime</span>
            </div>
          </div>

          <div v-if="projectEnvs(proj.slug).length" class="env-pills">
            <span
              v-for="env in projectEnvs(proj.slug)"
              :key="env.slug"
              class="env-pill"
              :class="statusClass(env.status)"
              :title="`${env.slug}: ${env.status ?? 'Unknown'}`"
            >
              <span class="env-dot"></span>{{ env.slug }}
            </span>
          </div>

          <div class="card-foot">
            <span class="card-foot-l">project</span>
            <span v-if="projectStats(proj.slug)?.lastDeployAt" class="card-date">
              deployed {{ formatDateShort(projectStats(proj.slug)!.lastDeployAt!) }}
            </span>
            <span v-else-if="proj.createdAt" class="card-date">
              created {{ formatDateShort(proj.createdAt) }}
            </span>
          </div>
        </router-link>
      </div>

      <!-- Table view -->
      <div v-else class="table-wrap">
        <table class="proj-table">
          <thead>
            <tr>
              <th class="col-name">Project</th>
              <th class="col-services">Services</th>
              <th class="col-uptime">Uptime</th>
              <th class="col-envs">Environments</th>
              <th class="col-date">Last deploy</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="proj in filteredProjects"
              :key="proj.slug"
              class="proj-row"
              tabindex="0"
              @click="openProject(proj.slug)"
              @keydown="(e: KeyboardEvent) => e.key === 'Enter' && openProject(proj.slug)"
            >
              <td>
                <div class="proj-cell">
                  <div class="proj-ic">{{ initials(proj.name) }}</div>
                  <div>
                    <div class="card-name">{{ proj.name }}</div>
                    <div class="card-slug">{{ proj.slug }}</div>
                  </div>
                </div>
              </td>
              <td>
                <span class="card-date">{{ projectStats(proj.slug)?.services ?? 0 }}</span>
              </td>
              <td>
                <span class="card-date">{{
                  formatUptime(projectStats(proj.slug)?.uptimePct)
                }}</span>
              </td>
              <td>
                <div v-if="projectEnvs(proj.slug).length" class="env-pills">
                  <span
                    v-for="env in projectEnvs(proj.slug)"
                    :key="env.slug"
                    class="env-pill"
                    :class="statusClass(env.status)"
                    :title="`${env.slug}: ${env.status ?? 'Unknown'}`"
                  >
                    <span class="env-dot"></span>{{ env.slug }}
                  </span>
                </div>
                <span v-else class="card-date">—</span>
              </td>
              <td>
                <span class="card-date">
                  {{
                    projectStats(proj.slug)?.lastDeployAt
                      ? formatDateShort(projectStats(proj.slug)!.lastDeployAt!)
                      : proj.createdAt
                        ? formatDateShort(proj.createdAt)
                        : '—'
                  }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <PaginationControls :page="page" :total-pages="totalPages" @update:page="onPageChange" />
    </template>

    <!-- Members panel -->
    <section v-if="!loading && !error" class="members-panel">
      <div class="members-head">
        <h2 class="members-title">Members</h2>
        <router-link :to="`/console/${wsSlug}/settings`" class="members-manage">Manage</router-link>
      </div>

      <div v-if="membersLoading" class="members-loading">Loading members…</div>

      <div v-else-if="members.length === 0" class="members-empty">No members yet.</div>

      <ul v-else class="members-list">
        <li v-for="m in members" :key="m.userId ?? m.email" class="member-row">
          <img
            v-if="m.avatarUrl"
            :src="m.avatarUrl"
            :alt="m.name ?? m.email ?? 'member'"
            class="member-avatar"
          />
          <div v-else class="member-avatar member-avatar-fallback">
            {{ initials(m.name ?? m.email) }}
          </div>
          <div class="member-info">
            <div class="member-name">{{ m.name ?? m.email }}</div>
            <div v-if="m.name && m.email" class="member-email">{{ m.email }}</div>
          </div>
          <span class="member-role" :class="`role-${(m.role ?? '').toLowerCase()}`">
            {{ m.role }}
          </span>
        </li>
      </ul>
    </section>

    <!-- Create dialog -->
    <CreateDialog
      title="Create Project"
      :open="showCreate"
      :loading="createLoading"
      :error="createError"
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

<style scoped>
.projects-view {
  display: contents;
}

.sub-strong {
  color: var(--color-text-primary);
  font-weight: 500;
}
.sub-sep {
  margin: 0 6px;
  color: var(--color-text-tertiary);
}
.sub-mono {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
}

/* filters bar */
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0 14px;
  flex-wrap: wrap;
}
.count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  flex-shrink: 0;
}
.count b {
  color: var(--color-text-primary);
  font-weight: 500;
}
.count-mut {
  color: var(--color-text-tertiary);
  margin-left: 6px;
  text-transform: none;
  letter-spacing: 0;
  font-family: var(--font-body);
}
.search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 320px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  padding: 6px 12px;
  color: var(--color-text-tertiary);
}
.search:focus-within {
  border-color: var(--color-accent-muted);
}
.search-ic {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.search-input {
  background: transparent;
  border: none;
  color: var(--color-text-primary);
  outline: none;
  flex: 1;
  font-family: var(--font-body);
  font-size: 13px;
  padding: 0;
  min-width: 0;
}
.search-input::-webkit-search-cancel-button {
  filter: grayscale(1);
  opacity: 0.6;
}
.view-toggle {
  display: flex;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-left: auto;
}
.view-btn {
  padding: 6px 14px;
  background: var(--color-bg-surface);
  color: var(--color-text-tertiary);
  border: none;
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  border-right: 1px solid var(--color-border-strong);
}
.view-btn:last-child {
  border-right: none;
}
.view-btn:hover {
  color: var(--color-text-secondary);
}
.view-btn.on {
  background: var(--color-bg-raised);
  color: var(--color-text-primary);
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

/* grid + cards */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}
.card {
  display: block;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  padding: 0;
  overflow: hidden;
  text-decoration: none;
  transition:
    border-color 120ms ease,
    transform 120ms ease;
}
.card:hover {
  border-color: var(--color-border-strong);
  transform: translateY(-1px);
}
.card-h {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 18px 20px 14px;
}
.card-ic {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
  flex-shrink: 0;
}
.card-info {
  min-width: 0;
  flex: 1;
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
  margin: 0 0 2px;
}
.card-slug {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
}
.card-stats {
  display: flex;
  gap: 6px;
  padding: 0 20px 14px;
}
.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}
.stat-val {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.1;
}
.stat-lbl {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.env-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 20px 14px;
}
.env-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.04em;
  padding: 2px 8px;
  border-radius: 2px;
  border: 1px solid currentColor;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.env-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.card-foot {
  padding: 10px 20px;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-base);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.card-foot-l {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.card-date {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
}

/* table view */
.table-wrap {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-surface);
}
.proj-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.proj-table th {
  padding: 11px 16px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 500;
  text-align: left;
  border-bottom: 1px solid var(--color-border-strong);
  background: var(--color-bg-surface);
}
.proj-table td {
  padding: 13px 16px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-weight: 300;
  vertical-align: middle;
}
.proj-table tbody tr:last-child td {
  border-bottom: none;
}
.proj-row {
  cursor: pointer;
  transition: background-color 120ms ease;
}
.proj-row:hover td,
.proj-row:focus-visible td {
  background: var(--color-bg-raised);
}
.proj-row:focus-visible {
  outline: none;
}
.proj-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.proj-ic {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-accent);
  flex-shrink: 0;
}
.col-date {
  width: 160px;
}
.col-services,
.col-uptime {
  width: 100px;
}
.col-envs {
  width: 280px;
}

/* members panel */
.members-panel {
  margin-top: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  overflow: hidden;
}
.members-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-base);
}
.members-title {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
  margin: 0;
}
.members-manage {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--color-accent);
  text-decoration: none;
}
.members-manage:hover {
  color: var(--color-accent-hover);
}
.members-loading,
.members-empty {
  padding: 20px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
.members-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.member-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border);
}
.member-row:last-child {
  border-bottom: none;
}
.member-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex-shrink: 0;
  object-fit: cover;
}
.member-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  font-family: var(--font-heading);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-accent);
}
.member-info {
  min-width: 0;
  flex: 1;
}
.member-name {
  font-size: 14px;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.member-email {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.02em;
}
.member-role {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-strong);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.member-role.role-owner {
  color: var(--color-accent);
  border-color: var(--color-accent-muted);
}
</style>
