/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // API surface + the OIDC kickoff (GET /oauth2/authorization/{reg}) and
      // callback (/login/oauth2/code/{reg}). All proxied to the platform on
      // :8080 with `xfwd: true` so X-Forwarded-{Host,Proto,Port} headers are
      // sent — combined with `server.forward-headers-strategy=FRAMEWORK` in
      // application-dev.yml, that makes Spring's `response.sendRedirect("/...")`
      // resolve to `localhost:5173` rather than the listening `localhost:8080`.
      // Without this, post-login lands on :8080 instead of staying on :5173.
      '/api': { target: 'http://localhost:8080', changeOrigin: false, xfwd: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: false, xfwd: true },
      '/login/oauth2': { target: 'http://localhost:8080', changeOrigin: false, xfwd: true },
    },
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        codeSplitting: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    exclude: ['e2e/**', 'node_modules/**'],
    testTimeout: 30_000,
    hookTimeout: 30_000,
    // `default` keeps the human terminal output; `junit` writes JUnit XML for
    // Codecov Test Analytics (uploaded via `codecov/test-results-action` in CI).
    reporters: ['default', 'junit'],
    outputFile: { junit: 'junit.xml' },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: 'coverage',
      include: ['src/**/*.{ts,vue}'],
      exclude: [
        // Auto-generated OpenAPI types — not production code we measure.
        'src/api/schema.ts',
        // App entrypoint + ambient types: bootstrap with no meaningful branches to cover.
        'src/main.ts',
        'src/env.d.ts',
        '**/*.d.ts',
        '**/__tests__/**',
        '**/*.{spec,test}.ts',
      ],
    },
  },
})
