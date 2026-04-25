<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import PageHeader from '@/components/PageHeader.vue'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import { buildBreadcrumbChain } from '@/utils/breadcrumbs'

type ResourceTypeInfo = components['schemas']['ResourceTypeInfo']
type CreateResourceRequest = components['schemas']['CreateResourceRequest']

const DEPLOYMENT_TYPE = 'deployment'

const route = useRoute()
const router = useRouter()

const wsSlug = computed(() => route.params.wsSlug as string)
const projSlug = computed(() => route.params.projSlug as string)
const envSlug = computed(() => route.params.envSlug as string)

const name = ref('')
const type = ref(DEPLOYMENT_TYPE)
const image = ref('')
const tag = ref('latest')
const port = ref(80)
const cpu = ref(250)
const memory = ref(256)
const replicas = ref(1)
const expose = ref('ingress')

const resourceTypes = ref<ResourceTypeInfo[]>([])
const loading = ref(false)
const error = ref('')
const errors = ref<string[]>([])

function resetForm() {
  name.value = ''
  type.value = DEPLOYMENT_TYPE
  image.value = ''
  tag.value = 'latest'
  port.value = 80
  cpu.value = 250
  memory.value = 256
  replicas.value = 1
  expose.value = 'ingress'
  error.value = ''
  errors.value = []
  loading.value = false
}

const DNS_NAME_REGEX = /^[a-z][a-z0-9-]*$/

function validate(): boolean {
  const result: string[] = []

  if (!name.value.trim()) {
    result.push('Name is required')
  } else if (!DNS_NAME_REGEX.test(name.value.trim())) {
    result.push(
      'Name must start with a lowercase letter and contain only lowercase letters, digits, and hyphens',
    )
  }

  if (type.value !== DEPLOYMENT_TYPE) {
    result.push('Only deployment resources can be created from the console at this time')
  } else {
    if (!image.value.trim()) {
      result.push('Image URL is required')
    }
    if (port.value < 1 || port.value > 65535 || !Number.isInteger(port.value)) {
      result.push('Port must be an integer between 1 and 65535')
    }
    if (cpu.value < 1 || !Number.isInteger(cpu.value)) {
      result.push('CPU must be a positive integer')
    }
    if (memory.value < 1 || !Number.isInteger(memory.value)) {
      result.push('Memory must be a positive integer')
    }
    if (replicas.value < 1 || !Number.isInteger(replicas.value)) {
      result.push('Replicas must be a positive integer (>= 1)')
    }
  }

  errors.value = result
  return result.length === 0
}

watch(envSlug, () => {
  resetForm()
})

onMounted(async () => {
  try {
    const { data } = await api.GET('/resource-types', {
      params: { query: {} },
    })
    if (data) {
      resourceTypes.value = data
    }
  } catch {
    error.value = 'Failed to load resource types'
  }
})

async function submit() {
  if (!validate()) {
    error.value = errors.value[0] ?? ''
    return
  }

  loading.value = true
  error.value = ''

  try {
    const body: CreateResourceRequest = {
      name: name.value.trim(),
      type: type.value,
      environmentSlug: envSlug.value,
      config: {},
    }
    if (type.value === DEPLOYMENT_TYPE) {
      body.config = {
        source: {
          type: 'docker',
          image: image.value.trim(),
          tag: tag.value.trim() || 'latest',
        },
        hosting: {
          cpu: cpu.value + 'm',
          memory: memory.value + 'Mi',
          minReplicas: replicas.value,
        },
        networking: {
          ports: [
            {
              port: port.value,
              expose: expose.value as 'ingress' | 'none',
            },
          ],
        },
        runMode: 'CONTINUOUS',
      }
    }
    const { error: apiError } = await api.POST('/resources', {
      body,
    })

    if (apiError) {
      const err = apiError as { message?: string; error?: string }
      error.value = err.message?.toString() || err.error?.toString() || 'Failed to create resource'
      return
    }

    router.push({
      name: 'environment',
      params: { wsSlug: wsSlug.value, projSlug: projSlug.value, envSlug: envSlug.value },
    })
  } catch {
    error.value = 'Failed to create resource'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader title="Create Resource" />

    <AppBreadcrumb :items="buildBreadcrumbChain({ wsSlug, projSlug, envSlug }, 'Create', true)" />

    <ul v-if="errors.length > 1" class="error-banner">
      <li v-for="(err, i) in errors" :key="i">{{ err }}</li>
    </ul>
    <div v-else-if="error" class="error-banner">{{ error }}</div>

    <form class="create-form" @submit.prevent="submit">
      <div class="form-section">
        <h3 class="form-section-title">Basic</h3>
        <div class="form-stack">
          <label class="form-label">
            Name
            <input v-model="name" class="form-input" placeholder="my-nginx" required />
          </label>
          <label class="form-label">
            Type
            <select v-model="type" class="form-input">
              <option :value="DEPLOYMENT_TYPE">Deployment</option>
              <option
                v-for="rt in resourceTypes.filter((t) => t.type !== DEPLOYMENT_TYPE)"
                :key="rt.type"
                :value="rt.type"
              >
                {{ rt.displayName || rt.type }}
              </option>
            </select>
          </label>
        </div>
      </div>

      <div v-if="type === DEPLOYMENT_TYPE" class="form-section">
        <h3 class="form-section-title">Docker Source</h3>
        <div class="form-stack">
          <label class="form-label">
            Image
            <input v-model="image" class="form-input" placeholder="nginx" required />
          </label>
          <label class="form-label">
            Tag
            <input v-model="tag" class="form-input" placeholder="latest" />
          </label>
        </div>
      </div>

      <div v-if="type === DEPLOYMENT_TYPE" class="form-section">
        <h3 class="form-section-title">Hosting & Networking</h3>
        <div class="form-grid">
          <label class="form-label">
            Port
            <input v-model.number="port" type="number" class="form-input" min="1" max="65535" />
          </label>
          <label class="form-label">
            CPU (millicores)
            <input v-model.number="cpu" type="number" class="form-input" min="50" step="50" />
          </label>
          <label class="form-label">
            Memory (MB)
            <input v-model.number="memory" type="number" class="form-input" min="64" step="64" />
          </label>
          <label class="form-label">
            Replicas
            <input v-model.number="replicas" type="number" class="form-input" min="1" max="10" />
          </label>
          <label class="form-label">
            Expose
            <select v-model="expose" class="form-input">
              <option value="ingress">Ingress</option>
              <option value="none">None</option>
            </select>
          </label>
        </div>
        <p v-if="expose === 'ingress'" class="form-hint">
          Domain will be auto-generated from the resource slug.
        </p>
      </div>

      <div class="form-actions">
        <router-link
          :to="{ name: 'environment', params: { wsSlug, projSlug, envSlug } }"
          class="btn-secondary"
          >Cancel</router-link
        >
        <button type="submit" class="btn-primary" :disabled="loading">
          {{ loading ? 'Creating...' : 'Create Resource' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.page {
  max-width: 640px;
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
</style>
