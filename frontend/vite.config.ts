/// <reference types="vitest/config" />
import type { IncomingMessage } from 'node:http'

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * 8080 e a porta padrao do `debug.sh`, mas nem sempre esta livre na maquina.
 * `API_URL=http://localhost:8090 npm run dev` reaponta o proxy sem editar este
 * arquivo — antes era preciso mexer no codigo, e a mudanca acabava commitada
 * por engano.
 */
const API = process.env.API_URL ?? 'http://localhost:8080'
const CAMINHOS_DA_API = ['/auth', '/pacientes', '/consultas', '/anotacoes', '/lembretes', '/auditoria']

/**
 * `/pacientes` e `/auditoria` sao caminho da API E rota de tela. Sem o desvio
 * abaixo, recarregar a pagina em /pacientes (ou abrir o link direto de uma
 * ficha) entrega o JSON da API no lugar do app: o proxy nao sabe distinguir os
 * dois. Navegacao de documento pede `text/html`; chamada do cliente pede json —
 * e so a primeira volta para o index.
 */
const proxyDaApi = Object.fromEntries(
  CAMINHOS_DA_API.map((caminho) => [
    caminho,
    {
      target: API,
      changeOrigin: true,
      bypass: (requisicao: IncomingMessage) => {
        const aceita = requisicao.headers.accept
        return typeof aceita === 'string' && aceita.includes('text/html')
          ? '/index.html'
          : undefined
      },
    },
  ]),
)

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5173, proxy: proxyDaApi },
  preview: { port: 4173, proxy: proxyDaApi },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/teste/setup.ts'],
    css: true,
    // `e2e/` e do Playwright: o `test()` dele explode dentro do Vitest.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})
