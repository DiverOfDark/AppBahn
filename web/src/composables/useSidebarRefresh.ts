import { ref } from 'vue'

/** Incremented on every sidebar-relevant mutation to trigger a re-fetch. */
const generation = ref(0)

export function useSidebarRefresh() {
  function refreshSidebar() {
    generation.value++
  }

  return { generation, refreshSidebar }
}
