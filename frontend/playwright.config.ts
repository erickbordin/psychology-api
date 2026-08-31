import { defineConfig } from '@playwright/test'

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
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure' },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})
