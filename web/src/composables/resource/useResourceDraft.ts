import { computed, ref, watch, type Ref } from 'vue'
import { api } from '@/api/client'
import type { components } from '@/api/schema'
import type { PortRow } from '@/components/resource/PortRowsEditor.vue'
import type { HealthCheckState, ProbeMode } from '@/components/resource/HealthCheckEditor.vue'
import { extractApiErrorMessage } from '@/utils/apiError'
import {
  asOptionalInt,
  buildProbe,
  parseMebibytes,
  parseMillicores,
  splitImageRef,
} from '@/utils/resource'

type Resource = components['schemas']['Resource']

export interface SettingsDraft {
  name: string
  image: string
  tag: string
  cpu: number
  memory: number
  minReplicas: number
  maxReplicas: number | undefined
  ports: PortRow[]
  health: HealthCheckState
  startup: HealthCheckState
  command: string
  args: string
}

/**
 * Owns the Settings-tab draft lifecycle for a single resource:
 *   - hydrate the draft from the resource on mount and on resource change
 *     (unless the user has unsaved edits — never clobber in-flight typing)
 *   - PATCH /resources/{slug} on save, promoting the just-submitted draft
 *     to the baseline so the dirty counter resets instantly
 *   - skip the next hydrate after a successful save (GET lags PATCH for the
 *     activeRelease-sourced image ref, which would otherwise re-stamp stale
 *     values back over what was just written)
 *
 * Returns refs the consumer can bind to in the template + helpers for
 * discard / save. The composable doesn't render anything itself.
 */
export function useResourceDraft(resource: Ref<Resource>, onSaved: () => void) {
  let portRowSeq = 0
  const initialDraft = ref<SettingsDraft | null>(null)
  const draft = ref<SettingsDraft | null>(null)
  const saving = ref(false)
  const error = ref('')
  const savedAt = ref<number | null>(null)

  function probeToState(
    probe: components['schemas']['ProbeConfig'] | undefined,
    defaults: { path: string; initialDelay: number; period: number; failureThreshold: number },
  ): HealthCheckState {
    const mode: ProbeMode = probe?.exec ? 'exec' : probe?.tcpSocket ? 'tcp' : 'http'
    return {
      enabled: probe != null,
      mode,
      path: probe?.httpGet?.path ?? defaults.path,
      port: probe?.httpGet?.port ?? probe?.tcpSocket?.port,
      command: (probe?.exec?.command ?? []).join(' '),
      initialDelay: probe?.initialDelaySeconds ?? defaults.initialDelay,
      period: probe?.periodSeconds ?? defaults.period,
      failureThreshold: probe?.failureThreshold ?? defaults.failureThreshold,
    }
  }

  function buildDraft(r: Resource): SettingsDraft {
    const config = r.config ?? {}
    const hosting = config.hosting ?? {}
    const ports: PortRow[] = (config.networking?.ports ?? []).map((p) => ({
      id: ++portRowSeq,
      port: p.port ?? 80,
      expose: (p.expose ?? 'None') as PortRow['expose'],
    }))
    if (ports.length === 0) ports.push({ id: ++portRowSeq, port: 80, expose: 'Ingress' })
    const liveness = config.healthCheck?.liveness ?? config.healthCheck?.readiness
    const [imagePart, tagPart] = splitImageRef(r.statusDetail?.activeRelease?.imageRef ?? '')
    return {
      name: r.name ?? r.slug ?? '',
      image: imagePart,
      tag: tagPart,
      cpu: parseMillicores(hosting.cpu) ?? 250,
      memory: parseMebibytes(hosting.memory) ?? 256,
      minReplicas: hosting.minReplicas ?? 1,
      maxReplicas: hosting.maxReplicas,
      ports,
      health: probeToState(liveness, {
        path: '/health',
        initialDelay: 10,
        period: 10,
        failureThreshold: 3,
      }),
      startup: probeToState(config.healthCheck?.startup, {
        path: '/health',
        initialDelay: 0,
        period: 5,
        failureThreshold: 30,
      }),
      command: '',
      args: '',
    }
  }

  const isDirty = computed(() => {
    if (!draft.value || !initialDraft.value) return false
    return JSON.stringify(draft.value) !== JSON.stringify(initialDraft.value)
  })

  const dirtyCount = computed(() => {
    if (!draft.value || !initialDraft.value) return 0
    let n = 0
    const a = draft.value
    const b = initialDraft.value
    if (a.name !== b.name) n++
    if (a.image !== b.image || a.tag !== b.tag) n++
    if (a.cpu !== b.cpu) n++
    if (a.memory !== b.memory) n++
    if (a.minReplicas !== b.minReplicas || a.maxReplicas !== b.maxReplicas) n++
    if (JSON.stringify(a.ports) !== JSON.stringify(b.ports)) n++
    if (a.health.enabled !== b.health.enabled) n++
    if (a.health.enabled && JSON.stringify(a.health) !== JSON.stringify(b.health)) n++
    if (a.startup.enabled !== b.startup.enabled) n++
    if (a.startup.enabled && JSON.stringify(a.startup) !== JSON.stringify(b.startup)) n++
    if (a.command !== b.command || a.args !== b.args) n++
    return n
  })

  const fullImage = computed(() => {
    if (!draft.value) return ''
    const i = draft.value.image.trim()
    if (!i) return ''
    const t = draft.value.tag.trim()
    return t ? `${i}:${t}` : i
  })

  let skipNextHydrate = false
  watch(
    resource,
    (r) => {
      if (skipNextHydrate) {
        skipNextHydrate = false
        return
      }
      if (!draft.value || !isDirty.value) {
        const fresh = buildDraft(r)
        initialDraft.value = fresh
        draft.value = JSON.parse(JSON.stringify(fresh))
      }
    },
    { immediate: true, deep: false },
  )

  function discard() {
    if (!initialDraft.value) return
    draft.value = JSON.parse(JSON.stringify(initialDraft.value))
    error.value = ''
  }

  async function save() {
    if (!draft.value) return
    // Snapshot the draft state ONCE up front. The PATCH body, the
    // full-image string, and the post-save baseline are all derived from
    // this frozen copy — so keystrokes that land DURING the awaited PATCH
    // don't get silently adopted as "saved" state (the dirty counter would
    // otherwise drop to 0 without those edits ever being sent).
    const snapshot: SettingsDraft = JSON.parse(JSON.stringify(draft.value))
    saving.value = true
    error.value = ''
    try {
      const config: components['schemas']['ResourceConfig'] = {
        hosting: {
          cpu: snapshot.cpu + 'm',
          memory: snapshot.memory + 'Mi',
          minReplicas: snapshot.minReplicas,
        },
        networking: {
          ports: snapshot.ports.map((row) => ({ port: row.port, expose: row.expose })),
        },
        env: resource.value.config?.env ?? {},
      }
      const maxR = asOptionalInt(snapshot.maxReplicas)
      if (maxR !== undefined) config.hosting!.maxReplicas = maxR
      const healthCheck: components['schemas']['HealthCheckConfig'] = {}
      if (snapshot.health.enabled) {
        const probe = buildProbe(snapshot.health, snapshot.ports[0]?.port)
        healthCheck.liveness = probe
        healthCheck.readiness = probe
      }
      if (snapshot.startup.enabled) {
        healthCheck.startup = buildProbe(snapshot.startup, snapshot.ports[0]?.port)
      }
      if (Object.keys(healthCheck).length > 0) {
        config.healthCheck = healthCheck
      }
      const snapImage = snapshot.image.trim()
      const snapTag = snapshot.tag.trim()
      const snapshotFullImage = snapImage ? (snapTag ? `${snapImage}:${snapTag}` : snapImage) : ''
      const body: components['schemas']['UpdateResourceRequest'] = {
        name: snapshot.name.trim(),
        config,
      }
      if (snapshotFullImage) {
        body.imageSource = { type: 'Image', image: { ref: snapshotFullImage } }
      }
      const commandTokens = snapshot.command.trim().split(/\s+/).filter(Boolean)
      const argsTokens = snapshot.args.trim().split(/\s+/).filter(Boolean)
      if (commandTokens.length > 0 || argsTokens.length > 0) {
        const override: components['schemas']['CommandOverride'] = {}
        if (commandTokens.length > 0) override.command = commandTokens
        if (argsTokens.length > 0) override.args = argsTokens
        body.commandOverride = override
      }
      const { error: apiError } = await api.PATCH('/resources/{slug}', {
        params: { path: { slug: resource.value.slug ?? '' } },
        body,
      })
      if (apiError) {
        error.value = extractApiErrorMessage(apiError, 'Failed to save settings')
        return
      }
      savedAt.value = Date.now()
      // Only the *sent* state becomes the new baseline. Keystrokes that
      // landed during the await stay in draft.value, so isDirty remains
      // true and they're preserved for the next save.
      initialDraft.value = snapshot
      skipNextHydrate = true
      onSaved()
    } catch {
      error.value = 'Failed to save settings'
    } finally {
      saving.value = false
    }
  }

  return {
    draft,
    isDirty,
    dirtyCount,
    fullImage,
    saving,
    error,
    savedAt,
    discard,
    save,
  }
}
