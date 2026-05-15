<script setup lang="ts">
/**
 * Resource Settings tab — sub-nav on the left, form panels on the right,
 * sticky save bar. Draft lifecycle lives in `useResourceDraft`; the danger
 * zone is its own component. Both keep this file focused on the template.
 */
import { ref, toRef } from 'vue'
import type { components } from '@/api/schema'
import PortRowsEditor from '@/components/resource/PortRowsEditor.vue'
import HealthCheckEditor from '@/components/resource/HealthCheckEditor.vue'
import ResourceDangerZone from '@/components/resource/ResourceDangerZone.vue'
import { useResourceDraft } from '@/composables/resource/useResourceDraft'

type Resource = components['schemas']['Resource']
type PendingActionKind = 'pause' | 'resume' | 'restart'

const props = defineProps<{
  resource: Resource
  pendingAction: PendingActionKind | null
  effectiveStopped: boolean
  onDelete: () => Promise<void>
}>()

const emit = defineEmits<{
  (e: 'saved'): void
  (e: 'restart'): void
  (e: 'pause'): void
  (e: 'resume'): void
}>()

const settingsActive = ref<string>('general')
const { draft, isDirty, dirtyCount, fullImage, saving, error, savedAt, discard, save } =
  useResourceDraft(toRef(props, 'resource'), () => emit('saved'))
</script>

<template>
  <section v-if="draft" class="settings-grid">
    <nav class="snav">
      <span class="snav-label">Resource</span>
      <a
        href="#general"
        :class="{ on: settingsActive === 'general' }"
        @click.prevent="settingsActive = 'general'"
        >General</a
      >
      <a
        href="#build"
        :class="{ on: settingsActive === 'build' }"
        @click.prevent="settingsActive = 'build'"
        >Build &amp; image</a
      >
      <a
        href="#runtime"
        :class="{ on: settingsActive === 'runtime' }"
        @click.prevent="settingsActive = 'runtime'"
        >Runtime</a
      >
      <a
        href="#scaling"
        :class="{ on: settingsActive === 'scaling' }"
        @click.prevent="settingsActive = 'scaling'"
        >Scaling</a
      >
      <a
        href="#health"
        :class="{ on: settingsActive === 'health' }"
        @click.prevent="settingsActive = 'health'"
        >Health checks</a
      >
      <a
        href="#networking"
        :class="{ on: settingsActive === 'networking' }"
        @click.prevent="settingsActive = 'networking'"
        >Networking</a
      >
      <span class="snav-label">Danger</span>
      <a
        href="#danger"
        :class="{ on: settingsActive === 'danger' }"
        class="snav-danger"
        @click.prevent="settingsActive = 'danger'"
        >Pause / delete</a
      >
    </nav>

    <div class="sections">
      <div v-if="error" class="error-banner">{{ error }}</div>

      <div v-show="settingsActive === 'general'" class="panel">
        <div class="panel-h">
          <h3>General</h3>
          <p>Identity for this resource.</p>
        </div>
        <div class="panel-body">
          <div class="field">
            <div class="field-l">
              <span class="lbl">Name</span>
              <span class="desc">used in URLs</span>
            </div>
            <div class="field-c">
              <input v-model="draft.name" class="form-input" />
            </div>
          </div>
          <div class="field">
            <div class="field-l">
              <span class="lbl">Slug</span>
              <span class="desc">cannot change</span>
            </div>
            <div class="field-c">
              <input :value="resource.slug" class="form-input" disabled style="opacity: 0.5" />
            </div>
          </div>
          <div class="field">
            <div class="field-l">
              <span class="lbl">Type</span>
              <span class="desc">cannot change</span>
            </div>
            <div class="field-c">
              <input :value="resource.type" class="form-input" disabled style="opacity: 0.5" />
            </div>
          </div>
        </div>
      </div>

      <div v-show="settingsActive === 'build'" class="panel">
        <div class="panel-h">
          <h3>Build &amp; image</h3>
          <p>The container image to pull. Tag or digest both work.</p>
        </div>
        <div class="panel-body">
          <div class="field">
            <div class="field-l">
              <span class="lbl">Image</span>
              <span class="desc">repo/path</span>
            </div>
            <div class="field-c">
              <input v-model="draft.image" class="form-input" placeholder="nginx" />
            </div>
          </div>
          <div class="field">
            <div class="field-l">
              <span class="lbl">Tag</span>
              <span class="desc">or digest</span>
            </div>
            <div class="field-c">
              <input v-model="draft.tag" class="form-input" placeholder="latest" />
              <span v-if="fullImage" class="hint mono">
                ▸ pulling <b class="accent">{{ fullImage }}</b>
              </span>
            </div>
          </div>
        </div>
      </div>

      <div v-show="settingsActive === 'runtime'" class="panel">
        <div class="panel-h">
          <h3>Runtime</h3>
          <p>Per-pod resources. Total = replicas × per-pod.</p>
        </div>
        <div class="panel-body">
          <div class="field">
            <div class="field-l">
              <span class="lbl">CPU per pod</span>
              <span class="desc">millicores</span>
            </div>
            <div class="field-c">
              <div class="row">
                <input
                  v-model.number="draft.cpu"
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
              <span class="lbl">Memory per pod</span>
              <span class="desc">megabytes</span>
            </div>
            <div class="field-c">
              <div class="row">
                <input
                  v-model.number="draft.memory"
                  type="number"
                  class="form-input field-num"
                  min="64"
                  step="64"
                />
                <span class="hint mono">Mi</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-show="settingsActive === 'scaling'" class="panel">
        <div class="panel-h">
          <h3>Scaling</h3>
          <p>Replica count.</p>
        </div>
        <div class="panel-body">
          <div class="field">
            <div class="field-l"><span class="lbl">Replicas (min / max)</span></div>
            <div class="field-c">
              <div class="row">
                <input
                  v-model.number="draft.minReplicas"
                  type="number"
                  class="form-input field-num"
                  min="1"
                  max="50"
                  aria-label="Minimum replicas"
                />
                <span class="hint mono">→</span>
                <input
                  v-model.number="draft.maxReplicas"
                  type="number"
                  class="form-input field-num"
                  min="1"
                  max="50"
                  placeholder="auto"
                  aria-label="Maximum replicas"
                />
                <span class="hint">leave max blank to fix at min</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-show="settingsActive === 'health'" class="panel">
        <div class="panel-h">
          <h3>Health checks</h3>
          <p>Liveness + readiness probe applied to every pod.</p>
        </div>
        <div class="panel-body">
          <HealthCheckEditor v-model="draft.health" :default-port="draft.ports[0]?.port" />
        </div>
      </div>

      <div v-show="settingsActive === 'networking'" class="panel">
        <div class="panel-h">
          <h3>Networking</h3>
          <p>Listen ports and visibility per port.</p>
        </div>
        <div class="panel-body">
          <div class="field">
            <div class="field-l">
              <span class="lbl">Ports</span>
              <span class="desc">listen + visibility</span>
            </div>
            <div class="field-c">
              <PortRowsEditor v-model="draft.ports" />
            </div>
          </div>
        </div>
      </div>

      <ResourceDangerZone
        v-show="settingsActive === 'danger'"
        :resource="resource"
        :pending-action="pendingAction"
        :effective-stopped="effectiveStopped"
        :on-delete="onDelete"
        @pause="emit('pause')"
        @resume="emit('resume')"
        @restart="emit('restart')"
      />

      <div v-if="settingsActive !== 'danger'" class="settings-foot">
        <span v-if="isDirty" class="settings-changes">
          ● {{ dirtyCount }} unsaved {{ dirtyCount === 1 ? 'change' : 'changes' }}
        </span>
        <span v-else-if="savedAt" class="settings-changes settings-saved">✓ Saved</span>
        <span v-else class="settings-changes settings-saved">No changes</span>
        <div class="settings-actions">
          <button
            type="button"
            class="btn-secondary"
            :disabled="!isDirty || saving"
            @click="discard"
          >
            Discard
          </button>
          <button type="button" class="btn-primary" :disabled="!isDirty || saving" @click="save">
            {{ saving ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
@import '@/assets/form-fields.css';

.settings-grid {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 24px;
  align-items: start;
  padding-bottom: 16px;
}
@media (max-width: 1100px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
.snav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-self: start;
  position: sticky;
  top: 16px;
}
.snav a {
  padding: 7px 12px;
  font-size: 13px;
  color: var(--color-text-tertiary);
  text-decoration: none;
  border-left: 2px solid transparent;
  cursor: pointer;
}
.snav a:hover {
  color: var(--color-text-primary);
}
.snav a.on {
  color: var(--color-text-primary);
  border-left-color: var(--color-accent);
  background: oklch(20% 0.04 80 / 0.25);
}
.snav-label {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  padding: 14px 12px 6px;
}
.snav a.snav-danger,
.snav a.snav-danger.on,
.snav a.snav-danger:hover {
  color: var(--color-status-error);
}
.snav a.snav-danger:hover {
  background: oklch(20% 0.04 25 / 0.3);
}
.snav a.snav-danger.on {
  background: oklch(20% 0.04 25 / 0.35);
  border-left-color: var(--color-status-error);
}

.sections {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}
.panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
}
.panel-h {
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: flex-start;
}
.panel-h h3 {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  margin: 0;
}
.panel-h p {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0;
  font-weight: 300;
}
.panel-body {
  padding: 4px 18px;
}

.settings-foot {
  position: sticky;
  bottom: 0;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  z-index: 1;
}
.settings-changes {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-accent);
  letter-spacing: 0.04em;
}
.settings-changes.settings-saved {
  color: var(--color-text-tertiary);
}
.settings-actions {
  display: flex;
  gap: 8px;
}
</style>
