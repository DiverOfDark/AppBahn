import { computed, ref, watch, type Ref } from 'vue'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import type { PortRow } from '@/components/resource/PortRowsEditor.vue'
import type { EnvVarRow } from '@/components/resource/EnvVarsEditor.vue'
import type { HealthCheckState } from '@/components/resource/HealthCheckEditor.vue'
import { extractApiErrorMessage } from '@/utils/apiError'
import { asOptionalInt, buildProbe } from '@/utils/resource'

type CreateResourceRequest = components['schemas']['CreateResourceRequest']
type HostingConfig = components['schemas']['HostingConfig']
type ResourceConfig = components['schemas']['ResourceConfig']
type ImageSourceSpec = components['schemas']['ImageSourceSpec']
export type DeployStrategy = NonNullable<HostingConfig['deployStrategy']>

export type SourceKind = 'docker' | 'git' | 'promote'
export type Kind = 'resource' | 'cronjob'
export type PromotionBinding = 'track' | 'pin'

const DEPLOYMENT_TYPE = 'deployment'
const DNS_NAME_REGEX = /^[a-z][a-z0-9-]*$/
const DIGEST_REGEX = /^sha256:[0-9a-f]{64}$/i

function defaultHealth(): HealthCheckState {
  return {
    enabled: false,
    mode: 'http',
    path: '/health',
    port: undefined,
    command: '',
    initialDelay: 10,
    period: 10,
    failureThreshold: 3,
  }
}

/**
 * Owns the Create-Resource form state and submit pipeline:
 *   - reactive draft (source/kind selection, identity, image, runtime,
 *     networking, env vars, health check)
 *   - validation against the same rules the previous monolith enforced
 *   - POST /resources on submit, redirect to the environment view on success
 *
 * Resets when the user navigates between environments (envSlug change), so
 * an in-progress draft from `staging` doesn't leak into `production`.
 */
export interface PromotionResolver {
  /** Cluster name of the *source* environment whose resource is being promoted. */
  sourceClusterName: () => string
  /** Active platform namespace prefix (e.g. `abp`). */
  namespacePrefix: () => string
}

export function useResourceCreateForm(
  envSlug: Ref<string>,
  onCreated: () => void,
  promotionResolver?: PromotionResolver,
) {
  const source = ref<SourceKind>('docker')
  const kind = ref<Kind>('resource')
  const name = ref('')
  const type = ref(DEPLOYMENT_TYPE)
  const image = ref('')
  const tag = ref('latest')
  const cpu = ref(100)
  const memory = ref(128)
  const minReplicas = ref(1)
  const maxReplicas = ref<number | undefined>(undefined)
  const nodePool = ref<string | undefined>(undefined)
  const deployStrategy = ref<DeployStrategy>('Rolling')
  const pdbMinAvailable = ref<number | undefined>(undefined)

  // Promote-from-environment fields. Bound to the form's source-card panel.
  const promoteEnvSlug = ref<string>('')
  const promoteResourceSlug = ref<string>('')
  const promotionBinding = ref<PromotionBinding>('track')
  const pinnedDigest = ref<string>('')

  let portRowSeq = 0
  const ports = ref<PortRow[]>([{ id: ++portRowSeq, port: 80, expose: 'Ingress' }])
  const envVars = ref<EnvVarRow[]>([])

  // Single probe shape applied to both liveness AND readiness. Startup probes
  // are not exposed here yet — most apps don't need them and the design hides
  // them behind the same toggle anyway.
  const health = ref<HealthCheckState>(defaultHealth())

  const loading = ref(false)
  const error = ref('')
  const errors = ref<string[]>([])

  const fullImage = computed(() => {
    const i = image.value.trim()
    if (!i) return ''
    const t = tag.value.trim()
    return t ? `${i}:${t}` : i
  })

  function resetForm() {
    source.value = 'docker'
    kind.value = 'resource'
    name.value = ''
    type.value = DEPLOYMENT_TYPE
    image.value = ''
    tag.value = 'latest'
    cpu.value = 250
    memory.value = 256
    minReplicas.value = 1
    maxReplicas.value = undefined
    nodePool.value = undefined
    deployStrategy.value = 'Rolling'
    pdbMinAvailable.value = undefined
    ports.value = [{ id: ++portRowSeq, port: 80, expose: 'Ingress' }]
    envVars.value = []
    health.value = defaultHealth()
    promoteEnvSlug.value = ''
    promoteResourceSlug.value = ''
    promotionBinding.value = 'track'
    pinnedDigest.value = ''
    error.value = ''
    errors.value = []
    loading.value = false
  }

  // When the user re-picks the source environment, the previously-selected
  // source resource no longer belongs to it. Clear it so the form doesn't
  // ship a cross-environment slug on submit.
  watch(promoteEnvSlug, () => {
    promoteResourceSlug.value = ''
  })

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
      if (source.value === 'docker') {
        if (!image.value.trim()) {
          result.push('Image URL is required')
        }
      } else if (source.value === 'promote') {
        if (!promoteEnvSlug.value.trim()) {
          result.push('Source environment is required')
        }
        if (!promoteResourceSlug.value.trim()) {
          result.push('Source resource is required')
        }
        if (promotionBinding.value === 'pin') {
          const d = pinnedDigest.value.trim()
          if (!d) {
            result.push('Pinned digest is required when binding is "Pin to digest"')
          } else if (!DIGEST_REGEX.test(d)) {
            result.push('Pinned digest must look like sha256:<64-hex>')
          }
        }
      }
      if (cpu.value < 1 || !Number.isInteger(cpu.value)) {
        result.push('CPU must be a positive integer')
      }
      if (memory.value < 1 || !Number.isInteger(memory.value)) {
        result.push('Memory must be a positive integer')
      }
      if (minReplicas.value < 1 || !Number.isInteger(minReplicas.value)) {
        result.push('Replicas must be a positive integer (>= 1)')
      }
      const maxR = asOptionalInt(maxReplicas.value)
      if (maxR !== undefined) {
        if (!Number.isInteger(maxR) || maxR < 1) {
          result.push('Max replicas, when set, must be a positive integer')
        } else if (maxR < minReplicas.value) {
          result.push('Max replicas cannot be less than the minimum replicas')
        }
      }
      const pdbMin = asOptionalInt(pdbMinAvailable.value)
      if (pdbMin !== undefined && (!Number.isInteger(pdbMin) || pdbMin < 0)) {
        result.push('Pod disruption budget minAvailable, when set, must be a non-negative integer')
      }
      const seen = new Set<number>()
      for (const row of ports.value) {
        if (!Number.isInteger(row.port) || row.port < 1 || row.port > 65535) {
          result.push('Each port must be an integer between 1 and 65535')
          break
        }
        if (seen.has(row.port)) {
          result.push(`Port ${row.port} is listed more than once`)
          break
        }
        seen.add(row.port)
      }
      if (health.value.enabled) {
        const h = health.value
        if (h.mode === 'http' && !h.path.trim()) {
          result.push('Health-check path is required for HTTP probes')
        }
        if (h.mode === 'exec' && !h.command.trim()) {
          result.push('Health-check command is required for exec probes')
        }
        if (
          !Number.isInteger(h.initialDelay) ||
          h.initialDelay < 0 ||
          !Number.isInteger(h.period) ||
          h.period < 1 ||
          !Number.isInteger(h.failureThreshold) ||
          h.failureThreshold < 1
        ) {
          result.push('Health-check timings must be positive integers')
        }
      }
    }

    errors.value = result
    return result.length === 0
  }

  watch(envSlug, () => {
    resetForm()
  })

  function buildPromotionImageSource(): ImageSourceSpec {
    const cluster = promotionResolver?.sourceClusterName() ?? ''
    const prefix = promotionResolver?.namespacePrefix() ?? 'abp'
    const upstream: components['schemas']['ImageSourcePromotionSpec'] = {
      upstream: {
        cluster,
        namespace: `${prefix}-${promoteEnvSlug.value}`,
        name: promoteResourceSlug.value,
      },
    }
    if (promotionBinding.value === 'pin') {
      upstream.pinnedDigest = pinnedDigest.value.trim()
    } else {
      upstream.autoPromote = true
    }
    return { type: 'ImageSource', imageSource: upstream }
  }

  async function submit() {
    if (!validate()) {
      error.value = errors.value[0] ?? ''
      return
    }

    loading.value = true
    error.value = ''

    try {
      const imageSourceSpec: ImageSourceSpec =
        source.value === 'promote'
          ? buildPromotionImageSource()
          : { type: 'Image', image: { ref: fullImage.value } }

      const body: CreateResourceRequest = {
        name: name.value.trim(),
        type: type.value,
        environmentSlug: envSlug.value,
        config: {},
        imageSource: imageSourceSpec,
      }
      if (type.value === DEPLOYMENT_TYPE) {
        // Drop empty rows; later occurrences of the same key win (matches what
        // most config systems do when you have duplicates).
        const env: Record<string, string> = {}
        for (const row of envVars.value) {
          const k = row.key.trim()
          if (k) env[k] = row.value
        }
        const hosting: HostingConfig = {
          cpu: cpu.value + 'm',
          memory: memory.value + 'Mi',
          minReplicas: minReplicas.value,
        }
        const maxR = asOptionalInt(maxReplicas.value)
        if (maxR !== undefined) {
          hosting.maxReplicas = maxR
        }
        if (nodePool.value && nodePool.value.trim()) {
          hosting.nodePool = nodePool.value.trim()
        }
        if (deployStrategy.value && deployStrategy.value !== 'Rolling') {
          hosting.deployStrategy = deployStrategy.value
        }
        const pdbMin = asOptionalInt(pdbMinAvailable.value)
        if (pdbMin !== undefined) {
          hosting.pdb = { minAvailable: pdbMin }
        }
        const config: ResourceConfig = {
          hosting,
          networking: {
            ports: ports.value.map((row) => ({ port: row.port, expose: row.expose })),
          },
          env,
          runMode: 'Continuous',
        }
        if (health.value.enabled) {
          const probe = buildProbe(health.value, ports.value[0]?.port)
          config.healthCheck = { liveness: probe, readiness: probe }
        }
        body.config = config
      }
      const { error: apiError } = await api.POST('/resources', { body })

      if (apiError) {
        error.value = extractApiErrorMessage(apiError, 'Failed to create resource')
        return
      }

      onCreated()
    } catch {
      error.value = 'Failed to create resource'
    } finally {
      loading.value = false
    }
  }

  return {
    source,
    kind,
    name,
    type,
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
    resetForm,
  }
}
