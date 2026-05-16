import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api/client'
import type { components } from '@/api/schema'

type PlatformConfig = components['schemas']['PlatformConfig']

export const usePlatformConfigStore = defineStore('platformConfig', () => {
  const config = ref<PlatformConfig | null>(null)
  const loaded = ref(false)

  async function load() {
    if (loaded.value) return
    try {
      const { data } = await api.GET('/admin/config')
      if (data) config.value = data
    } catch {
      // Non-fatal: UI degrades gracefully without config
    } finally {
      loaded.value = true
    }
  }

  return { config, loaded, load }
})
