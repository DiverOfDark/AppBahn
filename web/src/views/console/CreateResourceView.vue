<script setup lang="ts">
/**
 * Create-resource page — orchestrator. The reactive form state, validation,
 * and POST /resources pipeline live in `useResourceCreateForm`; the five
 * sections of the page are split into sub-components under
 * `./create-resource/`. This file owns layout, header/breadcrumb wiring,
 * error display, and the submit action bar.
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'
import { useResourceCreateForm } from '@/composables/resource/useResourceCreateForm'
import { usePlatformConfigStore } from '@/stores/platformConfig'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import SourcePicker from './create-resource/SourcePicker.vue'
import KindPicker from './create-resource/KindPicker.vue'
import ResourceForm from './create-resource/ResourceForm.vue'
import CreateSummary from './create-resource/CreateSummary.vue'

type NodePool = components['schemas']['NodePool']
type Environment = components['schemas']['Environment']
type Resource = components['schemas']['Resource']

// Kinds whose successful build produces an OCI image that downstream
// environments can promote. Today only `deployment` qualifies.
const IMAGE_PRODUCING_KINDS = new Set(['deployment'])

const route = useRoute()
const router = useRouter()

const wsSlug = computed(() => route.params.wsSlug as string)
const projSlug = computed(() => route.params.projSlug as string)
const envSlug = computed(() => route.params.envSlug as string)

const nodePools = ref<NodePool[]>([])
const promoteEnvironments = ref<Environment[]>([])
const promoteResources = ref<Resource[]>([])
const promoteEnvsLoading = ref(false)
const promoteResourcesLoading = ref(false)

const platformConfigStore = usePlatformConfigStore()
void platformConfigStore.load()

async function fetchNodePools() {
  if (!envSlug.value) {
    nodePools.value = []
    return
  }
  try {
    const { data } = await api.GET('/environments/{slug}/node-pools', {
      params: { path: { slug: envSlug.value } },
    })
    nodePools.value = data ?? []
  } catch {
    // Node-pool catalogue is optional. On failure the Placement & rollout
    // panel auto-hides; the resource still creates with any-pool placement.
    nodePools.value = []
  }
}

watch(envSlug, fetchNodePools, { immediate: true })

async function fetchPromoteEnvironments() {
  if (!projSlug.value) {
    promoteEnvironments.value = []
    return
  }
  promoteEnvsLoading.value = true
  try {
    const { data } = await api.GET('/environments', {
      params: { query: { projectSlug: projSlug.value } },
    })
    // Don't list the current environment as a promotion *source* — promoting
    // into the same environment is a no-op and would confuse the picker.
    promoteEnvironments.value = (data?.content ?? []).filter((e) => e.slug !== envSlug.value)
  } catch {
    promoteEnvironments.value = []
  } finally {
    promoteEnvsLoading.value = false
  }
}

async function fetchPromoteResources(sourceEnvSlug: string) {
  if (!sourceEnvSlug) {
    promoteResources.value = []
    return
  }
  promoteResourcesLoading.value = true
  try {
    const { data } = await api.GET('/resources', {
      params: { query: { environmentSlug: sourceEnvSlug } },
    })
    promoteResources.value = (data?.content ?? []).filter((r) =>
      IMAGE_PRODUCING_KINDS.has(r.type ?? ''),
    )
  } catch {
    promoteResources.value = []
  } finally {
    promoteResourcesLoading.value = false
  }
}

const {
  source,
  kind,
  name,
  image,
  tag,
  cpu,
  memory,
  minReplicas,
  maxReplicas,
  nodePool,
  deployStrategy,
  pdbMinAvailable,
  ports,
  envVars,
  health,
  promoteEnvSlug,
  promoteResourceSlug,
  promotionBinding,
  pinnedDigest,
  loading,
  error,
  errors,
  fullImage,
  submit,
} = useResourceCreateForm(
  envSlug,
  () => {
    void router.push({
      name: 'environment',
      params: { wsSlug: wsSlug.value, projSlug: projSlug.value, envSlug: envSlug.value },
    })
  },
  {
    sourceClusterName: () =>
      promoteEnvironments.value.find((e) => e.slug === promoteEnvSlug.value)?.targetCluster ?? '',
    namespacePrefix: () => platformConfigStore.config?.namespacePrefix ?? 'abp',
  },
)

// Lazy-load the env list the first time the user opens the Promote source
// card. Refresh on env change so navigating between environments doesn't
// show stale candidates.
watch(
  [source, projSlug, envSlug],
  ([newSource]) => {
    if (newSource === 'promote') {
      void fetchPromoteEnvironments()
    }
  },
  { immediate: true },
)

watch(promoteEnvSlug, (slug) => {
  void fetchPromoteResources(slug)
})
</script>

<template>
  <div class="create-resource">
    <PageHeader title="Create Resource">
      <template #subtitle>
        <span class="flow-meta">
          <span class="num">01</span>
          <span class="sep">·</span>
          <span>Resource provisioning</span>
          <span class="sep">·</span>
          <span class="mono">{{ projSlug }} / {{ envSlug }}</span>
        </span>
      </template>
    </PageHeader>

    <AppBreadcrumb :items="buildBreadcrumbChain({ wsSlug, projSlug, envSlug }, 'Create', true)" />

    <ul v-if="errors.length > 1" class="error-banner">
      <li v-for="(err, i) in errors" :key="i">{{ err }}</li>
    </ul>
    <div v-else-if="error" class="error-banner">{{ error }}</div>

    <form class="create-form" @submit.prevent="submit">
      <div class="layout-grid">
        <div class="main-col">
          <SourcePicker v-model="source" />
          <KindPicker v-model="kind" />
          <ResourceForm
            v-model:name="name"
            v-model:image="image"
            v-model:tag="tag"
            v-model:cpu="cpu"
            v-model:memory="memory"
            v-model:min-replicas="minReplicas"
            v-model:max-replicas="maxReplicas"
            v-model:node-pool="nodePool"
            v-model:deploy-strategy="deployStrategy"
            v-model:pdb-min-available="pdbMinAvailable"
            v-model:ports="ports"
            v-model:env-vars="envVars"
            v-model:health="health"
            v-model:promote-env-slug="promoteEnvSlug"
            v-model:promote-resource-slug="promoteResourceSlug"
            v-model:promotion-binding="promotionBinding"
            v-model:pinned-digest="pinnedDigest"
            :source="source"
            :proj-slug="projSlug"
            :env-slug="envSlug"
            :full-image="fullImage"
            :node-pools="nodePools"
            :promote-environments="promoteEnvironments"
            :promote-resources="promoteResources"
            :promote-envs-loading="promoteEnvsLoading"
            :promote-resources-loading="promoteResourcesLoading"
          />
        </div>

        <CreateSummary
          :name="name"
          :kind="kind"
          :source="source"
          :full-image="fullImage"
          :ports="ports"
          :cpu="cpu"
          :memory="memory"
          :min-replicas="minReplicas"
          :max-replicas="maxReplicas"
          :health="health"
          :env-vars="envVars"
          :env-slug="envSlug"
          :promote-env-slug="promoteEnvSlug"
          :promote-resource-slug="promoteResourceSlug"
          :promotion-binding="promotionBinding"
        />
      </div>

      <div class="actbar">
        <div class="actbar-l">
          <span class="actbar-flow mono">
            ▸ source · <b>{{ source }}</b>
            <span class="sep">·</span>
            kind · <b>{{ kind }}</b>
          </span>
        </div>
        <div class="actbar-r">
          <router-link
            :to="{ name: 'environment', params: { wsSlug, projSlug, envSlug } }"
            class="btn-secondary"
          >
            Cancel
          </router-link>
          <button type="submit" class="btn-primary" :disabled="loading">
            {{ loading ? 'Creating...' : 'Create Resource' }}
          </button>
        </div>
      </div>
    </form>
  </div>
</template>

<style scoped>
.create-resource {
  display: contents;
}

.flow-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}
.flow-meta .num {
  color: var(--color-accent);
}
.flow-meta .sep {
  color: var(--color-text-tertiary);
}
.flow-meta .mono {
  text-transform: none;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-top: 8px;
}

.layout-grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 28px;
  align-items: start;
  padding-bottom: 24px;
}

.main-col {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

@media (max-width: 1100px) {
  .layout-grid {
    grid-template-columns: 1fr;
  }
}

/* Bottom action bar */
.actbar {
  position: sticky;
  bottom: 0;
  background: var(--color-bg-surface);
  border-top: 1px solid var(--color-border);
  padding: 14px 32px;
  margin: 0 -32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  z-index: 1;
}
.actbar-l {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  display: flex;
  align-items: center;
  gap: 8px;
}
.actbar-flow b {
  color: var(--color-text-primary);
  font-weight: 500;
}
.actbar-flow .sep {
  margin: 0 4px;
  color: var(--color-text-tertiary);
}
.actbar-r {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
