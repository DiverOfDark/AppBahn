import { ref } from 'vue'
import { api } from '@/api/client'
import type { components } from '@/api/schema'

type CurrentUser = components['schemas']['CurrentUserResponse']

// Module-level promise so the first caller fetches once; subsequent calls
// reuse the same in-flight or resolved promise for the session lifetime.
let sessionPromise: Promise<CurrentUser | null> | null = null
const user = ref<CurrentUser | null>(null)

export function useCurrentUser() {
  function fetch(): Promise<CurrentUser | null> {
    if (!sessionPromise) {
      sessionPromise = api
        .GET('/users/me')
        .then(({ data }) => {
          user.value = data ?? null
          return user.value
        })
        .catch(() => {
          sessionPromise = null
          return null
        })
    }
    return sessionPromise
  }

  return { user, fetch }
}
