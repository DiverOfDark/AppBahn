import { ref, computed } from 'vue'
import { setAccessToken, getAccessToken } from '@/api/client'

const authenticated = ref(false)

export function useAuth() {
  const isAuthenticated = computed(() => authenticated.value)

  /**
   * Redirect to the backend's auth endpoint. The backend handles OIDC
   * discovery, PKCE generation, and redirects the browser to the IdP.
   * After authentication, the backend exchanges the code for tokens and
   * redirects back to /auth/complete?token=...
   */
  function login() {
    window.location.href = '/api/v1/auth/login'
  }

  /**
   * Called on the /auth/complete route — extracts the token from the URL
   * query params (set by the backend callback handler).
   */
  function handleAuthComplete(): boolean {
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    if (token) {
      setAccessToken(token)
      authenticated.value = true
      // Clean the token from the URL
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
