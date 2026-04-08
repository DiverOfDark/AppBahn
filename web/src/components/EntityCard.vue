<script setup lang="ts">
defineProps<{
  name: string
  slug: string
  to: string
  date?: string
}>()

function formatDate(iso?: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
</script>

<template>
  <router-link :to="to" class="card">
    <div class="card-name">{{ name }}</div>
    <div class="card-meta">
      <span class="card-slug">{{ slug }}</span>
      <span v-if="date" class="card-date">{{ formatDate(date) }}</span>
    </div>
  </router-link>
</template>

<style scoped>
.card {
  display: block;
  padding: 16px;
  background-color: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  text-decoration: none;
  transition: border-color 0.15s;
}

.card:hover {
  border-color: var(--color-border-strong);
}

.card-name {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-slug {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.card-date {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
</style>
