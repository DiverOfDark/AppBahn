import createClient from 'openapi-fetch'
import type { paths } from './schema'

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

export const api = createClient<paths>({
  baseUrl: '/api/v1',
  headers: {},
})

// Attach bearer token to every request
api.use({
  async onRequest({ request }) {
    if (accessToken) {
      request.headers.set('Authorization', `Bearer ${accessToken}`)
    }
    return request
  },
})
