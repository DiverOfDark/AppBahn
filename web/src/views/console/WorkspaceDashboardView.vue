<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <div>
    <h1 class="text-xl font-semibold mb-6" style="font-family: var(--font-heading)">
      Workspace: {{ route.params.wsSlug }}
    </h1>
    <p class="text-sm" style="color: var(--color-text-secondary)">
      Dashboard coming in a future sprint.
    </p>
  </div>
</template>
