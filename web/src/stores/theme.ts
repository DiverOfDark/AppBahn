import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type Theme = 'dark' | 'light'

const STORAGE_KEY = 'appbahn-theme'

function readTheme(): Theme {
  try {
    return (localStorage.getItem(STORAGE_KEY) as Theme) ?? 'dark'
  } catch {
    return 'dark'
  }
}

function writeTheme(t: Theme) {
  try {
    localStorage.setItem(STORAGE_KEY, t)
  } catch {
    // localStorage unavailable
  }
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<Theme>(readTheme())

  function apply(t: Theme) {
    document.documentElement.classList.toggle('light', t === 'light')
  }

  function toggle() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  watch(theme, (t) => {
    writeTheme(t)
    apply(t)
  }, { immediate: true })

  return { theme, toggle }
})
