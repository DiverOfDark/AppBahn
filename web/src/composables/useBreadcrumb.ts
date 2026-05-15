import { ref } from 'vue'
import type { RouteLocationRaw } from 'vue-router'

export interface BreadcrumbItem {
  label: string
  to?: string | RouteLocationRaw
}

// Module-level shared state. Pages register their chain via the AppBreadcrumb
// component; the ConsoleLayout's topbar reads this ref directly. Centralising
// the chain in the global chrome matches the design — breadcrumbs belong on
// the outer frame, not duplicated inside each view's body.
const items = ref<BreadcrumbItem[]>([])

export function useBreadcrumb() {
  return {
    items,
    set(next: BreadcrumbItem[]) {
      items.value = next
    },
    clear() {
      items.value = []
    },
  }
}
