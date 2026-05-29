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
import { useUserPreferences } from '@/composables/useUserPreferences'
import { formatDateShort, formatRelativeTime } from '@/utils/format'
import { extractApiErrorMessage } from '@/utils/apiError'
import { initials } from '@/utils/resource'
import { useRouter } from 'vue-router'

type Workspace = components['schemas']['Workspace']
type WorkspaceInvite = components['schemas']['WorkspaceInvite']
type WorkspaceStats = components['schemas']['WorkspaceStats']
type ViewMode = 'cards' | 'list'
type RoleFilter = 'all' | 'owned' | 'member'

const VIEW_PREF_KEY = 'appbahn.workspacesView'

const { preferences: userPrefs, fetch: fetchPrefs, setDefaultWorkspace } = useUserPreferences()
const router = useRouter()

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
const pinning = ref<string | null>(null)
const { refreshSidebar } = useSidebarRefresh()
const { user, fetch: fetchCurrentUser } = useCurrentUser()

const userLabel = computed(() => user.value?.name ?? user.value?.email ?? null)

const defaultWorkspace = computed<Workspace | undefined>(() => {
  const slug = userPrefs.value?.defaultWorkspaceSlug
  if (!slug) return undefined
  return workspaces.value.find((w) => w.slug === slug)
})

// --- Pending invites ---
const pendingInvites = ref<WorkspaceInvite[]>([])
const inviteActionLoading = ref<string | null>(null)

// --- Per-card stats (bulk-stats endpoint) ---
const workspaceStats = ref<Record<string, WorkspaceStats>>({})
function statsFor(slug?: string): WorkspaceStats | undefined {
  if (!slug) return undefined
  return workspaceStats.value[slug]
}

// --- Join with code ---
const showJoinCode = ref(false)
const joinCode = ref('')
const joinCodeLoading = ref(false)
const joinCodeError = ref('')

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
    const [wsRes, invRes] = await Promise.all([
      api.GET('/workspaces', { params: { query: { page: page.value, size: 20 } } }),
      api.GET('/users/me/invites'),
    ])
    if (wsRes.data) {
      workspaces.value = wsRes.data.content ?? []
      totalPages.value = wsRes.data.totalPages ?? 0
      totalElements.value = wsRes.data.totalElements ?? workspaces.value.length
    }
    if (invRes.data) {
      pendingInvites.value = invRes.data
    }
  } catch {
    error.value = 'Failed to load workspaces'
  } finally {
    loading.value = false
  }
  // Fire-and-forget: stats are decorative; failure leaves cards rendering
  // without the grid rather than blocking the whole page.
  fetchStats()
}

async function fetchStats() {
  const slugs = workspaces.value.map((w) => w.slug).filter((s): s is string => !!s)
  if (slugs.length === 0) {
    workspaceStats.value = {}
    return
  }
  try {
    const { data } = await api.GET('/workspaces/stats', { params: { query: { slugs } } })
    if (!data) return
    const next: Record<string, WorkspaceStats> = {}
    for (const s of data) {
      if (s.slug) next[s.slug] = s
    }
    workspaceStats.value = next
  } catch {
    // graceful degradation — cards keep rendering without the stats grid
  }
}

async function acceptInvite(inviteId: string) {
  inviteActionLoading.value = inviteId
  try {
    const { data, error: apiError } = await api.POST('/invites/{id}/accept', {
      params: { path: { id: inviteId } },
    })
    if (apiError) {
      error.value = extractApiErrorMessage(apiError, 'Failed to accept invitation')
      return
    }
    pendingInvites.value = pendingInvites.value.filter((i) => i.id !== inviteId)
    await fetchWorkspaces()
    refreshSidebar()
    if (data?.workspaceSlug) {
      router.push(`/console/${data.workspaceSlug}`)
    }
  } catch {
    error.value = 'Failed to accept invitation'
  } finally {
    inviteActionLoading.value = null
  }
}

async function declineInvite(inviteId: string) {
  inviteActionLoading.value = inviteId
  try {
    const { error: apiError } = await api.POST('/invites/{id}/decline', {
      params: { path: { id: inviteId } },
    })
    if (apiError) {
      error.value = extractApiErrorMessage(apiError, 'Failed to decline invitation')
      return
    }
    pendingInvites.value = pendingInvites.value.filter((i) => i.id !== inviteId)
  } catch {
    error.value = 'Failed to decline invitation'
  } finally {
    inviteActionLoading.value = null
  }
}

function closeJoinDialog() {
  showJoinCode.value = false
  joinCode.value = ''
  joinCodeError.value = ''
}

async function redeemCode() {
  if (!joinCode.value.trim()) return
  joinCodeLoading.value = true
  joinCodeError.value = ''
  try {
    const { data, error: apiError } = await api.POST('/invites/redeem', {
      body: { code: joinCode.value.trim() },
    })
    if (apiError) {
      joinCodeError.value = extractApiErrorMessage(apiError, 'Failed to redeem code')
      return
    }
    showJoinCode.value = false
    joinCode.value = ''
    await fetchWorkspaces()
    refreshSidebar()
    if (data?.workspaceSlug) {
      router.push(`/console/${data.workspaceSlug}`)
    }
  } catch {
    joinCodeError.value = 'Failed to redeem invite code'
  } finally {
    joinCodeLoading.value = false
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

async function pinWorkspace(slug: string | null) {
  if (pinning.value) return
  pinning.value = slug ?? ''
  try {
    await setDefaultWorkspace(slug)
  } finally {
    pinning.value = null
  }
}

onMounted(() => {
  fetchWorkspaces()
  fetchCurrentUser()
  fetchPrefs()
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
        <button class="btn-secondary" @click="showJoinCode = true">Join with code</button>
        <button class="btn-primary" @click="openCreate">+ Create Workspace</button>
      </template>
    </PageHeader>

    <!-- Pending invitations -->
    <template v-if="pendingInvites.length > 0">
      <div class="invites-section">
        <div class="invites-label">Pending invitations</div>
        <div class="invites-list">
          <div v-for="inv in pendingInvites" :key="inv.id" class="invite-card">
            <div class="invite-body">
              <div class="invite-ws">{{ inv.workspaceName }}</div>
              <div class="invite-meta">
                <span v-if="inv.invitedBy" class="invite-by">
                  Invited by {{ inv.invitedBy.name || inv.invitedBy.email }}
                </span>
                <span class="invite-role">{{ inv.role }}</span>
                <span v-if="inv.expiresAt" class="invite-exp">
                  expires {{ formatDateShort(inv.expiresAt) }}
                </span>
              </div>
            </div>
            <div class="invite-actions">
              <button
                class="btn-primary btn-sm"
                :disabled="inviteActionLoading === inv.id"
                @click="acceptInvite(inv.id!)"
              >
                Accept
              </button>
              <button
                class="btn-secondary btn-sm"
                :disabled="inviteActionLoading === inv.id"
                @click="declineInvite(inv.id!)"
              >
                Decline
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>

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
      <!-- Default workspace banner -->
      <div v-if="defaultWorkspace" class="default-banner">
        <div class="default-banner-label">Default workspace</div>
        <div class="default-banner-body">
          <div class="ws-ic default-banner-ic" :data-tone="paletteSlot(defaultWorkspace.slug)">
            {{ initials(defaultWorkspace.name) }}
          </div>
          <div class="default-banner-info">
            <div class="card-name">{{ defaultWorkspace.name }}</div>
            <div class="card-slug">{{ defaultWorkspace.slug }}</div>
          </div>
          <div class="default-banner-actions">
            <router-link :to="`/console/${defaultWorkspace.slug}`" class="btn-primary">
              Open
            </router-link>
            <router-link :to="`/console/${defaultWorkspace.slug}/settings`" class="btn-secondary">
              Settings
            </router-link>
            <button
              type="button"
              class="btn-ghost"
              :disabled="pinning !== null"
              @click="pinWorkspace(null)"
            >
              Unpin
            </button>
          </div>
        </div>
      </div>

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
          <div class="ws-stats" data-testid="ws-stats" aria-label="Workspace statistics">
            <div class="ws-stat">
              <div class="ws-stat-value">{{ statsFor(ws.slug)?.projectCount ?? '–' }}</div>
              <div class="ws-stat-label">Projects</div>
            </div>
            <div class="ws-stat">
              <div class="ws-stat-value">{{ statsFor(ws.slug)?.resourceCount ?? '–' }}</div>
              <div class="ws-stat-label">Resources</div>
            </div>
            <div class="ws-stat">
              <div class="ws-stat-value">{{ statsFor(ws.slug)?.clusterCount ?? '–' }}</div>
              <div class="ws-stat-label">Clusters</div>
            </div>
            <div class="ws-stat">
              <div class="ws-stat-value">{{ statsFor(ws.slug)?.memberCount ?? '–' }}</div>
              <div class="ws-stat-label">Members</div>
            </div>
          </div>
          <div class="ws-foot">
            <span v-if="statsFor(ws.slug)?.lastEventAt" class="ws-foot-l ws-activity">
              <span class="ws-activity-dot" aria-hidden="true"></span>
              last activity {{ formatRelativeTime(statsFor(ws.slug)?.lastEventAt) }}
            </span>
            <span v-else class="ws-foot-l">workspace</span>
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
        <div v-for="ws in filtered" :key="ws.slug" class="ws-row-wrap">
          <router-link :to="`/console/${ws.slug}`" class="ws-row">
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
            <span class="ws-row-counts" data-testid="ws-row-counts" aria-label="Counts">
              <span class="ws-row-count" title="Projects">
                <span class="ws-row-count-v">{{ statsFor(ws.slug)?.projectCount ?? '–' }}</span>
                <span class="ws-row-count-l">P</span>
              </span>
              <span class="ws-row-count" title="Resources">
                <span class="ws-row-count-v">{{ statsFor(ws.slug)?.resourceCount ?? '–' }}</span>
                <span class="ws-row-count-l">R</span>
              </span>
              <span class="ws-row-count" title="Clusters">
                <span class="ws-row-count-v">{{ statsFor(ws.slug)?.clusterCount ?? '–' }}</span>
                <span class="ws-row-count-l">C</span>
              </span>
              <span class="ws-row-count" title="Members">
                <span class="ws-row-count-v">{{ statsFor(ws.slug)?.memberCount ?? '–' }}</span>
                <span class="ws-row-count-l">M</span>
              </span>
            </span>
            <span class="ws-row-date">
              <template v-if="statsFor(ws.slug)?.lastEventAt">
                {{ formatRelativeTime(statsFor(ws.slug)?.lastEventAt) }}
              </template>
              <template v-else-if="ws.createdAt">
                created {{ formatDateShort(ws.createdAt) }}
              </template>
            </span>
            <span class="ws-row-arrow" aria-hidden="true">→</span>
          </router-link>
          <button
            type="button"
            class="ws-row-pin"
            :class="{ pinned: userPrefs?.defaultWorkspaceSlug === ws.slug }"
            :disabled="pinning !== null"
            :title="
              userPrefs?.defaultWorkspaceSlug === ws.slug ? 'Unpin default' : 'Pin as default'
            "
            @click="
              pinWorkspace(userPrefs?.defaultWorkspaceSlug === ws.slug ? null : (ws.slug ?? null))
            "
          >
            {{ userPrefs?.defaultWorkspaceSlug === ws.slug ? '★' : '☆' }}
          </button>
        </div>
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

    <!-- Join with code dialog -->
    <CreateDialog
      title="Join with invite code"
      :open="showJoinCode"
      :loading="joinCodeLoading"
      :error="joinCodeError"
      @close="closeJoinDialog"
      @submit="redeemCode"
    >
      <label class="form-label">
        Invite Code
        <input
          v-model="joinCode"
          class="form-input"
          type="text"
          placeholder="e.g. abp_xY3…"
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
  padding-top: 8px;
  margin-top: auto;
  border-top: 1px solid var(--color-border);
}
/* stats grid sits between ws-h and ws-foot; suppress the duplicate border-top */
.ws-stats + .ws-foot {
  border-top: none;
  padding-top: 0;
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
.ws-row-wrap {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid var(--color-border);
}
.ws-row-wrap:last-child {
  border-bottom: none;
}
.ws-row {
  display: grid;
  grid-template-columns: 32px 1fr auto auto auto 24px;
  gap: 14px;
  align-items: center;
  padding: 12px 16px;
  text-decoration: none;
  color: inherit;
  transition: background-color 120ms ease;
  flex: 1;
  min-width: 0;
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

/* per-card stats grid */
.ws-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);
}
.ws-stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}
.ws-stat-value {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
  line-height: 1.1;
}
.ws-stat-label {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

/* last-activity decoration */
.ws-activity {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-transform: none;
  letter-spacing: 0.04em;
  font-size: 11px;
}
.ws-activity-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-accent);
  display: inline-block;
  flex-shrink: 0;
}

/* compact stats in list view */
.ws-row-counts {
  display: inline-flex;
  gap: 12px;
  align-items: baseline;
  flex-shrink: 0;
}
.ws-row-count {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  font-family: var(--font-mono);
}
.ws-row-count-v {
  font-size: 13px;
  color: var(--color-text-primary);
  font-weight: 500;
}
.ws-row-count-l {
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
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

.ws-row-pin {
  border: none;
  border-left: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-tertiary);
  font-size: 16px;
  padding: 0 14px;
  cursor: pointer;
  transition:
    color 120ms ease,
    background-color 120ms ease;
  flex-shrink: 0;
}
.ws-row-pin:hover:not(:disabled) {
  color: var(--color-accent);
  background: var(--color-bg-raised);
}
.ws-row-pin.pinned {
  color: var(--color-accent);
}
.ws-row-pin:disabled {
  opacity: 0.4;
  cursor: default;
}

/* default workspace banner */
.default-banner {
  border: 1px solid var(--color-accent);
  border-radius: var(--radius-md);
  background: oklch(20% 0.04 80 / 0.15);
  padding: 16px 20px;
  margin-bottom: 16px;
}
.default-banner-label {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-accent);
  margin-bottom: 10px;
}
.default-banner-body {
  display: flex;
  align-items: center;
  gap: 16px;
}
.default-banner-ic {
  flex-shrink: 0;
}
.default-banner-info {
  flex: 1;
  min-width: 0;
}
.default-banner-actions,
.invite-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* pending invitations */
.invites-section {
  margin-bottom: 24px;
}
.invites-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
  margin-bottom: 10px;
}
.invites-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.invite-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
}
.invite-body {
  min-width: 0;
}
.invite-ws {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.invite-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 4px;
  flex-wrap: wrap;
}
.invite-by,
.invite-exp {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.03em;
}
.invite-role {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-accent);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.btn-sm {
  padding: 5px 12px;
  font-size: 12px;
}
</style>
