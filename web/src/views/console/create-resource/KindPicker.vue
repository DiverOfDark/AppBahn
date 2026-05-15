<script setup lang="ts">
import type { Kind } from '@/composables/resource/useResourceCreateForm'

const kind = defineModel<Kind>({ required: true })
</script>

<template>
  <section class="section">
    <header class="slabel">
      <div>
        <h2>02 · Kind</h2>
        <p class="slabel-desc">Two execution models — pick the one that matches.</p>
      </div>
    </header>
    <div class="kind-cards">
      <button
        type="button"
        class="kind-card"
        :class="{ on: kind === 'resource' }"
        @click="kind = 'resource'"
      >
        <div class="kind-gly">▸</div>
        <div class="kind-body">
          <div class="kind-nm">Resource</div>
          <div class="kind-dsc">
            Long-running process. HTTP, gRPC, TCP, or a worker that holds connections. Replicas,
            rolling deploys, health checks.
          </div>
        </div>
        <div class="kind-ck">✓</div>
      </button>
      <div class="kind-card disabled">
        <div class="kind-gly">◷</div>
        <div class="kind-body">
          <div class="kind-nm">Cronjob <span class="src-soon">Coming soon</span></div>
          <div class="kind-dsc">Runs on a schedule. Each invocation is a one-shot container.</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
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

.kind-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.kind-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  padding: 18px;
  display: grid;
  grid-template-columns: 36px 1fr auto;
  gap: 14px;
  cursor: pointer;
  align-items: center;
  text-align: left;
  font-family: inherit;
  color: inherit;
  transition: border-color 120ms ease;
}
.kind-card:hover:not(.disabled) {
  border-color: var(--color-border-strong);
}
.kind-card.on {
  border-color: var(--color-accent);
  background: oklch(20% 0.04 80 / 0.35);
}
.kind-card.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.kind-gly {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 18px;
  color: var(--color-accent);
}
.kind-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.kind-nm {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}
.kind-dsc {
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.45;
}
.kind-ck {
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 13px;
  opacity: 0;
}
.kind-card.on .kind-ck {
  opacity: 1;
}

.src-soon {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
  border: 1px solid var(--color-border);
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-base);
}
</style>
