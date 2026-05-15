<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="page-header-text">
      <h1 class="page-header-title">{{ title }}</h1>
      <div v-if="$slots.subtitle" class="page-header-subtitle">
        <slot name="subtitle" />
      </div>
    </div>
    <div class="page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header-text {
  min-width: 0;
}

.page-header-title {
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.page-header-subtitle {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 300;
  color: var(--color-text-secondary);
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
