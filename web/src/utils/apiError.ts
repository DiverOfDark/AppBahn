/**
 * Extract a user-facing message from an `openapi-fetch` error response body.
 * The backend's Spring `@RestControllerAdvice` returns shapes like
 *   { "error": "Bad Request", "message": "Name must match …", "path": "/api/v1/environments" }
 * for validation failures and
 *   { "error": "License limit reached", "message": "…" }
 * for license / quota responses. Both are covered.
 */
export function extractApiErrorMessage(apiError: unknown, fallback: string): string {
  if (!apiError) return fallback
  if (typeof apiError === 'string') return apiError
  const err = apiError as { message?: unknown; error?: unknown }
  if (typeof err.message === 'string' && err.message.trim()) return err.message
  if (typeof err.error === 'string' && err.error.trim()) return err.error
  return fallback
}
