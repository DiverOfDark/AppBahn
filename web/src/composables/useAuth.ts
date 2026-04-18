import { ref, computed } from 'vue'
import { setAccessToken, getAccessToken } from '@/api/client'

const authenticated = ref(false)

export function useAuth() {
  const isAuthenticated = computed(() => authenticated.value)

  /** Backend handles OIDC discovery/PKCE and redirects back to /auth/complete?token=… */
  function login() {
    window.location.href = '/api/v1/auth/login'
  }

  function handleAuthComplete(): boolean {
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    if (token) {
      setAccessToken(token)
      authenticated.value = true
      window.history.replaceState({}, '', '/console')
      return true
    }
    return false
  }

  function setToken(token: string) {
    setAccessToken(token)
    authenticated.value = true
  }

  function logout() {
    setAccessToken(null)
    authenticated.value = false
  }

  function checkAuth(): boolean {
    return getAccessToken() !== null
  }

  return { isAuthenticated, login, handleAuthComplete, setToken, logout, checkAuth }
}
