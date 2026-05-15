<script setup lang="ts">
import { computed } from 'vue'
import PortRowsEditor, { type PortRow } from '@/components/resource/PortRowsEditor.vue'
import EnvVarsEditor, { type EnvVarRow } from '@/components/resource/EnvVarsEditor.vue'
import HealthCheckEditor, {
  type HealthCheckState,
} from '@/components/resource/HealthCheckEditor.vue'
import type { SourceKind } from '@/composables/resource/useResourceCreateForm'

const props = defineProps<{
  source: SourceKind
  projSlug: string
  envSlug: string
  fullImage: string
}>()

const name = defineModel<string>('name', { required: true })
const image = defineModel<string>('image', { required: true })
const tag = defineModel<string>('tag', { required: true })
const cpu = defineModel<number>('cpu', { required: true })
const memory = defineModel<number>('memory', { required: true })
const minReplicas = defineModel<number>('minReplicas', { required: true })
const maxReplicas = defineModel<number | undefined>('maxReplicas', { required: true })
const ports = defineModel<PortRow[]>('ports', { required: true })
const envVars = defineModel<EnvVarRow[]>('envVars', { required: true })
const health = defineModel<HealthCheckState>('health', { required: true })

const firstPort = computed(() => ports.value[0]?.port)
const isDocker = computed(() => props.source === 'docker')
</script>

<template>
  <section class="section">
    <header class="slabel">
      <div>
        <h2>03 · Configuration</h2>
        <p class="slabel-desc">Identity, build, runtime, networking.</p>
      </div>
    </header>

    <div class="panel">
      <div class="panel-h">
        <h3>Identity</h3>
        <p>Names this resource and how it appears in the console and CLI.</p>
      </div>
      <div class="panel-body">
        <div class="field">
          <div class="field-l">
            <span class="lbl">Name <span class="req">REQUIRED</span></span>
            <span class="desc">a-z 0-9 -</span>
          </div>
          <div class="field-c">
            <input v-model="name" class="form-input" placeholder="my-nginx" required />
            <span v-if="name" class="hint">
              Final identifier ·
              <span class="mono">
                {{ projSlug }} / {{ envSlug }} /
                <b class="accent">{{ name }}</b>
              </span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="isDocker" class="panel">
      <div class="panel-h">
        <h3>Build</h3>
        <p>The image to pull. Tag or digest both work.</p>
      </div>
      <div class="panel-body">
        <div class="field">
          <div class="field-l">
            <span class="lbl">Image <span class="req">REQUIRED</span></span>
            <span class="desc">repo/path</span>
          </div>
          <div class="field-c">
            <input v-model="image" class="form-input" placeholder="nginx" required />
          </div>
        </div>
        <div class="field">
          <div class="field-l">
            <span class="lbl">Tag</span>
            <span class="desc">or digest</span>
          </div>
          <div class="field-c">
            <input v-model="tag" class="form-input" placeholder="latest" />
            <span v-if="fullImage" class="hint mono">
              ▸ pulling <b class="accent">{{ fullImage }}</b>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-h">
        <h3>Runtime &amp; networking</h3>
        <p>How many copies and how it's reached. Editable post-deploy.</p>
      </div>
      <div class="panel-body">
        <div class="field">
          <div class="field-l">
            <span class="lbl">CPU</span>
            <span class="desc">millicores</span>
          </div>
          <div class="field-c">
            <div class="row">
              <input
                v-model.number="cpu"
                type="number"
                class="form-input field-num"
                min="50"
                step="50"
              />
              <span class="hint mono">m</span>
            </div>
          </div>
        </div>
        <div class="field">
          <div class="field-l">
            <span class="lbl">Memory</span>
            <span class="desc">megabytes</span>
          </div>
          <div class="field-c">
            <div class="row">
              <input
                v-model.number="memory"
                type="number"
                class="form-input field-num"
                min="64"
                step="64"
              />
              <span class="hint mono">Mi</span>
            </div>
          </div>
        </div>
        <div class="field">
          <div class="field-l">
            <span class="lbl">Replicas</span>
            <span class="desc">min · max</span>
          </div>
          <div class="field-c">
            <div class="row">
              <input
                v-model.number="minReplicas"
                type="number"
                class="form-input field-num"
                min="1"
                max="50"
                aria-label="Minimum replicas"
              />
              <span class="hint mono">→</span>
              <input
                v-model.number="maxReplicas"
                type="number"
                class="form-input field-num"
                min="1"
                max="50"
                placeholder="auto"
                aria-label="Maximum replicas (optional)"
              />
              <span class="hint">leave max blank to fix at min</span>
            </div>
          </div>
        </div>
        <div class="field">
          <div class="field-l">
            <span class="lbl">Ports</span>
            <span class="desc">listen + visibility per port</span>
          </div>
          <div class="field-c">
            <PortRowsEditor v-model="ports" />
            <span class="hint">
              Public → HTTP Ingress with auto-domain · TCP → load-balancer for raw TCP · Private →
              in-cluster only.
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Environment variables — plain string→string for now. The design's
         REF/VAL toggle + resource-output picker depends on backend support
         that doesn't exist yet (see spec/TECHDEBT.md / Create-resource). -->
    <div class="panel">
      <div class="panel-h">
        <h3>Environment variables</h3>
        <p>Key/value pairs injected into the running container.</p>
      </div>
      <div class="panel-body">
        <EnvVarsEditor v-model="envVars" />
      </div>
    </div>

    <!-- Health check — applied to BOTH liveness and readiness probes.
         Startup probes and per-probe customization are out of scope here. -->
    <div class="panel">
      <div class="panel-h">
        <h3>Health check</h3>
        <p>Liveness + readiness probe applied to every pod.</p>
      </div>
      <div class="panel-body">
        <HealthCheckEditor v-model="health" :default-port="firstPort" />
      </div>
    </div>
  </section>
</template>

<style scoped>
@import '@/assets/form-fields.css';

.section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.slabel {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}
.slabel h2 {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
  letter-spacing: -0.01em;
}
.slabel-desc {
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 4px 0 0;
  max-width: 540px;
  line-height: 1.5;
}
.section .panel + .panel {
  margin-top: 14px;
}
</style>
