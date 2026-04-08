<script setup lang="ts">
</script>

<template>
  <div class="table-wrapper">
    <table class="data-table">
      <thead>
        <tr>
          <slot name="header" />
        </tr>
      </thead>
      <tbody>
        <slot name="body" />
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-wrapper {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table :deep(th) {
  text-align: left;
  padding: 10px 16px;
  font-family: var(--font-heading);
  font-weight: 600;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-tertiary);
  border-bottom: 1px solid var(--color-border);
}

.data-table :deep(td) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.data-table :deep(.table-row-link) {
  cursor: pointer;
  transition: background-color 0.1s;
}

.data-table :deep(.table-row-link:hover) {
  background-color: var(--color-bg-raised);
}

.data-table :deep(.cell-name) {
  font-weight: 500;
  color: var(--color-text-primary);
}

.data-table :deep(.cell-slug) {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.data-table :deep(.cell-date) {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.data-table :deep(.cell-type) {
  color: var(--color-text-secondary);
}

.data-table :deep(.cell-cluster) {
  color: var(--color-text-secondary);
}
</style>
