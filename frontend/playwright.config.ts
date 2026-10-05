import { defineConfig } from '@playwright/test'

const baseUrlExterna = process.env.E2E_BASE_URL

/**
 * O webServer sobe o Vite, cujo proxy espelha os caminhos da API. A API em si e
 * pre-requisito externo: rode `./debug.sh` em `backend/` antes.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  metadata: {
    convites: (process.env.E2E_CONVITES ?? '').split(','),
    prefixoApi: process.env.E2E_API_PREFIX ?? '',
  },
  use: {
    baseURL: baseUrlExterna ?? 'http://localhost:5173',
    ignoreHTTPSErrors: baseUrlExterna?.startsWith('https://localhost') ?? false,
    trace: 'retain-on-failure',
  },
  webServer: baseUrlExterna
    ? undefined
    : {
        command: 'npm run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: true,
        timeout: 60_000,
      },
})
