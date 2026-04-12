<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'

type Workspace = components['schemas']['Workspace']
type Project = components['schemas']['Project']
type Environment = components['schemas']['Environment']

const route = useRoute()
const router = useRouter()

const workspaces = ref<Workspace[]>([])
const expandedWs = ref<Set<string>>(new Set())
const expandedProj = ref<Set<string>>(new Set())
const projectsByWs = ref<Record<string, Project[]>>({})
const envsByProj = ref<Record<string, Environment[]>>({})
const loadingWs = ref<Set<string>>(new Set())
const loadingProj = ref<Set<string>>(new Set())
const loadingRoot = ref(true)

async function fetchWorkspaces() {
  loadingRoot.value = true
  try {
    const { data } = await api.GET('/workspaces', {
      params: { query: { page: 0, size: 100 } },
    })
    if (data) {
      workspaces.value = data.content ?? []
    }
  } finally {
    loadingRoot.value = false
  }
}

async function fetchProjects(wsSlug: string) {
  if (projectsByWs.value[wsSlug]) return
  loadingWs.value.add(wsSlug)
  try {
    const { data } = await api.GET('/projects', {
      params: { query: { workspaceSlug: wsSlug, page: 0, size: 100 } },
    })
    if (data) {
      projectsByWs.value[wsSlug] = data.content ?? []
    }
  } finally {
    loadingWs.value.delete(wsSlug)
  }
}

async function fetchEnvironments(projSlug: string) {
  if (envsByProj.value[projSlug]) return
  loadingProj.value.add(projSlug)
  try {
    const { data } = await api.GET('/environments', {
      params: { query: { projectSlug: projSlug, page: 0, size: 100 } },
    })
    if (data) {
      envsByProj.value[projSlug] = data.content ?? []
    }
  } finally {
    loadingProj.value.delete(projSlug)
  }
}

function toggleWs(wsSlug: string) {
  if (expandedWs.value.has(wsSlug)) {
    expandedWs.value.delete(wsSlug)
  } else {
    expandedWs.value.add(wsSlug)
    fetchProjects(wsSlug)
  }
}

function toggleProj(projSlug: string) {
  if (expandedProj.value.has(projSlug)) {
    expandedProj.value.delete(projSlug)
  } else {
    expandedProj.value.add(projSlug)
    fetchEnvironments(projSlug)
  }
}

function isActive(wsSlug: string, projSlug?: string, envSlug?: string): boolean {
  const params = route.params
  if (envSlug) {
    return params.wsSlug === wsSlug && params.projSlug === projSlug && params.envSlug === envSlug
  }
  if (projSlug) {
    return params.wsSlug === wsSlug && params.projSlug === projSlug && !params.envSlug
  }
  return params.wsSlug === wsSlug && !params.projSlug
}

function navigate(wsSlug: string, projSlug?: string, envSlug?: string) {
  if (envSlug) {
    router.push({ name: 'environment', params: { wsSlug, projSlug, envSlug } })
  } else if (projSlug) {
    router.push({ name: 'project', params: { wsSlug, projSlug } })
  } else {
    router.push({ name: 'workspace', params: { wsSlug } })
  }
}

async function expandToCurrentRoute() {
  const { wsSlug, projSlug } = route.params as Record<string, string>
  if (wsSlug) {
    expandedWs.value.add(wsSlug)
    await fetchProjects(wsSlug)
  }
  if (projSlug) {
    expandedProj.value.add(projSlug)
    await fetchEnvironments(projSlug)
  }
}

watch(
  () => [route.params.wsSlug, route.params.projSlug, route.params.envSlug],
  () => expandToCurrentRoute(),
)

onMounted(async () => {
  await fetchWorkspaces()
  await expandToCurrentRoute()
})
</script>

<template>
  <div class="sidebar-tree">
    <router-link to="/console" class="tree-section-label" active-class="" :exact="true">
      Workspaces
    </router-link>

    <div v-if="loadingRoot" class="tree-loading">Loading...</div>

    <div v-for="ws in workspaces" :key="ws.slug" class="tree-node">
      <div class="tree-item tree-item--ws" :class="{ 'tree-item--active': isActive(ws.slug!) }">
        <button class="tree-toggle" @click.stop="toggleWs(ws.slug!)">
          <span class="tree-chevron" :class="{ 'tree-chevron--open': expandedWs.has(ws.slug!) }"
            >&#9656;</span
          >
        </button>
        <span class="tree-label" @click="navigate(ws.slug!)">{{ ws.name }}</span>
      </div>

      <div v-if="expandedWs.has(ws.slug!)" class="tree-children">
        <div v-if="loadingWs.has(ws.slug!)" class="tree-loading">Loading...</div>

        <div v-for="proj in projectsByWs[ws.slug!] ?? []" :key="proj.slug" class="tree-node">
          <div
            class="tree-item tree-item--proj"
            :class="{ 'tree-item--active': isActive(ws.slug!, proj.slug!) }"
          >
            <button class="tree-toggle" @click.stop="toggleProj(proj.slug!)">
              <span
                class="tree-chevron"
                :class="{ 'tree-chevron--open': expandedProj.has(proj.slug!) }"
                >&#9656;</span
              >
            </button>
            <span class="tree-label" @click="navigate(ws.slug!, proj.slug!)">{{ proj.name }}</span>
          </div>

          <div v-if="expandedProj.has(proj.slug!)" class="tree-children">
            <div v-if="loadingProj.has(proj.slug!)" class="tree-loading">Loading...</div>

            <div v-for="env in envsByProj[proj.slug!] ?? []" :key="env.slug" class="tree-node">
              <div
                class="tree-item tree-item--env"
                :class="{ 'tree-item--active': isActive(ws.slug!, proj.slug!, env.slug!) }"
                @click="navigate(ws.slug!, proj.slug!, env.slug!)"
              >
                <span class="tree-leaf-dot">&#8226;</span>
                <span class="tree-label">{{ env.name }}</span>
              </div>
            </div>

            <div
              v-if="!loadingProj.has(proj.slug!) && (envsByProj[proj.slug!] ?? []).length === 0"
              class="tree-empty"
            >
              No environments
            </div>
          </div>
        </div>

        <div
          v-if="!loadingWs.has(ws.slug!) && (projectsByWs[ws.slug!] ?? []).length === 0"
          class="tree-empty"
        >
          No projects
        </div>
      </div>
    </div>

    <div v-if="!loadingRoot && workspaces.length === 0" class="tree-empty">No workspaces</div>

    <div class="tree-divider"></div>

    <router-link
      to="/console/admin"
      class="tree-section-label"
      active-class="tree-section-label--active"
    >
      Admin
    </router-link>
  </div>
</template>

<style scoped>
.sidebar-tree {
  display: flex;
  flex-direction: column;
  gap: 1px;
  font-size: 13px;
}

.tree-section-label {
  display: block;
  padding: 6px 12px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-tertiary);
  text-decoration: none;
  transition: color 0.15s;
}

.tree-section-label:hover {
  color: var(--color-text-secondary);
}

.tree-section-label--active {
  color: var(--color-text-primary);
}

.tree-node {
  display: flex;
  flex-direction: column;
}

.tree-item {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--color-text-secondary);
  transition:
    background-color 0.1s,
    color 0.1s;
  user-select: none;
}

.tree-item:hover {
  background-color: var(--color-bg-raised);
  color: var(--color-text-primary);
}

.tree-item--active {
  background-color: var(--color-bg-raised);
  color: var(--color-text-primary);
  font-weight: 500;
}

.tree-item--ws {
  padding-left: 4px;
}

.tree-item--proj {
  padding-left: 20px;
}

.tree-item--env {
  padding-left: 36px;
}

.tree-toggle {
  background: none;
  border: none;
  padding: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: inherit;
  flex-shrink: 0;
}

.tree-chevron {
  display: inline-block;
  font-size: 10px;
  transition: transform 0.15s;
}

.tree-chevron--open {
  transform: rotate(90deg);
}

.tree-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 2px 4px;
}

.tree-leaf-dot {
  width: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 8px;
  color: var(--color-text-tertiary);
}

.tree-children {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.tree-loading {
  padding: 4px 12px 4px 36px;
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-style: italic;
}

.tree-empty {
  padding: 4px 12px 4px 36px;
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-style: italic;
}

.tree-divider {
  height: 1px;
  background-color: var(--color-border);
  margin: 8px 12px;
}
</style>
