<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
import { useAuth } from '@/composables/useAuth'
import { useRouter } from 'vue-router'

const themeStore = useThemeStore()
const { logout } = useAuth()
const router = useRouter()

function handleLogout() {
  logout()
  router.push('/')
}
</script>

<template>
  <div class="console-shell">
    <!-- Top bar -->
    <header class="topbar">
      <div class="topbar-left">
        <router-link to="/console" class="topbar-brand">
          <svg width="24" height="24" viewBox="0 0 40 40" fill="none">
            <rect width="40" height="40" rx="4" fill="var(--color-accent)" />
            <text
              x="50%"
              y="54%"
              dominant-baseline="middle"
              text-anchor="middle"
              fill="var(--color-bg-base)"
              font-family="var(--font-heading)"
              font-weight="700"
              font-size="18"
            >
              A
            </text>
          </svg>
          <span class="topbar-title">AppBahn</span>
        </router-link>
      </div>
      <div class="topbar-right">
        <button
          class="topbar-action"
          :title="`Switch to ${themeStore.theme === 'dark' ? 'light' : 'dark'} mode`"
          @click="themeStore.toggle()"
        >
          <span v-if="themeStore.theme === 'dark'">&#9788;</span>
          <span v-else>&#9790;</span>
        </button>
        <button class="topbar-action" title="Log out" @click="handleLogout">&#x2192;</button>
      </div>
    </header>

    <div class="console-body">
      <!-- Sidebar -->
      <nav class="sidebar">
        <router-link
          to="/console"
          class="sidebar-link"
          active-class="sidebar-link--active"
          :exact="true"
        >
          Workspaces
        </router-link>
        <router-link to="/console/admin" class="sidebar-link" active-class="sidebar-link--active">
          Admin
        </router-link>
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
  padding: 0 16px;
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-bg-surface);
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: var(--color-text-primary);
}

.topbar-title {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.topbar-action {
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 16px;
  transition:
    border-color 0.15s,
    color 0.15s;
}

.topbar-action:hover {
  border-color: var(--color-border);
  color: var(--color-text-primary);
}

/* ── Body ────────────────────────────────────────────────────────────── */

.console-body {
  display: flex;
  flex: 1;
}

/* ── Sidebar ─────────────────────────────────────────────────────────── */

.sidebar {
  width: 200px;
  padding: 8px;
  border-right: 1px solid var(--color-border);
  background-color: var(--color-bg-surface);
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
}

.sidebar-link {
  display: block;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
  text-decoration: none;
  border-left: 2px solid transparent;
  transition:
    color 0.15s,
    border-color 0.15s;
}

.sidebar-link:hover {
  color: var(--color-text-primary);
}

.sidebar-link--active {
  color: var(--color-text-primary);
  border-left-color: var(--color-accent);
}

/* ── Content ─────────────────────────────────────────────────────────── */

.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
