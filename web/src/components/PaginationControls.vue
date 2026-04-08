<script setup lang="ts">
defineProps<{
  page: number
  totalPages: number
}>()

defineEmits<{
  'update:page': [value: number]
}>()
</script>

<template>
  <div v-if="totalPages > 1" class="pagination">
    <button
      class="pagination-btn"
      :disabled="page <= 0"
      @click="$emit('update:page', page - 1)"
    >
      &larr; Previous
    </button>
    <span class="pagination-info">
      Page {{ page + 1 }} of {{ totalPages }}
    </span>
    <button
      class="pagination-btn"
      :disabled="page >= totalPages - 1"
      @click="$emit('update:page', page + 1)"
    >
      Next &rarr;
    </button>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px 0;
}

.pagination-btn {
  padding: 6px 14px;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}

.pagination-btn:hover:not(:disabled) {
  border-color: var(--color-border-strong);
  color: var(--color-text-primary);
}

.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagination-info {
  font-size: 13px;
  color: var(--color-text-tertiary);
}
</style>
