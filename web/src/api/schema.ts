// Auto-generated from api/public-api.yaml via openapi-typescript
// Run: npx openapi-typescript ../../api/public-api.yaml -o src/api/schema.ts
//
// Placeholder until generated — provides minimal types for the login page

export interface paths {
  '/admin/config': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['PlatformConfig']
          }
        }
      }
    }
  }
}

export interface components {
  schemas: {
    PlatformConfig: {
      domain?: string
      branding?: {
        instanceName?: string
        tagline?: string
        logoUrl?: string
        loginButtonText?: string
      }
      auth?: Record<string, unknown>
      registry?: Record<string, unknown>
    }
  }
}
