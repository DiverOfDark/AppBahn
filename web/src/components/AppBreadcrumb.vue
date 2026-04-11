<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'

defineProps<{
  items: Array<{ label: string; to?: string | RouteLocationRaw }>
}>()
</script>

<template>
  <nav class="breadcrumb" aria-label="Breadcrumb">
    <template v-for="(item, index) in items" :key="index">
      <router-link v-if="item.to" :to="item.to" class="breadcrumb-link">{{
        item.label
      }}</router-link>
      <span v-else class="breadcrumb-current" aria-current="page">{{ item.label }}</span>
      <span v-if="index < items.length - 1" class="breadcrumb-sep">/</span>
    </template>
  </nav>
</template>
