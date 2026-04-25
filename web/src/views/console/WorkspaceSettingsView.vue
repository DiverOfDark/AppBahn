<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import DataTable from '@/components/DataTable.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import PaginationControls from '@/components/PaginationControls.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import ConfirmButton from '@/components/ConfirmButton.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'
import { formatDate } from '@/utils/format'
import { usePageTitle } from '@/composables/usePageTitle'
import { useSidebarRefresh } from '@/composables/useSidebarRefresh'

type Workspace = components['schemas']['Workspace']
type WorkspaceMember = components['schemas']['WorkspaceMember']
type OidcGroupMapping = components['schemas']['OidcGroupMapping']
type Quota = components['schemas']['Quota']
type RegistryConfig = components['schemas']['RegistryConfig']
type AuditLogEntry = components['schemas']['AuditLogEntry']

const ROLES = ['OWNER', 'ADMIN', 'EDITOR', 'VIEWER'] as const
type Role = (typeof ROLES)[number]

const TABS = ['members', 'group-mappings', 'quotas', 'registry', 'security', 'audit-log'] as const
type Tab = (typeof TABS)[number]

const TAB_LABELS: Record<Tab, string> = {
  members: 'Members',
  'group-mappings': 'Group Mappings',
  quotas: 'Quotas',
  registry: 'Registry',
  security: 'Security',
  'audit-log': 'Audit Log',
}

const route = useRoute()
const { setPageTitle } = usePageTitle()
const { refreshSidebar } = useSidebarRefresh()
const wsSlug = ref(route.params.wsSlug as string)

const workspace = ref<Workspace | null>(null)
const activeTab = ref<Tab>('members')
const loading = ref(true)
const error = ref('')
const saving = ref(false)
const saveSuccess = ref('')

// -- Members --
const members = ref<WorkspaceMember[]>([])
const showInvite = ref(false)
const inviteLoading = ref(false)
const inviteEmail = ref('')
const inviteRole = ref<Role>('VIEWER')
const inviteResult = ref<string | null>(null)

// -- Group Mappings --
const groupMappings = ref<OidcGroupMapping[]>([])
const showAddMapping = ref(false)
const addMappingLoading = ref(false)
const newMappingGroup = ref('')
const newMappingRole = ref<Role>('VIEWER')

// -- Quotas --
const quota = ref<Quota>({
  maxCpuCores: undefined,
  maxMemoryMb: undefined,
  maxStorageGb: undefined,
  maxResources: undefined,
})

// -- Registry --
const registry = ref<RegistryConfig>({ url: '', credentialRef: '' })

// -- Security --
const runtimeClassName = ref('')

// -- Audit Log --
const auditEntries = ref<AuditLogEntry[]>([])
const auditPage = ref(0)
const auditTotalPages = ref(0)

// ── Fetch functions ──────────────────────────────────────────────────

async function fetchWorkspace() {
  try {
    const { data } = await api.GET('/workspaces/{slug}', {
      params: { path: { slug: wsSlug.value } },
    })
    if (data) {
      workspace.value = data
      setPageTitle(data.name ?? wsSlug.value, 'Settings')
      runtimeClassName.value = data.runtimeClassName ?? ''
      if (data.registry) {
        registry.value = { ...data.registry }
      }
    }
  } catch {
    error.value = 'Failed to load workspace'
  }
}

async function fetchMembers() {
  try {
    const { data } = await api.GET('/workspaces/{slug}/members', {
      params: { path: { slug: wsSlug.value } },
    })
    if (data) {
      members.value = data
    }
  } catch {
    error.value = 'Failed to load members'
  }
}

async function fetchGroupMappings() {
  try {
    const { data } = await api.GET('/workspaces/{slug}/group-mappings', {
      params: { path: { slug: wsSlug.value } },
    })
    if (data) {
      groupMappings.value = data
    }
  } catch {
    error.value = 'Failed to load group mappings'
  }
}

async function fetchQuota() {
  try {
    const { data } = await api.GET('/workspaces/{slug}/quota', {
      params: { path: { slug: wsSlug.value } },
    })
    if (data) {
      quota.value = { ...data }
    }
  } catch {
    error.value = 'Failed to load quota'
  }
}

async function fetchAuditLog() {
  try {
    const { data } = await api.GET('/workspaces/{slug}/audit-log', {
      params: {
        path: { slug: wsSlug.value },
        query: { page: auditPage.value, size: 20 },
      },
    })
    if (data) {
      auditEntries.value = data.content ?? []
      auditTotalPages.value = data.totalPages ?? 0
    }
  } catch {
    error.value = 'Failed to load audit log'
  }
}

async function fetchTabData() {
  error.value = ''
  loading.value = true
  try {
    await fetchWorkspace()
    switch (activeTab.value) {
      case 'members':
        await fetchMembers()
        break
      case 'group-mappings':
        await fetchGroupMappings()
        break
      case 'quotas':
        await fetchQuota()
        break
      case 'audit-log':
        await fetchAuditLog()
        break
      // registry/security load via fetchWorkspace, no per-tab fetch needed.
    }
  } finally {
    loading.value = false
  }
}

// ── Member actions ───────────────────────────────────────────────────

async function inviteMember() {
  if (!inviteEmail.value.trim()) return
  inviteLoading.value = true
  inviteResult.value = null
  try {
    const { data } = await api.POST('/workspaces/{slug}/members', {
      params: { path: { slug: wsSlug.value } },
      body: { email: inviteEmail.value.trim(), role: inviteRole.value },
    })
    if (data?.status === 'PENDING') {
      inviteResult.value = 'pending'
    }
    showInvite.value = false
    inviteEmail.value = ''
    inviteRole.value = 'VIEWER'
    await fetchMembers()
  } catch {
    error.value = 'Failed to invite member'
  } finally {
    inviteLoading.value = false
  }
}

async function updateMemberRole(userId: string, role: Role) {
  try {
    await api.PATCH('/workspaces/{slug}/members/{user_id}', {
      params: { path: { slug: wsSlug.value, user_id: userId } },
      body: { role },
    })
    await fetchMembers()
  } catch {
    error.value = 'Failed to update member role'
  }
}

async function removeMember(userId: string) {
  try {
    await api.DELETE('/workspaces/{slug}/members/{user_id}', {
      params: { path: { slug: wsSlug.value, user_id: userId } },
    })
    await fetchMembers()
  } catch {
    error.value = 'Failed to remove member'
  }
}

// ── Group Mapping actions ────────────────────────────────────────────

async function addGroupMapping() {
  if (!newMappingGroup.value.trim()) return
  addMappingLoading.value = true
  try {
    await api.POST('/workspaces/{slug}/group-mappings', {
      params: { path: { slug: wsSlug.value } },
      body: { oidcGroup: newMappingGroup.value.trim(), role: newMappingRole.value },
    })
    showAddMapping.value = false
    newMappingGroup.value = ''
    newMappingRole.value = 'VIEWER'
    await fetchGroupMappings()
  } catch {
    error.value = 'Failed to create group mapping'
  } finally {
    addMappingLoading.value = false
  }
}

async function updateMappingRole(mappingId: string, role: Role) {
  try {
    await api.PATCH('/workspaces/{slug}/group-mappings/{mapping_id}', {
      params: { path: { slug: wsSlug.value, mapping_id: mappingId } },
      body: { role },
    })
    await fetchGroupMappings()
  } catch {
    error.value = 'Failed to update group mapping'
  }
}

async function deleteMapping(mappingId: string) {
  try {
    await api.DELETE('/workspaces/{slug}/group-mappings/{mapping_id}', {
      params: { path: { slug: wsSlug.value, mapping_id: mappingId } },
    })
    await fetchGroupMappings()
  } catch {
    error.value = 'Failed to delete group mapping'
  }
}

// ── Quota actions ────────────────────────────────────────────────────

async function saveQuota() {
  saving.value = true
  saveSuccess.value = ''
  try {
    await api.PATCH('/workspaces/{slug}/quota', {
      params: { path: { slug: wsSlug.value } },
      body: quota.value,
    })
    saveSuccess.value = 'Quota saved.'
  } catch {
    error.value = 'Failed to save quota'
  } finally {
    saving.value = false
  }
}

// ── Registry actions ─────────────────────────────────────────────────

async function saveRegistry() {
  saving.value = true
  saveSuccess.value = ''
  try {
    await api.PUT('/workspaces/{slug}/registry', {
      params: { path: { slug: wsSlug.value } },
      body: registry.value,
    })
    saveSuccess.value = 'Registry configuration saved.'
  } catch {
    error.value = 'Failed to save registry configuration'
  } finally {
    saving.value = false
  }
}

// ── Security actions ─────────────────────────────────────────────────

async function saveSecurity() {
  saving.value = true
  saveSuccess.value = ''
  try {
    await api.PATCH('/workspaces/{slug}', {
      params: { path: { slug: wsSlug.value } },
      body: { name: workspace.value?.name },
    })
    await fetchWorkspace()
    refreshSidebar()
    saveSuccess.value = 'Security settings saved.'
  } catch {
    error.value = 'Failed to save security settings'
  } finally {
    saving.value = false
  }
}

// ── Audit log pagination ─────────────────────────────────────────────

function onAuditPageChange(p: number) {
  auditPage.value = p
  fetchAuditLog()
}

// ── Tab switching ────────────────────────────────────────────────────

function switchTab(tab: Tab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  saveSuccess.value = ''
  error.value = ''
  fetchTabData()
}

// ── Lifecycle ────────────────────────────────────────────────────────

watch(
  () => route.params.wsSlug,
  (slug) => {
    if (slug && typeof slug === 'string') {
      wsSlug.value = slug
      fetchTabData()
    }
  },
)

onMounted(fetchTabData)
</script>

<template>
  <div>
    <PageHeader :title="`${workspace?.name ?? 'Workspace'} Settings`" />

    <AppBreadcrumb :items="buildBreadcrumbChain({ wsSlug }, 'Settings', true)" />

    <!-- Tab bar -->
    <div class="tab-bar">
      <button
        v-for="tab in TABS"
        :key="tab"
        class="tab-btn"
        :class="{ 'tab-btn--active': activeTab === tab }"
        @click="switchTab(tab)"
      >
        {{ TAB_LABELS[tab] }}
      </button>
    </div>

    <!-- Success message -->
    <div v-if="saveSuccess" class="success-banner">
      {{ saveSuccess }}
    </div>

    <!-- Error -->
    <div v-if="error" class="error-state">
      <p>{{ error }}</p>
      <button class="btn-secondary" @click="error = ''">Dismiss</button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <p>Loading...</p>
    </div>

    <!-- ═══════════════ MEMBERS TAB ═══════════════ -->
    <template v-else-if="activeTab === 'members'">
      <div class="tab-header">
        <h2 class="section-title">Members</h2>
        <button class="btn-primary" @click="showInvite = true">+ Invite Member</button>
      </div>

      <div v-if="inviteResult === 'pending'" class="info-banner">
        Invitation sent. The user will appear as a member once they accept.
      </div>

      <EmptyState v-if="members.length === 0" message="No members yet." />

      <DataTable v-else>
        <template #header>
          <th>Email</th>
          <th>Role</th>
          <th>Actions</th>
        </template>
        <template #body>
          <tr v-for="member in members" :key="member.userId">
            <td class="cell-name">{{ member.email }}</td>
            <td>
              <select
                class="form-input role-select"
                :value="member.role"
                @change="
                  updateMemberRole(
                    member.userId!,
                    ($event.target as HTMLSelectElement).value as Role,
                  )
                "
              >
                <option v-for="r in ROLES" :key="r" :value="r">{{ r }}</option>
              </select>
            </td>
            <td>
              <ConfirmButton
                label="Remove"
                confirm-label="Confirm Remove"
                btn-class="btn-danger btn-sm"
                @confirm="removeMember(member.userId!)"
              />
            </td>
          </tr>
        </template>
      </DataTable>

      <CreateDialog
        title="Invite Member"
        :open="showInvite"
        :loading="inviteLoading"
        @close="showInvite = false"
        @submit="inviteMember"
      >
        <div class="form-stack">
          <label class="form-label">
            Email
            <input
              v-model="inviteEmail"
              class="form-input"
              type="email"
              placeholder="user@example.com"
              autofocus
            />
          </label>
          <label class="form-label">
            Role
            <select v-model="inviteRole" class="form-input">
              <option v-for="r in ROLES" :key="r" :value="r">{{ r }}</option>
            </select>
          </label>
        </div>
      </CreateDialog>
    </template>

    <!-- ═══════════════ GROUP MAPPINGS TAB ═══════════════ -->
    <template v-else-if="activeTab === 'group-mappings'">
      <div class="tab-header">
        <h2 class="section-title">OIDC Group Mappings</h2>
        <button class="btn-primary" @click="showAddMapping = true">+ Add Mapping</button>
      </div>

      <EmptyState v-if="groupMappings.length === 0" message="No group mappings configured." />

      <DataTable v-else>
        <template #header>
          <th>OIDC Group</th>
          <th>Role</th>
          <th>Actions</th>
        </template>
        <template #body>
          <tr v-for="mapping in groupMappings" :key="mapping.id">
            <td class="cell-name">{{ mapping.oidcGroup }}</td>
            <td>
              <select
                class="form-input role-select"
                :value="mapping.role"
                @change="
                  updateMappingRole(mapping.id!, ($event.target as HTMLSelectElement).value as Role)
                "
              >
                <option v-for="r in ROLES" :key="r" :value="r">{{ r }}</option>
              </select>
            </td>
            <td>
              <ConfirmButton btn-class="btn-danger btn-sm" @confirm="deleteMapping(mapping.id!)" />
            </td>
          </tr>
        </template>
      </DataTable>

      <CreateDialog
        title="Add Group Mapping"
        :open="showAddMapping"
        :loading="addMappingLoading"
        @close="showAddMapping = false"
        @submit="addGroupMapping"
      >
        <div class="form-stack">
          <label class="form-label">
            OIDC Group Name
            <input
              v-model="newMappingGroup"
              class="form-input"
              type="text"
              placeholder="e.g. engineering-team"
              autofocus
            />
          </label>
          <label class="form-label">
            Role
            <select v-model="newMappingRole" class="form-input">
              <option v-for="r in ROLES" :key="r" :value="r">{{ r }}</option>
            </select>
          </label>
        </div>
      </CreateDialog>
    </template>

    <!-- ═══════════════ QUOTAS TAB ═══════════════ -->
    <template v-else-if="activeTab === 'quotas'">
      <h2 class="section-title">Workspace Quotas</h2>

      <form class="settings-form" @submit.prevent="saveQuota">
        <label class="form-label">
          Max CPU Cores
          <input
            v-model.number="quota.maxCpuCores"
            class="form-input"
            type="number"
            step="0.5"
            min="0"
          />
        </label>
        <label class="form-label">
          Max Memory (MB)
          <input v-model.number="quota.maxMemoryMb" class="form-input" type="number" min="0" />
        </label>
        <label class="form-label">
          Max Storage (GB)
          <input v-model.number="quota.maxStorageGb" class="form-input" type="number" min="0" />
        </label>
        <label class="form-label">
          Max Resources
          <input v-model.number="quota.maxResources" class="form-input" type="number" min="0" />
        </label>
        <div class="form-actions">
          <button type="submit" class="btn-primary" :disabled="saving">
            {{ saving ? 'Saving...' : 'Save Quotas' }}
          </button>
        </div>
      </form>
    </template>

    <!-- ═══════════════ REGISTRY TAB ═══════════════ -->
    <template v-else-if="activeTab === 'registry'">
      <h2 class="section-title">Container Registry</h2>

      <form class="settings-form" @submit.prevent="saveRegistry">
        <label class="form-label">
          Registry URL
          <input
            v-model="registry.url"
            class="form-input"
            type="text"
            placeholder="e.g. registry.example.com/appbahn"
          />
        </label>
        <label class="form-label">
          Credential Ref
          <input
            v-model="registry.credentialRef"
            class="form-input"
            type="text"
            placeholder="e.g. registry-secret"
          />
        </label>
        <div class="form-actions">
          <button type="submit" class="btn-primary" :disabled="saving">
            {{ saving ? 'Saving...' : 'Save Registry' }}
          </button>
        </div>
      </form>
    </template>

    <!-- ═══════════════ SECURITY TAB ═══════════════ -->
    <template v-else-if="activeTab === 'security'">
      <h2 class="section-title">Security Settings</h2>

      <form class="settings-form" @submit.prevent="saveSecurity">
        <label class="form-label">
          Runtime Class Name
          <input
            v-model="runtimeClassName"
            class="form-input"
            type="text"
            placeholder="e.g. gvisor"
          />
        </label>
        <p class="form-hint">
          Sets the Kubernetes runtimeClassName for all pods in this workspace. Leave empty for the
          cluster default.
        </p>
        <div class="form-actions">
          <button type="submit" class="btn-primary" :disabled="saving">
            {{ saving ? 'Saving...' : 'Save Security' }}
          </button>
        </div>
      </form>
    </template>

    <!-- ═══════════════ AUDIT LOG TAB ═══════════════ -->
    <template v-else-if="activeTab === 'audit-log'">
      <h2 class="section-title">Audit Log</h2>

      <EmptyState v-if="auditEntries.length === 0" message="No audit log entries found." />

      <DataTable v-else>
        <template #header>
          <th>Timestamp</th>
          <th>Actor</th>
          <th>Action</th>
          <th>Target</th>
        </template>
        <template #body>
          <tr v-for="entry in auditEntries" :key="entry.id">
            <td class="cell-date">{{ formatDate(entry.timestamp) }}</td>
            <td class="cell-name">{{ entry.actorEmail ?? entry.actorSource ?? '--' }}</td>
            <td>
              <span class="action-badge">{{ entry.action }}</span>
            </td>
            <td class="cell-slug">
              {{ entry.targetType }}{{ entry.targetId ? `: ${entry.targetId}` : '' }}
            </td>
          </tr>
        </template>
      </DataTable>

      <PaginationControls
        :page="auditPage"
        :total-pages="auditTotalPages"
        @update:page="onAuditPageChange"
      />
    </template>
  </div>
</template>

<style scoped>
.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 24px;
  overflow-x: auto;
}

.tab-btn {
  padding: 10px 16px;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-tertiary);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  white-space: nowrap;
  transition:
    color 0.15s,
    border-color 0.15s;
}

.tab-btn:hover {
  color: var(--color-text-primary);
}

.tab-btn--active {
  color: var(--color-text-primary);
  border-bottom-color: var(--color-accent);
}

.tab-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 12px;
}

.tab-header .section-title {
  margin-bottom: 0;
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 480px;
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-actions {
  padding-top: 8px;
}

.role-select {
  padding: 4px 8px;
  font-size: 12px;
  min-width: 100px;
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

.action-badge {
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

.success-banner {
  padding: 10px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--color-status-ready);
  background-color: var(--color-bg-raised);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.info-banner {
  padding: 10px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--color-status-pending);
  background-color: var(--color-bg-raised);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
</style>
