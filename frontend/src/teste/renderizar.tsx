import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import type { RouteObject } from 'react-router-dom'
import { MemoryRouter, RouterProvider, createMemoryRouter } from 'react-router-dom'

import { SessaoProvider } from '../features/auth/SessaoProvider'

/**
 * `retry: false` e obrigatorio: com o retry padrao da Query, um teste de erro
 * espera tres tentativas e estoura o timeout.
 */
export function novoQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

/**
 * Para um componente isolado, sem rotas de dados — a maioria dos testes.
 */
export function renderizarComProvedores(elemento: ReactElement, rotaInicial = '/') {
  return render(
    <QueryClientProvider client={novoQueryClient()}>
      <MemoryRouter initialEntries={[rotaInicial]}>{elemento}</MemoryRouter>
    </QueryClientProvider>,
  )
}

/**
 * Para quando o teste precisa de um router de dados de verdade — ex.: o
 * `useBlocker` da Task 8, que so funciona dentro de um data router.
 */
export function renderizarComRotas(rotas: RouteObject[], rotaInicial = '/') {
  const router = createMemoryRouter(rotas, { initialEntries: [rotaInicial] })
  return render(
    <QueryClientProvider client={novoQueryClient()}>
      <SessaoProvider>
        <RouterProvider router={router} />
      </SessaoProvider>
    </QueryClientProvider>,
  )
}
