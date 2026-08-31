/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const API = 'http://localhost:8080'
const CAMINHOS_DA_API = ['/auth', '/pacientes', '/consultas', '/anotacoes', '/lembretes', '/auditoria']

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: Object.fromEntries(
      CAMINHOS_DA_API.map((caminho) => [caminho, { target: API, changeOrigin: true }]),
    ),
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/teste/setup.ts'],
    css: true,
    // `e2e/` e do Playwright: o `test()` dele explode dentro do Vitest.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})
