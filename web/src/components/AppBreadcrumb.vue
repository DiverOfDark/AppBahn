<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { useBreadcrumb, type BreadcrumbItem } from '@/composables/useBreadcrumb'

const props = defineProps<{
  items: Array<{ label: string; to?: string | RouteLocationRaw }>
}>()

const { set, clear, items: storeItems } = useBreadcrumb()

// Register the page's chain with the shared store; ConsoleLayout's topbar
// renders the actual breadcrumb in the outer frame (per design). This
// component renders nothing inline.
//
// `buildBreadcrumbChain(...)` callers typically pass a freshly built array
// every parent render, so the watcher fires on every poll tick. Deep-equal
// against the current store value before calling `set()` to avoid restamping
// an identical array — that would otherwise re-render ConsoleLayout's topbar
// on every 30s resource poll.
watch(
  () => props.items as BreadcrumbItem[],
  (next) => {
    if (JSON.stringify(next) === JSON.stringify(storeItems.value)) return
    set(next)
  },
  { immediate: true, deep: true },
)

onBeforeUnmount(() => {
  // Clear so the next route's mount-time set() lands on an empty slate.
  // Pages register their chain in setup/onMounted, so the gap is invisible.
  clear()
})
</script>

<template>
  <!-- Page-level usage only registers items in the shared store; ConsoleLayout's
       topbar renders the real breadcrumb. The hidden span keeps Vue's template
       parser happy (it requires a root element) without affecting layout. -->
  <span aria-hidden="true" class="breadcrumb-portal"></span>
</template>

<style scoped>
.breadcrumb-portal {
  display: none;
}
</style>
