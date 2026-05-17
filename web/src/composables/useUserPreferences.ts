import { ref } from 'vue'
import { api } from '@/api/client'
import type { components } from '@/api/schema'

type UserPreferences = components['schemas']['UserPreferences']

let sessionPromise: Promise<UserPreferences | null> | null = null
const preferences = ref<UserPreferences | null>(null)

export function useUserPreferences() {
  function fetch(): Promise<UserPreferences | null> {
    if (!sessionPromise) {
      sessionPromise = api
        .GET('/users/me/preferences')
        .then(({ data }) => {
          preferences.value = data ?? null
          return preferences.value
        })
        .catch(() => {
          sessionPromise = null
          return null
        })
    }
    return sessionPromise
  }

  async function setDefaultWorkspace(slug: string | null): Promise<void> {
    // null clears the default; the schema type is widened here because the backend accepts
    // JSON null even though the generated schema shows the field as non-nullable string.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const body = { defaultWorkspaceSlug: slug } as any
    const { data } = await api.PATCH('/users/me/preferences', { body })
    if (data) {
      preferences.value = data
      sessionPromise = Promise.resolve(data)
    }
  }

  return { preferences, fetch, setDefaultWorkspace }
}
