import { defineConfig } from '@playwright/test'

// Dedicated Playwright config for the PR screenshot run. Drives the Vite dev
// server (not preview) because preview proxies unmocked API calls to
// localhost:8080 — with no backend running, the SPA auth-redirects to a blank
// page and produces ~20 KB black-rectangle PNGs.
//
// Used by .github/workflows/ci.yml `screenshot` job. Captures only the
// `pr-screenshots.spec.ts` file; the regular e2e config covers everything else.

export default defineConfig({
  testDir: './e2e',
  testMatch: /pr-screenshots\.spec\.ts$/,
  timeout: 60_000,
  globalTimeout: 5 * 60 * 1_000,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    headless: true,
    screenshot: 'off',
    trace: 'off',
  },
  webServer: {
    command: 'npm run dev -- --port 5173',
    port: 5173,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
})
