<script setup lang="ts">
defineProps<{
  title: string
  open: boolean
  loading: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: []
}>()

function onOverlayClick(e: MouseEvent) {
  if (e.target === e.currentTarget) {
    emit('close')
  }
}

function onSubmit(e: Event) {
  e.preventDefault()
  emit('submit')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="dialog-overlay" @click="onOverlayClick">
      <div class="dialog-panel">
        <div class="dialog-header">
          <h2 class="dialog-title">{{ title }}</h2>
          <button class="dialog-close" @click="$emit('close')" title="Close">&#x2715;</button>
        </div>
        <form @submit="onSubmit">
          <div class="dialog-body">
            <slot />
          </div>
          <div class="dialog-footer">
            <button type="button" class="dialog-btn dialog-btn--secondary" @click="$emit('close')">
              Cancel
            </button>
            <button type="submit" class="dialog-btn dialog-btn--primary" :disabled="loading">
              <span v-if="loading">Creating...</span>
              <span v-else>Create</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background-color: oklch(0% 0 0 / 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: 16px;
}

.dialog-panel {
  background-color: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  width: 100%;
  max-width: 420px;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.dialog-title {
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.dialog-close {
  background: transparent;
  border: none;
  color: var(--color-text-tertiary);
  cursor: pointer;
  font-size: 14px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  transition: color 0.15s;
}

.dialog-close:hover {
  color: var(--color-text-primary);
}

.dialog-body {
  padding: 20px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}

.dialog-btn {
  padding: 8px 16px;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    background-color 0.15s,
    border-color 0.15s;
}

.dialog-btn--secondary {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.dialog-btn--secondary:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text-primary);
}

.dialog-btn--primary {
  background-color: var(--color-accent);
  color: var(--color-bg-base);
  border: none;
}

.dialog-btn--primary:hover:not(:disabled) {
  background-color: var(--color-accent-hover);
}

.dialog-btn--primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
