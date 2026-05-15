<script setup lang="ts">
/**
 * Liveness/readiness probe builder used by CreateResourceView and the
 * Settings tab. The same probe shape applies to both — Kubernetes treats
 * them separately but for the 95% case a single editor is the right UX.
 *
 * Two-way binds a single {@link HealthCheckState} object so the caller can
 * either spread it into a top-level draft or store it as a nested field. The
 * caller is responsible for translating this state into the API's
 * {@code ProbeConfig} (different shape — httpGet / tcpSocket / exec union).
 */
export type ProbeMode = 'http' | 'tcp' | 'exec'

export interface HealthCheckState {
  enabled: boolean
  mode: ProbeMode
  path: string
  port: number | undefined
  command: string
  initialDelay: number
  period: number
  failureThreshold: number
}

defineProps<{
  /** Placeholder for the probe-port input — typically the first listener port. */
  defaultPort?: number
}>()

const state = defineModel<HealthCheckState>({ required: true })
</script>

<template>
  <div class="hc-panel">
    <div class="field">
      <div class="field-l"><span class="lbl">Enabled</span></div>
      <div class="field-c">
        <label class="hc-toggle">
          <input v-model="state.enabled" type="checkbox" />
          <span>{{ state.enabled ? 'on' : 'off' }}</span>
        </label>
      </div>
    </div>
    <template v-if="state.enabled">
      <div class="field">
        <div class="field-l"><span class="lbl">Transport</span></div>
        <div class="field-c">
          <div class="seg">
            <button
              type="button"
              :class="{ on: state.mode === 'http' }"
              @click="state.mode = 'http'"
            >
              HTTP
            </button>
            <button type="button" :class="{ on: state.mode === 'tcp' }" @click="state.mode = 'tcp'">
              TCP
            </button>
            <button
              type="button"
              :class="{ on: state.mode === 'exec' }"
              @click="state.mode = 'exec'"
            >
              Exec
            </button>
          </div>
        </div>
      </div>
      <div v-if="state.mode === 'http'" class="field">
        <div class="field-l">
          <span class="lbl">Path</span>
          <span class="desc">GET …</span>
        </div>
        <div class="field-c">
          <input v-model="state.path" class="form-input" placeholder="/health" />
        </div>
      </div>
      <div v-if="state.mode === 'http' || state.mode === 'tcp'" class="field">
        <div class="field-l">
          <span class="lbl">Probe port</span>
          <span class="desc">defaults to first port</span>
        </div>
        <div class="field-c">
          <input
            v-model.number="state.port"
            type="number"
            class="form-input field-num"
            min="1"
            max="65535"
            :placeholder="defaultPort != null ? String(defaultPort) : '80'"
          />
        </div>
      </div>
      <div v-if="state.mode === 'exec'" class="field">
        <div class="field-l">
          <span class="lbl">Command</span>
          <span class="desc">space-separated</span>
        </div>
        <div class="field-c">
          <input v-model="state.command" class="form-input" placeholder="cat /tmp/healthy" />
        </div>
      </div>
      <div class="field">
        <div class="field-l">
          <span class="lbl">Timing</span>
          <span class="desc">delay · period · fails</span>
        </div>
        <div class="field-c">
          <div class="row">
            <input
              v-model.number="state.initialDelay"
              type="number"
              class="form-input field-num"
              min="0"
              aria-label="Initial delay seconds"
            />
            <span class="hint mono">s delay</span>
            <input
              v-model.number="state.period"
              type="number"
              class="form-input field-num"
              min="1"
              aria-label="Period seconds"
            />
            <span class="hint mono">s period</span>
            <input
              v-model.number="state.failureThreshold"
              type="number"
              class="form-input field-num"
              min="1"
              aria-label="Failure threshold"
            />
            <span class="hint mono">fails</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
@import '@/assets/form-fields.css';

.seg {
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.seg button {
  padding: 7px 14px;
  background: var(--color-bg-base);
  color: var(--color-text-tertiary);
  border: none;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  cursor: pointer;
  border-right: 1px solid var(--color-border);
  transition: color 120ms ease;
}
.seg button:last-child {
  border-right: none;
}
.seg button.on {
  background: var(--color-bg-raised);
  color: var(--color-accent);
}
.seg button:hover:not(.on) {
  color: var(--color-text-secondary);
}
.hc-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.hc-toggle input[type='checkbox'] {
  accent-color: var(--color-accent);
  width: 14px;
  height: 14px;
}
</style>
