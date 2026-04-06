<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const { handleAuthComplete } = useAuth()

onMounted(() => {
  handleAuthComplete()
  router.replace('/console')
})
</script>

<template>
  <div class="flex items-center justify-center min-h-screen">
    <p class="text-[var(--color-text-secondary)]">Completing login...</p>
  </div>
</template>
