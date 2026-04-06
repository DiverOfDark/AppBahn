<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { useAuth } from '@/composables/useAuth'

const themeStore = useThemeStore()
const { login } = useAuth()

interface Branding {
  instanceName: string
  tagline: string
  logoUrl: string
  loginButtonText: string
}

const branding = ref<Branding>({
  instanceName: 'AppBahn',
  tagline: 'Deploy and manage your applications',
  logoUrl: '',
  loginButtonText: 'Log in with SSO',
})

const loading = ref(true)

onMounted(async () => {
  try {
    const res = await fetch('/api/v1/admin/config')
    if (res.ok) {
      const config = await res.json()
      if (config?.branding) {
        branding.value = { ...branding.value, ...config.branding }
      }
    }
  } catch {
    // Use defaults
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="login-page">
    <button
      class="theme-toggle"
      :title="`Switch to ${themeStore.theme === 'dark' ? 'light' : 'dark'} mode`"
      @click="themeStore.toggle()"
    >
      <span v-if="themeStore.theme === 'dark'">&#9788;</span>
      <span v-else>&#9790;</span>
    </button>

    <main class="login-card">
      <div class="login-brand">
        <img
          v-if="branding.logoUrl"
          :src="branding.logoUrl"
          :alt="branding.instanceName"
          class="login-logo"
        />
        <div v-else class="login-logo-placeholder">
          <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
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
            >A</text>
          </svg>
        </div>
        <h1 class="login-title">{{ branding.instanceName }}</h1>
        <p class="login-tagline">{{ branding.tagline }}</p>
      </div>

      <button class="login-button" @click="login" :disabled="loading">
        {{ branding.loginButtonText }}
      </button>

      <nav class="login-links">
        <a href="https://appbahn.eu/docs" target="_blank">Documentation</a>
        <span class="login-links-separator">&middot;</span>
        <a href="https://appbahn.eu/docs/api-reference" target="_blank">API Reference</a>
      </nav>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px;
  position: relative;
}

.theme-toggle {
  position: absolute;
  top: 24px;
  right: 24px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 18px;
  transition: border-color 0.15s, color 0.15s;
}

.theme-toggle:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text-primary);
}

.login-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  max-width: 360px;
  width: 100%;
}

.login-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.login-logo {
  height: 48px;
  width: auto;
}

.login-title {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--color-text-primary);
}

.login-tagline {
  font-size: 14px;
  color: var(--color-text-secondary);
  text-align: center;
}

.login-button {
  width: 100%;
  padding: 12px 24px;
  background-color: var(--color-accent);
  color: var(--color-bg-base);
  border: none;
  border-radius: var(--radius-md);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s;
}

.login-button:hover:not(:disabled) {
  background-color: var(--color-accent-hover);
}

.login-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.login-links {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.login-links a {
  color: var(--color-text-tertiary);
  text-decoration: none;
  transition: color 0.15s;
}

.login-links a:hover {
  color: var(--color-text-primary);
}

.login-links-separator {
  color: var(--color-text-tertiary);
}
</style>
