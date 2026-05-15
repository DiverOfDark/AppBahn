<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuth } from '@/composables/useAuth'
import { useBreadcrumb } from '@/composables/useBreadcrumb'
import { getAccessToken } from '@/api/client'
import SidebarTree from '@/components/SidebarTree.vue'

const themeStore = useThemeStore()
const { logout } = useAuth()
const router = useRouter()
const { items: crumbs } = useBreadcrumb()

function handleLogout() {
  logout()
  router.push('/')
}

// Decode the JWT to get the user's email/preferred_username for the avatar.
// We don't have a /users/me endpoint; the OIDC token carries enough for a
// non-authoritative display label.
function decodeJwt(): Record<string, unknown> | null {
  const token = getAccessToken()
  if (!token) return null
  const payload = token.split('.')[1]
  if (!payload) return null
  const b64 =
    payload.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (payload.length % 4)) % 4)
  try {
    // `atob` returns a binary string; non-ASCII claims (e.g. umlauts in
    // `preferred_username`) need a proper UTF-8 decode round-trip.
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

const claims = computed(() => decodeJwt())
const userLabel = computed(() => {
  const c = claims.value
  if (!c) return ''
  return (
    (typeof c.preferred_username === 'string' && c.preferred_username) ||
    (typeof c.email === 'string' && c.email) ||
    (typeof c.sub === 'string' && c.sub) ||
    ''
  )
})
const userInitials = computed(() => {
  const label = userLabel.value
  if (!label) return '··'
  // Email → take first char of local part + first char after first dot/dash.
  const local = label.split('@')[0] ?? label
  const parts = local.split(/[._-]/).filter(Boolean)
  const a = parts[0]
  const b = parts[1]
  if (a && b) return (a.charAt(0) + b.charAt(0)).toUpperCase()
  return local.slice(0, 2).toUpperCase()
})

// User-menu popover state. Close on outside click / Escape.
const menuOpen = ref(false)
const menuRef = ref<HTMLElement | null>(null)
function toggleMenu() {
  menuOpen.value = !menuOpen.value
}
function handleClickOutside(event: MouseEvent) {
  if (menuOpen.value && menuRef.value && !menuRef.value.contains(event.target as Node)) {
    menuOpen.value = false
  }
}
function handleEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && menuOpen.value) menuOpen.value = false
}
onMounted(() => {
  document.addEventListener('mousedown', handleClickOutside)
  document.addEventListener('keydown', handleEscape)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleClickOutside)
  document.removeEventListener('keydown', handleEscape)
})
</script>

<template>
  <div class="console-shell">
    <!-- Top bar — global chrome, breadcrumb is registered by pages via the
         useBreadcrumb composable (see AppBreadcrumb.vue). -->
    <header class="topbar">
      <div class="topbar-left">
        <router-link to="/console" class="topbar-brand" aria-label="AppBahn home">
          <img src="/logo.png" alt="AppBahn" class="topbar-logo" />
        </router-link>
        <nav v-if="crumbs.length" class="bcrumb" aria-label="Breadcrumb">
          <template v-for="(item, index) in crumbs" :key="index">
            <RouterLink v-if="item.to" :to="item.to" class="bcrumb-link">
              {{ item.label }}
            </RouterLink>
            <span v-else class="bcrumb-current" aria-current="page">{{ item.label }}</span>
            <span v-if="index < crumbs.length - 1" class="bcrumb-sep">/</span>
          </template>
        </nav>
      </div>
      <div ref="menuRef" class="topbar-right">
        <button
          type="button"
          class="topbar-avatar"
          :aria-label="userLabel || 'User menu'"
          :title="userLabel"
          @click="toggleMenu"
        >
          {{ userInitials }}
        </button>
        <div v-if="menuOpen" class="topbar-menu" role="menu">
          <div v-if="userLabel" class="topbar-menu-label" :title="userLabel">
            {{ userLabel }}
          </div>
          <button
            type="button"
            class="topbar-menu-item"
            role="menuitem"
            @click="(themeStore.toggle(), (menuOpen = false))"
          >
            {{ themeStore.theme === 'dark' ? 'Light mode' : 'Dark mode' }}
          </button>
          <button
            type="button"
            class="topbar-menu-item"
            role="menuitem"
            @click="(handleLogout(), (menuOpen = false))"
          >
            Log out
          </button>
        </div>
      </div>
    </header>

    <div class="console-body">
      <!-- Sidebar -->
      <nav class="sidebar">
        <SidebarTree />
      </nav>

      <!-- Main content -->
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.console-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* ── Top bar ─────────────────────────────────────────────────────────── */

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-bg-surface);
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.topbar-brand {
  display: flex;
  align-items: center;
  text-decoration: none;
}
.topbar-logo {
  height: 20px;
  width: auto;
}

/* Breadcrumb — sits inline next to the logo per design. */
.bcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  min-width: 0;
  overflow: hidden;
}
.bcrumb-sep {
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-size: 12px;
}
.bcrumb-link {
  color: var(--color-text-secondary);
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 220px;
}
.bcrumb-link:hover {
  color: var(--color-text-primary);
}
.bcrumb-current {
  color: var(--color-text-primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.topbar-right {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
}

.topbar-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-accent);
  color: var(--color-accent-fg, oklch(20% 0.02 80));
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-heading);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  letter-spacing: 0.02em;
}
.topbar-avatar:hover {
  filter: brightness(1.08);
}

.topbar-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 180px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 24px oklch(0% 0 0 / 0.35);
  padding: 6px;
  z-index: 50;
  display: flex;
  flex-direction: column;
}
.topbar-menu-label {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.04em;
  padding: 6px 10px 8px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.topbar-menu-item {
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  text-align: left;
  padding: 7px 10px;
  font-size: 13px;
  font-family: var(--font-body);
  cursor: pointer;
  border-radius: var(--radius-sm);
}
.topbar-menu-item:hover {
  background: var(--color-bg-raised, var(--color-bg-base));
  color: var(--color-text-primary);
}

/* ── Body ────────────────────────────────────────────────────────────── */

.console-body {
  display: flex;
  flex: 1;
}

/* ── Sidebar ─────────────────────────────────────────────────────────── */

.sidebar {
  width: 240px;
  padding: 8px;
  border-right: 1px solid var(--color-border);
  background-color: var(--color-bg-surface);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow-y: auto;
}

/* ── Content ─────────────────────────────────────────────────────────── */

.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
