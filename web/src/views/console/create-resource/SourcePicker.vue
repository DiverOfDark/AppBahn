<script setup lang="ts">
import type { SourceKind } from '@/composables/resource/useResourceCreateForm'

const source = defineModel<SourceKind>({ required: true })
</script>

<template>
  <section class="section">
    <header class="slabel">
      <div>
        <h2>01 · Source</h2>
        <p class="slabel-desc">
          Where the artifact comes from. Pull a Docker image, or promote a build already running in
          another environment of this project.
        </p>
      </div>
    </header>
    <div class="source-cards">
      <button
        type="button"
        class="src-card"
        :class="{ on: source === 'docker' }"
        @click="source = 'docker'"
      >
        <div class="src-h">
          <div class="src-ic dock">D</div>
        </div>
        <div class="src-name">Docker image</div>
        <div class="src-sub">
          Pull a prebuilt image from any OCI registry — DockerHub, GHCR, Harbor, ECR.
        </div>
        <div class="src-meta"><span>oci</span><span>private</span><span>tag/digest</span></div>
      </button>

      <button
        type="button"
        class="src-card"
        :class="{ on: source === 'promote' }"
        @click="source = 'promote'"
      >
        <div class="src-h">
          <div class="src-ic env">↥</div>
        </div>
        <div class="src-name">Promote from environment</div>
        <div class="src-sub">
          Reuse a build already running in another environment. Same image, new env config.
        </div>
        <div class="src-meta"><span>same image</span><span>fast</span><span>pin / track</span></div>
      </button>

      <div class="src-card disabled">
        <div class="src-h">
          <div class="src-ic git">GH</div>
          <span class="src-soon">Coming soon</span>
        </div>
        <div class="src-name">Git repository</div>
        <div class="src-sub">
          Connect a Git provider (GitHub / GitLab) or a custom URL. Build runs on the cluster.
        </div>
        <div class="src-meta"><span>https</span><span>ssh</span></div>
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

.source-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.src-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-surface);
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: inherit;
  transition: border-color 120ms ease;
  position: relative;
  min-height: 150px;
}
.src-card:hover:not(.disabled) {
  border-color: var(--color-border-strong);
}
.src-card.on {
  border-color: var(--color-accent);
  background: oklch(20% 0.04 80 / 0.35);
}
.src-card.on::after {
  content: '✓';
  position: absolute;
  top: 14px;
  right: 16px;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 13px;
}
.src-card.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.src-h {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.src-ic {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.src-ic.git {
  background: oklch(22% 0.05 250);
}
.src-ic.dock {
  background: oklch(22% 0.05 230);
}
.src-ic.env {
  background: oklch(20% 0.04 80);
  color: var(--color-accent);
}
.src-name {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--color-text-primary);
}
.src-sub {
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.5;
  flex: 1;
}
.src-meta {
  display: flex;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  margin-top: auto;
}
.src-meta span {
  padding: 2px 6px;
  background: var(--color-bg-base);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
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
