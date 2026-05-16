import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { components } from '@/api/schema'

type Environment = components['schemas']['Environment']

export const useActiveEnvironmentStore = defineStore('activeEnvironment', () => {
  const environment = ref<Environment | null>(null)

  function set(env: Environment | null) {
    environment.value = env
  }

  function clear() {
    environment.value = null
  }

  return { environment, set, clear }
})
