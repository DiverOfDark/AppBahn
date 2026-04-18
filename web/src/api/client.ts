import createClient from 'openapi-fetch'
import type { paths } from './schema'

const TOKEN_KEY = 'appbahn_token'

let accessToken: string | null = sessionStorage.getItem(TOKEN_KEY)

export function setAccessToken(token: string | null) {
  accessToken = token
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(TOKEN_KEY)
  }
}

export function getAccessToken(): string | null {
  return accessToken
}

export const api = createClient<paths>({
  baseUrl: '/api/v1',
  headers: {},
})

api.use({
  async onRequest({ request }) {
    if (accessToken) {
      request.headers.set('Authorization', `Bearer ${accessToken}`)
    }
    return request
  },
  async onResponse({ response }) {
    if (response.status === 401) {
      setAccessToken(null)
      window.location.href = '/'
    }
    return response
  },
})
