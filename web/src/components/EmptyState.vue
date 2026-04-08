<script setup lang="ts">
defineProps<{
  message: string
}>()
</script>

<template>
  <div class="empty-state">
    <svg class="empty-state-icon" width="48" height="48" viewBox="0 0 48 48" fill="none">
      <rect
        x="4"
        y="8"
        width="40"
        height="32"
        rx="3"
        stroke="currentColor"
        stroke-width="1.5"
        fill="none"
      />
      <line x1="4" y1="16" x2="44" y2="16" stroke="currentColor" stroke-width="1.5" />
      <circle cx="10" cy="12" r="1.5" fill="currentColor" />
      <circle cx="16" cy="12" r="1.5" fill="currentColor" />
      <circle cx="22" cy="12" r="1.5" fill="currentColor" />
    </svg>
    <p class="empty-state-message">{{ message }}</p>
    <div class="empty-state-action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.empty-state-icon {
  color: var(--color-text-tertiary);
  margin-bottom: 16px;
}

.empty-state-message {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 16px;
  max-width: 320px;
}

.empty-state-action {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
