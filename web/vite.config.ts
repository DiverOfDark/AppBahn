/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
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
